package galois_scj;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import galois.runtime.Callback;
import galois.runtime.Features;
import galois.runtime.ForeachContext;
import galois.runtime.Iteration;
import galois.runtime.IterationAbortException;
import galois.runtime.wl.Worklist;

//collects a worklist and other information that is common accross forked worker tasks
//each worker task will have an ID that it uses to find the correct piece of work
//then a worker can start a GaloisSCJProcess with that id that does the actual work
public class GaloisSCJComputation<T> {

	final Worklist<T> worklist;
	final int numTasks;
	final ReentrantLock lock;
	final Condition moreWork;

	volatile boolean yield = false;
	volatile boolean finish = false;

	final AtomicInteger numDone = new AtomicInteger();
	
	private final Deque<Callback> suspendThunks;
	
	//not sure what this really does
	final int lockCoalescing = 0;

	public GaloisSCJComputation(Worklist<T> worklist, int numTasks) {
		this.worklist = worklist;
		this.numTasks = numTasks;
		lock = new ReentrantLock();
		moreWork = lock.newCondition();
		suspendThunks = new ArrayDeque<Callback>();
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
}
