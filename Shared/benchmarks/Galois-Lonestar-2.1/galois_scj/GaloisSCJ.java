package galois_scj;

import galois.runtime.ForeachContext;
import scj.Task;

public abstract class GaloisSCJ<T> {

	public abstract void scjTask_doCall(Task<Void> now, T item, ForeachContext<T> context);
}
