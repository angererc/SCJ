package scj.compiler.optimizing;

import javassist.expr.FieldAccess;

public class CompilationStats {
	
	private int ir, ur, iw, uw, ia, ua = 0;

	public void recordInstrumentedRead(FieldAccess f) {
		ir++;
	}

	public void recordUninstrumentedRead(FieldAccess f) {
		ur++;
	}

	public void recordInstrumentedWrite(FieldAccess f) {
		iw++;
	}

	public void recordUninstrumentedWrite(FieldAccess f) {
		uw++;
	}
	
	public void recordUninstrumentedArrayAccess(int pos, int c,
			String methodName) {
		ua++;
	}

	public void recordInstrumentedArrayAccess(int pos, int c, String methodName) {
		ia++;
	}

	public void printStats() {
		System.out.printf("reads: sum=%s, instrumented=%s, uninstrumented=%s\n", ir+ur, ir, ur);
		System.out.printf("writes: sum=%s, instrumented=%s, uninstrumented=%s\n", iw+uw, iw, uw);
		System.out.printf("array: sum=%s, instrumented=%s, uninstrumented=%s\n", ia+ua, ia, ua);
	}
}
