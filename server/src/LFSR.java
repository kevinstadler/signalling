import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A linear feedback shift register
 */
public class LFSR  implements Iterator<Integer> {

	final int bits;
	final int[] taps;
	int period;
	protected int register;

	// In one 2^n-1 period of a maximal n-bit LFSR, 2^(n−1) runs occur (for
	// example, a six bit LFSR will have 32 runs). Exactly half of these
	// runs will be one bit long, a quarter will be two bits long, up to a
	// single n-1 bit long run of zeroes and a single n-bit run of ones.
	public LFSR(int bits, int[] taps, int initState) {
		this.bits = bits;
		this.taps = taps;
		// TODO check initState isn't zero and only the last n bits are filled
		this.register = initState & (int)Math.pow(2, bits)-1;
		this.next();
		for (this.period = 1; this.register != initState; this.period++) {
			this.next();
		}
		if (this.period == 1) {
			throw new IllegalArgumentException("Tap/initial state specification leads to a locked LFSR");
		}
	}

	public LFSR(int bits, int initState) {
		// select taps that create a maximal LFSR - works for n in [2,7]
		this(bits, new int[]{bits-1}, initState);
	}

	// Iterator stuff
	public boolean hasNext() {
		return true;
	}
	public void remove() {
		throw new UnsupportedOperationException();
	}

	public Integer next() {
		int input = this.register & 1;
		for (int tap : this.taps) {
			input = input ^ (this.register >> this.bits-tap);
		}
		this.register = (this.register >> 1) | ((input & 1) << (this.bits-1));
		return this.register % 2;
	}

	public List<Integer> cycle() {
		List<Integer> c = new ArrayList<Integer>(this.period);
		for (int i = 0; i < this.period; i++) {
			c.add(this.next());
		}
		return c;
	}

	// only works for periods up to 32, i.e. 5 bit registers
	// Integer.toBinaryString(i)
	public int intCycle() {
		int c = 0;
		for (int i = 0; i < this.period; i++) {
			c = (c << 1) + this.next();
		}
		return c;
	}

	public String toString() {
		return this.bits + " bit linear feedback shift register with period " + this.period + ", cycle " + Integer.toBinaryString(this.intCycle());
	}
}
