package galois_scj;

import galois.runtime.GaloisRuntime;
import galois.runtime.wl.Priority;
import galois.runtime.wl.Worklist;
import galois.runtime.wl.Priority.Rule;

import java.util.concurrent.ExecutionException;

public class OrderedGaloisSCJComputation<T> extends GaloisSCJComputation<T> {

	public OrderedGaloisSCJComputation(Iterable<T> initial, Rule priority) throws ExecutionException {
		this(initial, GaloisRuntime.getRuntime().getMaxThreads(), priority);
	}
	
	@SuppressWarnings("unchecked")
	public OrderedGaloisSCJComputation(Iterable<T> initial, int numTasks, Rule priority) throws ExecutionException {
		super((Worklist<T>) Priority.makeOrdered(priority), initial, numTasks);
	}
}
