import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;

public class Box {

	static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
	static final int SENDERFEEDBACK = 700;
	static final int RECEIVERFEEDBACK = 1400;

	final String interactionName;
	FileWriter logFile;

	final boolean swapRoles;
	final boolean communicative;
	final boolean bidirectional;
	final Iterator<Integer> stimuli;
	final int[] states;
	final int[] signals;
	final int[] acts;
	final int rounds;

	final Random r;
	int stimulus;
	final List<Rat> clients = new LinkedList<Rat>();
	int ready = 0;
	int nextActor = 0;
	boolean firstTurn = false;
	int round = 0;
	int shuffle;
	long waitingSince = System.currentTimeMillis();
	boolean aborted = false;

	public Box(boolean swapRoles, boolean communicative, boolean bidirectional, int rounds, int[] states, int[] signals, int[] acts, Iterator<Integer> stimuli) throws IOException {
		this.swapRoles = swapRoles;
		this.communicative = communicative;
		this.bidirectional = bidirectional;
		this.rounds = rounds;
		this.states = states;
		this.signals = signals;
		this.acts = acts;
		this.stimuli = stimuli;
		int[] symbols = new int[states.length + signals.length + acts.length];
		System.arraycopy(states, 0, symbols, 0, states.length);
		System.arraycopy(signals, 0, symbols, states.length, signals.length);
		System.arraycopy(acts, 0, symbols, states.length+signals.length, acts.length);
		int sym = 0;
		for (int i : symbols) {
			sym = sym*10 + symbols[i];
		}
		this.r = new Random(this.waitingSince);
//		System.out.println(Integer.toBinaryString(new BitStuffingLFSR(4,3).intCycle()));
		this.interactionName = "comm" + communicative + ".swap" + swapRoles + ".sig" + sym + "." + this.waitingSince;
		this.logFile = new FileWriter("log." + this.interactionName, true); // don't buffer
	}

	public Box newInstance() throws IOException {
		// TODO passing the (stateful) stimuli object is a bad idea...
		return new Box(this.swapRoles, this.communicative, this.bidirectional, this.rounds, this.states, this.signals, this.acts, this.stimuli);
	}

	// returns true if this interaction now has all the participants it needs
	public synchronized boolean addParticipant(Rat c) {
		this.log(c, "\tconnected");
		this.clients.add(c);
		String instructions = "";
		if (this.communicative) {
			instructions += "In this experiment you will be interacting with someone else";
			if (this.swapRoles) {
				instructions += " you will sometimes be the sender and sometimes the recipient.";
			} else {
				if (this.clients.size() % 2 == 1) {
					instructions += " You will observe a symbol and try to communicate this to your partner by selecting one of two messages.";
				} else {
					instructions += " You will receive messages and have to try to react to them appropriately.";
				}
			}
			// if this.states != this.acts -> choose an act depending on the state
			instructions += "At the end of each interaction you will get feedback about whether the receiver managed to correctly decode the meaning intended by the sender.";
		} else {
			// the bacteria (or "you couldn't fake this to a baboon") condition
			instructions += "In this experiment you will have to learn to respond correctly to a stimulus by selecting one of two responses.";
		}
		instructions += " You will receive a bonus proportional to how many trials you get right.";
		c.send("<p>" + instructions + "</p><input type=\"submit\" value=\"Begin experiment\" onclick=\"socket.send('start')\"/>"); // and hide button?
//		this.ipInfo.put(c, "Received instructions for condition " + this.interactionName);
		this.log(c, "sending\tinstructions");
		this.waitingSince = System.currentTimeMillis();
		return this.isFull();
	}
	
	public boolean isFull() {
		return this.clients.size() == 2;
	}

	public boolean isRunning() {
		return !this.aborted && this.ready == 2 && this.round != this.rounds;
	}

	private void nextRound() {
		this.waitingSince = System.currentTimeMillis();
		if (this.round == this.rounds || this.aborted) {
			this.nextActor = -1;
			for (Rat c : this.clients) {
				c.send("<p>Thank you for participating in this experiment. If you have any thoughts e.g. on whether it got harder or easier over time, or whether you recognised certain patterns, please enter them here:</p><textarea rows=\"6\" cols=\"40\" id=\"exit\"></textarea><br/><input type=\"submit\" value=\"Finish experiment\" onclick=\"socket.send('exit ' + document.getElementById('exit').value)\"/>");
//				this.ipInfo.put(c, "finished condition " + this.interactionName + " and got sent exit questionnaire");
			}
		} else {
			this.shuffle = this.r.nextInt(2);
			this.stimulus = this.stimuli.next();
			this.log("round\t" + this.round + "\tstate " + this.stimulus + " shuffle " + this.shuffle);
			String stimuli = "opts" + this.states[this.stimulus] + this.signals[this.shuffle] + this.signals[(this.shuffle+1)%2];
			// TODO timertask
			this.clients.get(this.nextActor).send(stimuli);
			this.round++;
		}
	}

	private void sendWait(Rat c) {
		c.send(this.communicative ? "Waiting for partner.." : "Retrieving data..");
	}

