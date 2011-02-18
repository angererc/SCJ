package galois.runtime;

public abstract class GaloisRuntime {

	protected static GaloisRuntime instance; 
	
	public static GaloisRuntime getRuntime() {
		assert instance != null : "call initialize() on the concrete runtime";
		return instance;
	}
	

	public abstract boolean useSerial();
	
	public abstract boolean needMethodFlag(byte flags, byte option);
	
	public abstract void onUndo(Iteration it, Callback callback);
	
	public abstract void onCommit(Iteration currentIteration, Callback callback);
	public abstract int getMaxThreads();
	public abstract boolean ignoreUserFlags();
	public abstract void invalidate();


	public abstract void raiseConflict(Iteration iteration, Iteration iteration2);
}
