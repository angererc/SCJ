package galois_scj;

import galois.runtime.GaloisRuntime;
import galois.runtime.wl.Priority;
import galois.runtime.wl.Worklist;
import galois.runtime.wl.Priority.Rule;

import java.util.concurrent.ExecutionException;

public class UnorderedGaloisSCJComputation<T> extends GaloisSCJComputation<T> {

	public UnorderedGaloisSCJComputation(Iterable<T> initial, Rule priority) throws ExecutionException {
		this(initial, GaloisRuntime.getRuntime().getMaxThreads(), priority);
	}
	
	@SuppressWarnings("unchecked")
	public UnorderedGaloisSCJComputation(Iterable<T> initial, int numTasks, Rule priority) throws ExecutionException {
		super((Worklist<T>) Priority.makeUnordered(priority), initial, numTasks);
	}

	
}
