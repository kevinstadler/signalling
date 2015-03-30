public abstract class Rat {
	private Box box;

	Rat(Box box) {
		this.box = box;
	}

	public Box getBox() {
		return this.box;
	}

	public void disconnect(String reason) {
		this.box = null;
	}

	public abstract void send(Object... objects);
	public abstract String getId();
	public abstract String getInfo();
}
