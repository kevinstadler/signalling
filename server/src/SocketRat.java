import org.java_websocket.WebSocket;

public class SocketRat extends Rat {

	private WebSocket client;
	private String info;

	SocketRat(Box box, WebSocket client) {
		super(box);
		this.client = client;
	}

	public void receive(String message) {
		this.getBox().response(this, message);
	}

	public void send(Object... objects) {
		StringBuilder msg = new StringBuilder();
		for (Object o : objects) {
			msg.append(o);
		}
		this.client.send(msg.toString());
	}

	public void disconnect(String reason) {
		super.disconnect(reason);
		this.client.close(Burrhus.CLOSECODE, reason);
		this.client = null;
	}

	public String getId() {
		return this.client.getRemoteSocketAddress().getAddress().getHostAddress();
	}

	public String getInfo() {
		return this.info;
	}
}
