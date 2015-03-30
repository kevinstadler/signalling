/**
 * A maximal linear feedback shift register that stuffs an extra zero into the
 * longest stretch of 1's to achieve a balanced cycle with a period of 2^nbits.
 */
public class BitStuffingLFSR extends LFSR {

	private boolean stuff = false;

	public BitStuffingLFSR(int bits, int initState) {
		super(bits, initState);
	}

	public Integer next() {
		if (this.stuff) {
			this.stuff = false;
			return 0;
		} else {
			int a = super.next();
			if (Integer.bitCount(this.register) == this.bits) {
				this.stuff = true;
			}
			return a;
		}
	}

	public static int[] getAllSwitchingInitStates(int nbits) {
		int[] r = new int[(int) Math.pow(2,nbits-1)];
		for (int i = 0; i < Math.pow(2,nbits-3); i++) {
			r[4*i] = (i << 3) + 2;
			r[4*i+1] = (i << 3) + 3;
			r[4*i+2] = (i << 3) + 4;
			r[4*i+3] = (i << 3) + 5;
		}
		return r;
	}
}