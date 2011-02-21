package galois_scj;

import galois.objects.MethodFlag;
import galois.runtime.Callback;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;
import galois.runtime.IterationAbortException;
import galois.runtime.WorkNotProgressiveException;
import galois.runtime.WorkNotUsefulException;

import java.util.concurrent.ExecutionException;

import scj.Task;

public abstract class GaloisSCJProcess<T> implements ForeachContext<T> {

	private final GaloisSCJComputation<T> computation;

	private Iteration currentIteration;
	private int iterationId = -1;
	private boolean first;
	private int lastAbort;
	private int consecAborts;
	private final int id;
	
	protected int numCommitted;
	protected int numAborted;

	private long waitStart;
	private long accumWait;

	protected GaloisSCJProcess(GaloisSCJComputation<T> computation, int id) {
		this.computation = computation;
		this.id = id;

	}

	private final void startWaiting() {
		waitStart = System.nanoTime();
	}

	private final void stopWaiting() {
		accumWait += System.nanoTime() - waitStart;
	}

	private final boolean isDone() throws InterruptedException {
		computation.lock.lock();
		try {
			// Encountered an error by another thread?
			if (computation.numDone.incrementAndGet() > computation.numTasks) {
				return true;
			}

			// Last man: safe to check global termination property
			if (computation.numDone.get() == computation.numTasks) {
				if (computation.worklist.isEmpty()) {
					computation.wakeupAll();
					return true;
				} else {
					computation.numDone.decrementAndGet();
					return false;
				}
			}

			// Otherwise, wait for some work
			while (computation.numDone.get() < computation.numTasks && computation.worklist.isEmpty()) {
				startWaiting();
				try {
					computation.moreWork.await();
				} finally {
					stopWaiting();
				}
			}

			if (computation.numDone.get() == computation.numTasks) {
				// Done, truly
				computation.wakeupAll();
				return true;
			} else if (computation.numDone.get() > computation.numTasks) {
				// Error by another thread, finish up
				return true;
			} else {
				// More work to do!
				computation.numDone.decrementAndGet();
				return false;
			}
		} finally {
			computation.lock.unlock();
		}
	}

	@Override	
	public int getThreadId() {
		return id;
	}

	private final void setupCurrentIteration() {
		Iteration it = computation.newIteration(currentIteration, getThreadId());
		if (it != currentIteration || first) {
			// Try to reduce the number of Iteration.setCurrentIteration calls if we
			// can
			currentIteration = it;
			Iteration.setCurrentIteration(it);
			iterationId = currentIteration.getId();
			first = false;
		}
	}

	private T nextItem() {
		T item;
		try {
			item = computation.poll(this);
		} catch (IterationAbortException e) {
			throw new Error("Worklist method threw unexpected exception");
		}

		if (item == null) {
			computation.commitIteration(currentIteration, iterationId, item, true);
		}
		return item;
	}

	private final void doCommit(T item) {
		try {
			computation.commitIteration(currentIteration, iterationId, item, (numCommitted & computation.lockCoalescing) == 0);
			// XXX(ddn): This count will be incorrect for ordered executors because
			// commitIteration only puts an iteration into ready to commit
			numCommitted++;			
		} catch (IterationAbortException _) {
			// an iteration has thrown WorkNotUsefulException/WorkNotProgressiveException,
			// and tries to commit before it goes to RTC (i.e. completes), another thread
			// signals it to abort itself
			readd(item);
			doAbort();
		}
	}

	private final void doAbort() {
		computation.abortIteration(currentIteration);
		numAborted++;
		// TODO(ddn): Implement this better using control algo! Needed something fast
		// to make boruvka work.
		final int logFactor = 4;
		final int mask = (1 << logFactor) - 1;
		if (lastAbort == numCommitted) {
			// Haven't committed anything since last abort
			consecAborts++;
			if (consecAborts > 1 && (consecAborts & mask) == 0) {
				startWaiting();
				try {
					Thread.sleep(consecAborts >> logFactor);
				} catch (InterruptedException e) {
					throw new Error(e);
				} finally {
					stopWaiting();
				}
			}
		} else {
			consecAborts = 0;
		}
		lastAbort = numCommitted;
	}

	/**
	 * Re-add item to worklist in case of abort.
	 *
	 * @param item
	 */
	private void readd(T item) {
		while (true) {
			try {
				computation.worklist.add(item, this);
				break;
			} catch (IterationAbortException e) {
				// Commonly this exception is never thrown, but
				// client code may provide comparators/indexers
				// that may abort, in which case spin until we
				// can put the item back
			}
		}
	}

	protected abstract void body(T item, ForeachContext<T> context);

	public void scjTask_process(Task<Void> now) throws Exception {
		first = true;
		try {
			L1: while (true) {
				T item;

				while (true) {
					setupCurrentIteration();
					item = nextItem();
					if (item == null) {
						if (computation.yield) {
							break L1;
						} else {
							break;
						}
					}

					try {
						body(item, this);
						doCommit(item);
					} catch (IterationAbortException _) {
						readd(item);
						doAbort();
					} catch (WorkNotProgressiveException _) {
						doCommit(item);
					} catch (WorkNotUsefulException _) {
						doCommit(item);
					} catch (Throwable e) {
						// Gracefully terminate processes
						if (currentIteration != null) {
							numAborted++;
							computation.abortIteration(currentIteration);
						}
						throw new ExecutionException(e);
					}

					if (computation.yield) {
						break L1;
					}
				}

				// Slow check
				if (isDone()) {
					break;
				}
			}
		} finally {
			computation.makeAllDone();
			computation.wakeupAll();
			currentIteration = null;
			Iteration.setCurrentIteration(null);
		}
	}

	@Override
	public final void add(final T t) {
		add(t, MethodFlag.ALL);
	}

	@Override
	public void add(final T t, byte flags) {
		final ForeachContext<T> ctx = this;
		if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.SAVE_UNDO)) {
			currentIteration.addCommitAction(new Callback() {
				@Override
				public void call() {
					computation.worklist.add(t, ctx);
					if (computation.someDone()) {
						computation.wakeupOne();
					}
				}
			});
		} else {
			computation.worklist.add(t, ctx);
			if (computation.someDone()) {
				computation.wakeupOne();
			}
		}
	}

	@Override
	public void finish() {
		currentIteration.addCommitAction(new Callback() {
			@Override
			public void call() {
				computation.finish = true;
				computation.yield = true;
			}
		});
	}

	@Override
	public void suspendWith(final Callback call) {
		currentIteration.addCommitAction(new Callback() {
			@Override
			public void call() {
				computation.addSuspendThunk(call);
				computation.yield = true;
			}
		});
	}

	@Override
	public int getIterationId() {
		return iterationId;
	}


}