	// TODO add reinforcementrat and remove from burrhus pool?
	public void poll() {
		if (this.nextActor != -1) {
			long nowt =  System.currentTimeMillis() - this.waitingSince;
			if (nowt > 1000) {
				String poll = "poll\t" + nowt + "\t";
				if (this.isFull()) {
					if (this.isRunning()) {
						this.log(poll + "waiting for response from " + (this.firstTurn ? "receiver" : "sender") + " " + this.nextActor);
					} else {
						this.log(poll + "waiting for remaining client(s) to start experiment");
					}
				} else {
					// this.ready > 0
					this.log(poll + "waiting for other client(s) to connect..");
				}
			}
		}
	}

	public synchronized void response(Rat conn, String message) {
		this.waitingSince = System.currentTimeMillis();
		int client = this.clients.indexOf(conn);
		if (message.equals("start")) {
			if (++this.ready == 2) {
				this.log(conn, "ready start\t" + new Date());
				if (client != this.nextActor) {
					this.sendWait(conn);
				}
				this.nextRound();
			} else {
				this.log(conn, "ready\t" + new Date());
				// should we prematurely reveal the first state if only the sender is connected/ready?
				this.sendWait(conn);
//				this.ipInfo.put(conn, "Started experiment, waiting for other participants");
			}
		} else if (message.startsWith("exit ")) {
			if (this.nextActor != -1) {
				this.log(conn, "WARNING premature exit, attempting to cheat?");
			} else {
				String code = Burrhus.generateCompletionCode();
				this.log(conn, message.replace("\n", "\\n\\n"));
//				this.ipInfo.put(conn, "Received completion code " + code + " for interaction " + this.interactionName + " completing " + this.round + " rounds, aborted " + this.aborted);
				this.log(conn, "completion\t" + code);
				this.disconnect(conn, "<p>Cheerio for taking part, here's your completion code:</p><p>" + code + "</p><p>And here's a debrief.</p>");
			}
		} else {
			if (client != this.nextActor) {
				this.log(conn, "WARNING\treceived message from wrong participant: " + message);
			} else {
				int response = (Integer.parseInt(message.substring(0,1)) + this.shuffle) % 2;
				this.log(conn, response + "\t" + (this.firstTurn ? "signal" : "act") + " after " + message.substring(1));
				this.firstTurn = !this.firstTurn;
				if (this.firstTurn) {
					// pass on to other actor
					this.nextActor = (this.nextActor+1)%2;
					this.shuffle = this.r.nextInt(2);
					this.clients.get(this.nextActor).send("opts" + this.signals[response] + this.acts[this.shuffle] + this.acts[(this.shuffle+1)%2]);
				} else {
					if (!this.swapRoles) {
						// TODO randomise role-swapping?
						this.nextActor = (this.nextActor+1)%2;
					}
					boolean success = response == this.stimulus;
					final String msg = success ? "success" : "failure";
					this.log("outcome\t" + (success ? 1 : 0) + "\t");
					this.clients.get(0).send(msg);
					Burrhus.TIMER.schedule(new TimerTask() { public void run() {
						clients.get(1).send(msg);
					}}, 1500); // TODO fix delay

					// let the feedback sink in..
					Burrhus.TIMER.schedule(new TimerTask() { public void run() {
						nextRound();
					}}, Box.SENDERFEEDBACK);
				}
			}
		}
	}

	public synchronized void disconnect(Rat c, String reason) {
		c.disconnect(reason);
		this.clients.remove(c);
		this.wrapUp();
	}

	public synchronized void remoteDisconnect(Rat conn, int code) {
		String summary = code + "\tremote disconnect in round " + this.round + " after " + (System.currentTimeMillis()-this.waitingSince) + "ms inactivity waiting for " + (this.clients.indexOf(conn)==this.nextActor ? "self" : "other");
		this.clients.remove(conn);
		this.log(conn, summary);
	//	this.ipInfo.put(conn, summary);
		this.abortInteraction();
		this.wrapUp();
	}

	public synchronized void abortInteraction() {
		if (this.nextActor != -1) {
			this.log("abort\t\t");
			this.aborted = true;
			// fastforward to exit questionnaire
			this.nextRound();
		}
	}

	private void wrapUp() {
		if (this.clients.isEmpty()) {
			this.log("complete\t" + new Date() + "\tAll clients disconnected");
			try { this.logFile.close(); } catch (IOException e) { e.printStackTrace(); }
			new File("log." + this.interactionName).renameTo(new File("completed." + this.interactionName));
			this.logFile = null;
		}
	}

	private void log(String s) {
		String msg = DATEFORMAT.format(new Date()) + "\t" + s;
		try {
			this.logFile.write(msg + "\n");
		} catch (IOException e) {
			System.out.println("Problem writing to log file " + this.logFile + ": " + e.getMessage());
		}
		System.out.println(this.interactionName + ": " + s);
	}

	private void log(Rat r, String s) {
		this.log(r.getId() + "\t" + s);
	}

}