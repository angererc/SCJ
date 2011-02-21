package galois_scj;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import util.fn.LambdaVoid;

import galois.objects.Mappable;
import galois.runtime.AbstractExecutorContext;
import galois.runtime.Callback;
import galois.runtime.Features;
import galois.runtime.ForeachContext;
import galois.runtime.Iteration;
import galois.runtime.IterationAbortException;
import galois.runtime.wl.Worklist;

//collects a worklist and other information that is common accross forked worker tasks
//each worker task will have an ID that it uses to find the correct piece of work
//then a worker can start a GaloisSCJProcess with that id that does the actual work
public abstract class GaloisSCJComputation<T> {

	final Worklist<T> worklist;
	final int numTasks;
	final ReentrantLock lock;
	final Condition moreWork;

	volatile boolean yield = false;
	volatile boolean finish = false;

	final AtomicInteger numDone;
	
	private final Deque<Callback> suspendThunks;
	
	//not sure what this really does
	final int lockCoalescing = 0;

	protected GaloisSCJComputation(Worklist<T> worklist, int numTasks) throws ExecutionException {
		this.worklist = worklist;
		this.numTasks = numTasks;
		lock = new ReentrantLock();
		moreWork = lock.newCondition();
		suspendThunks = new ArrayDeque<Callback>();
		numDone = new AtomicInteger(0);
	}
	
	protected GaloisSCJComputation(Worklist<T> worklist, Iterable<T> initial, int numTasks) throws ExecutionException {
		this(worklist, numTasks);
		this.initializeWorklist(initial);
	}
	
	protected GaloisSCJComputation(Worklist<T> worklist, Mappable<T> initial, int numTasks) throws ExecutionException {
		this(worklist, numTasks);
		this.initializeWorklist(initial);
	}
	
	private void initializeWorklist(Iterable<T> initial)
	throws ExecutionException {
		final ForeachContext<T> ctx = new SimpleContext<T>(numTasks);

		for (T item : initial) {
			worklist.addInitial(item, ctx);
		}
		worklist.finishAddInitial();
	}
	
	private void initializeWorklist(Mappable<T> mappable)
	throws ExecutionException {
		final ForeachContext<T> ctx = new SimpleContext<T>(numTasks);

		mappable.map(new LambdaVoid<T>() {
			@Override
			public void call(T item) {
				worklist.addInitial(item, ctx);
			}
		});
		worklist.finishAddInitial();
	}
	
	public int getNumTasks() {
		return this.numTasks;
	}

	Iteration newIteration(Iteration prev, int tid) {
		if (prev == null) {
			return new Iteration(tid);
		} else {
			return prev.recycle();
		}
	}

	void commitIteration(Iteration it, int iterationId, T item, boolean releaseLocks) {
		if (item != null) {
			Features.getReplayFeature().onCommit(it, iterationId, item);
		}
		it.performCommit(releaseLocks);
	}

	void abortIteration(Iteration it) throws IterationAbortException {
		it.performAbort();
	}

	T poll(ForeachContext<T> ctx) {
		return worklist.poll(ctx);
	}

	boolean someDone() {
		return numDone.get() > 0;
	}

	void wakeupOne() {
		lock.lock();
		try {
			moreWork.signal();
		} finally {
			lock.unlock();
		}
	}

	void wakeupAll() {
		lock.lock();
		try {
			moreWork.signalAll();
		} finally {
			lock.unlock();
		}
	}

	void makeAllDone() {
		//System.out.println("GaloisSCJComputation.makeAllDone() called");
		// Can't use set() because there is a rare possibility
		// that it would happen between an increment decrement
		// pair in isDone and prevent some threads from leaving
		int n;
		do {
			n = numDone.get();
		} while (!numDone.compareAndSet(n, numTasks));
	}

	synchronized void addSuspendThunk(Callback callback) {
		suspendThunks.add(callback);
	}
	
	/*
	 * 
	 */
	
	private static class SimpleContext<S> extends AbstractExecutorContext<S> {
		private final int maxThreads;
		private final AtomicInteger current = new AtomicInteger();

		public SimpleContext(int maxThreads) {
			this.maxThreads = maxThreads;
		}

		@Override
		public int getThreadId() {
			return current.getAndIncrement() % maxThreads;
		}

		@Override
		public int getIterationId() {
			throw new UnsupportedOperationException("Not supported yet.");
		}
	}
}
