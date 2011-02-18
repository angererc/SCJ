package galois_scj;

import galois.objects.MethodFlag;
import galois.runtime.Callback;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;
import galois.runtime.IterationAbortException;

public class ReducedGaloisRuntime extends GaloisRuntime {

	public static ReducedGaloisRuntime getReducedRuntime() {
		if(instance == null) {
			// Use default serial Runtime
			initialize(true, false);
		}
		return (ReducedGaloisRuntime)instance;
	}

	public static void initialize(boolean useSerial, boolean ignoreUserFlags) {
		if (instance != null) {
			instance.invalidate();
		}

		instance = new ReducedGaloisRuntime(useSerial, ignoreUserFlags, MethodFlag.NONE);
	}

	private final boolean useSerial;
	private final boolean ignoreUserFlags;
	private final byte mask;
	
	private ReducedGaloisRuntime(boolean useSerial, boolean ignoreUserFlags, byte mask) {
		this.useSerial = useSerial;
		this.ignoreUserFlags = ignoreUserFlags;
		this.mask = mask;
	}

	@Override
	public boolean useSerial() {
		return useSerial;
	}
	
	@Override
	public boolean needMethodFlag(byte flags, byte option) {
		return ((flags & mask) & option) != 0;
	}

	@Override
	public void onUndo(Iteration it, Callback callback) {
		it.addUndoAction(callback);
	}

	@Override
	public void onCommit(Iteration currentIteration, Callback callback) {
		currentIteration.addCommitAction(callback);
	}

	@Override
	public int getMaxThreads() {
		return java.lang.Runtime.getRuntime().availableProcessors();
	}

	@Override
	public boolean ignoreUserFlags() {
		return ignoreUserFlags;
	}

	@Override
	public void invalidate() {
	}

	@Override
	public void raiseConflict(Iteration iteration, Iteration iteration2) {
		IterationAbortException.throwException();
	}

}
