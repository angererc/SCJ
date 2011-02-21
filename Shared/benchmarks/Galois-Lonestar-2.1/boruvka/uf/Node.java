/*
Galois, a framework to exploit amorphous data-parallelism in irregular
programs.

Copyright (C) 2010, The University of Texas at Austin. All rights reserved.
UNIVERSITY EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES CONCERNING THIS SOFTWARE
AND DOCUMENTATION, INCLUDING ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR ANY
PARTICULAR PURPOSE, NON-INFRINGEMENT AND WARRANTIES OF PERFORMANCE, AND ANY
WARRANTY THAT MIGHT OTHERWISE ARISE FROM COURSE OF DEALING OR USAGE OF TRADE.
NO WARRANTY IS EITHER EXPRESS OR IMPLIED WITH RESPECT TO THE USE OF THE
SOFTWARE OR DOCUMENTATION. Under no circumstances shall University be liable
for incidental, special, indirect, direct or consequential damages or loss of
profits, interruption of business, or related expenses which may arise from use
of Software or Documentation, including but not limited to those resulting from
defects in Software and/or Documentation, or loss or inaccuracy of data of any
kind.

File: Node.java 

 */

package boruvka.uf;

import galois.objects.AbstractReplayable;
import galois.objects.GObject;
import galois.objects.Lockable;
import galois.objects.MethodFlag;
import galois.runtime.Callback;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;
import galois_scj.ReducedGaloisRuntime;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

class Node extends AbstractReplayable implements GObject, Lockable {
  private static EdgeComparator comp = new EdgeComparator();
  private final AtomicReference<Iteration> owner;
  private final PriorityBlockingQueue<Edge> queue;
  private final AtomicReference<Node> parent;
  private int rank;
  private final ReentrantLock semanticInfoGuard;
  private final AtomicInteger findOwners;
  private final AtomicReference<Iteration> specEdgeOwner;

  public Node() {
    queue = new PriorityBlockingQueue<Edge>(10, comp);
    parent = new AtomicReference<Node>();
    parent.set(this);
    rank = 0;
    semanticInfoGuard = new ReentrantLock();
    findOwners = new AtomicInteger();
    specEdgeOwner = new AtomicReference<Iteration>();
    owner = new AtomicReference<Iteration>();
  }

  public void acquireNode() {
    semanticInfoGuard.lock();
  }

  public Node getParent() {
    return parent.get();
  }

  public int getRank() {
    return rank;
  }

  public void releaseNode() {
    semanticInfoGuard.unlock();
  }

  public void setParent(Node p) {
    this.parent.set(p);
  }

  public void setRank(int r) {
    this.rank = r;
  }

  public final AtomicInteger getFindOwners() {
    return findOwners;
  }

  public AtomicReference<Iteration> getSpecEdgeOwner() {
    return specEdgeOwner;
  }

  public void add(Edge edge) {
    assert (GaloisRuntime.getRuntime() instanceof ReducedGaloisRuntime) || FullGaloisRuntime.getFullRuntime().inRoot();
    queue.add(edge);
  }

  public Edge poll() {
    final Edge retval = queue.poll();

    if (retval != null && ((GaloisRuntime.getRuntime() instanceof ReducedGaloisRuntime) || !FullGaloisRuntime.getFullRuntime().inRoot())) {
      GaloisRuntime.getRuntime().onUndo(Iteration.getCurrentIteration(), new Callback() {
        @Override
        public void call() {
          queue.add(retval);
        }
      });
    }

    return retval;
  }

  public void addAll(Node node, byte flags) {
    assert !GaloisRuntime.getRuntime().needMethodFlag(flags, (byte) (MethodFlag.SAVE_UNDO | MethodFlag.CHECK_CONFLICT));
    queue.addAll(node.queue);
  }

  public void clear(byte flags) {
    assert !GaloisRuntime.getRuntime().needMethodFlag(flags, (byte) (MethodFlag.SAVE_UNDO | MethodFlag.CHECK_CONFLICT));
    queue.clear();
  }

  @Override
  public void access(byte flags) {
    Iteration.acquire(this, flags);
  }

  @Override
  public AtomicReference<Iteration> getOwner() {
    return owner;
  }
}
