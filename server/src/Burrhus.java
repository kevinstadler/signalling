import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * Burrhus - operant conditioning for the 21st century
 */
public class Burrhus extends WebSocketServer {

	public final static Timer TIMER = new Timer(true);

	private final static String IPFILE = "ips.txt";
	public final static int CLOSECODE = 3000;
	public static long POLLINTERVAL = 2500;

	Map<Box,Integer> boxTypes;
	Box currentBox;

	BlockingQueue<WebSocket> pendingUsers = new LinkedBlockingQueue<WebSocket>();
	long oldestPendingUser;

	Properties ipBlacklist = new Properties();
	Map<WebSocket, SocketRat> connectedClients = new HashMap<WebSocket, SocketRat>();

	public Burrhus (int port) throws IOException {
		super(new InetSocketAddress(port));
		File f = new File(Burrhus.IPFILE);
		if (f.isFile()) {
			this.ipBlacklist.load(new FileInputStream(f));
		} else {
			f.createNewFile();
		}
		this.boxTypes = new HashMap<Box,Integer>();
		for (int startState : BitStuffingLFSR.getAllSwitchingInitStates(4)) {
			this.boxTypes.put(new Box(false, false, true, 64, new int[]{0, 1}, new int[]{2, 3}, new int[]{4, 5}, new BitStuffingLFSR(4, startState)), 1);
		}
	}

	public Box getNextBox() throws IOException {
		if (this.currentBox == null) {
			if (this.boxTypes.isEmpty()) {
				System.out.println("All conditions finished!!!");
			} else {
				System.out.println("Creating new box");
				int max = 0;
				Box nextType = null;
				for (Map.Entry<Box,Integer> type : this.boxTypes.entrySet()) {
					if (type.getValue() > max) {
						nextType = type.getKey();
						max = type.getValue();
					}
				}
				this.currentBox = nextType.newInstance();
			}
		}
		return this.currentBox;
	}

	public static void main(String[] args) throws InterruptedException, IOException {
//		WebSocketImpl.DEBUG = true;
		final Burrhus b = new Burrhus(8887);
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() { public void run() {
			System.out.println("Shutting downn");
			try {
				b.ipBlacklist.store(new FileOutputStream(Burrhus.IPFILE), "");
			} catch (IOException e) {

			}
			System.out.println("Halting");
		}}));
		b.start();
		Burrhus.TIMER.schedule(new TimerTask() { public void run() {
			for (Rat r : new HashSet<Rat>(b.connectedClients.values())) {
				r.getBox().poll();
			}
		}}, Burrhus.POLLINTERVAL, Burrhus.POLLINTERVAL);
	}

	public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected");
		if (this.ipBlacklist.containsKey(conn.getRemoteSocketAddress().getAddress().getHostAddress())) {
			System.out.println("Rejecting " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " from the ip blacklist");
			conn.close(Burrhus.CLOSECODE, "You (or someone else using your computer or computer network) have already participated in this experiment and are therefore not eligible to take part in it again.");
		} else {
			try {
				Box myBox = this.getNextBox();
				if (myBox == null) {
					System.out.println("OUT OF CONDITIONS!!!");
					conn.close(Burrhus.CLOSECODE, "Sorry, all tasks have already been taken by other workers");
				} else {
	//				this.ipBlacklist.setProperty(conn.getRemoteSocketAddress().getAddress().getHostAddress(), "waiting for communicative partners");
					SocketRat r = new SocketRat(myBox, conn);
					this.connectedClients.put(conn, r);
					if (myBox.addParticipant(r)) {
						this.currentBox = null;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void onMessage(WebSocket conn, String message) {
		if (this.connectedClients.containsKey(conn)) {
			this.connectedClients.get(conn).receive(message);
		} else {
			System.out.println("Message received by unknown client: " + conn.getRemoteSocketAddress().getAddress());
		}
	}

	public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {
		Rat r = this.connectedClients.get(conn);
		if (r == null) {
			return; // probably someone from the ip blacklist
		}
		// notify Box if the socket was closed remotely
		if (remote) {
			r.getBox().remoteDisconnect(r, code);
		}
		this.connectedClients.remove(r);
		if (r.getInfo() == null) {
			// poor guy didn't get very far, remove again
			this.ipBlacklist.remove(r.getId());
		} else {
			this.ipBlacklist.setProperty(r.getId(), r.getInfo());
		}
	}

	public void onError(WebSocket conn, Exception e) {
		e.printStackTrace();
	}

	public static String generateCompletionCode() {
		return "asdf";
	}

}
