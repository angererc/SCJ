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

File: UnionFind.java 

*/



package boruvka.uf;

import galois.runtime.Callback;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import util.SystemProperties;

public class UnionFind {
  private enum Operation {
    UNION, FIND
  };

  private final boolean useMaps = false;
  private ConcurrentMap<Iteration, Map<Node, Node>> mapRepCache;
  private final boolean useRepCache = false;
  private final UFConflictManager cm;

  public UnionFind() {
    if (useRepCache)
      mapRepCache = new ConcurrentHashMap<Iteration, Map<Node, Node>>();

    if (useMaps) {
      cm = new MapConflictManager();
    } else if (SystemProperties.getBooleanProperty("boruvka.uf.useExclusive", false)) {
      System.out.println("Using exclusive");
      cm = new ExclusiveLocker();
    } else {
      cm = new TLConflictManager(GaloisRuntime.getRuntime().getMaxThreads());
    }
  }

  private interface UFConflictManager {
    public void checkUnion(Iteration it, ForeachContext<Node> ctx, Node loser, Node repX, Node repY);

    public Node checkFind(Iteration it, ForeachContext<Node> ctx, Node x, Operation context);

    public int release(Iteration it, ForeachContext<Node> ctx);

    public boolean requiresRelease();
  }

  /**
   * Uses specEdgeOwner field in Node to implement exclusive ownership
   * on nodes of union-find.
   *
   */
  private static class ExclusiveLocker implements UFConflictManager {
    private ThreadLocal<List<Node>> seen = new ThreadLocal<List<Node>>() {
      @Override
      protected List<Node> initialValue() {
        return new ArrayList<Node>();
      }
    };

    private void acquire(Iteration it, Node n) {
      AtomicReference<Iteration> lock = n.getSpecEdgeOwner();

      if (lock.get() == it) {
        return;
      } else if (lock.compareAndSet(null, it)) {
        seen.get().add(n);
        return;
      } else {
        GaloisRuntime.getRuntime().raiseConflict(it, lock.get());
      }
    }

    @Override
    public Node checkFind(Iteration it, ForeachContext<Node> ctx, Node x, Operation context) {
      acquire(it, x);
      Node parent = x.getParent();
      Node newParent = parent;
      if (x == parent) { // Reached representative
        parent = x.getParent();
      } else { //parent != x
        newParent = checkFind(it, ctx, parent, context);
        x.setParent(newParent);
      }
      // TODO(ddn): Uncomment below?
      //    if (useRepCache)	
      //      updateRepCache(x, newParent, it);
      return newParent;
    }

    @Override
    public void checkUnion(Iteration it, ForeachContext<Node> ctx, Node loser, Node repX, Node repY) {
    }

    @Override
    public int release(Iteration it, ForeachContext<Node> ctx) {
      int retval = 0;

      for (Node x : seen.get()) {
        x.getSpecEdgeOwner().set(null);
        retval++;
      }
      seen.get().clear();
      return retval;
    }

    @Override
    public boolean requiresRelease() {
      return false;
    }
  }

  private static abstract class Gatekeeper implements UFConflictManager {
    protected abstract boolean addOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x);

    protected abstract boolean hasOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x);

    protected abstract List<Node> getOwnedSpecEdges(Iteration it);

    @Override
    public final void checkUnion(Iteration it, ForeachContext<Node> ctx, Node loser, Node repX, Node repY) {
      // Compare against finds
      int numFindOwners = loser.getFindOwners().get();

      if (!((numFindOwners == 1 && hasOwnedFindReps(it, ctx, loser)) || numFindOwners == 0)) {
        repX.releaseNode();
        repY.releaseNode();
        GaloisRuntime.getRuntime().raiseConflict(it, null);
        assert false;
      }

      loser.getSpecEdgeOwner().set(it);

      // Add info to local logs so that we can release the locks later
      getOwnedSpecEdges(it).add(loser);
    }

    @Override
    public final Node checkFind(Iteration it, ForeachContext<Node> ctx, Node x, Operation context) {
      Iteration owner = x.getSpecEdgeOwner().get();
      if (owner != null && owner != it) {
        GaloisRuntime.getRuntime().raiseConflict(it, owner);
      }
      Node parent = x.getParent();
      Node newParent = parent;
      if (x == parent) { // Reached representative
        x.acquireNode();
        parent = x.getParent();
        if (x != parent) {
          x.releaseNode();
          GaloisRuntime.getRuntime().raiseConflict(it, x.getSpecEdgeOwner().get());
        }
        if (context == Operation.FIND) {
          // Add info to local logs so that we can release the locks later
          if (addOwnedFindReps(it, ctx, x))
            x.getFindOwners().incrementAndGet();

          x.releaseNode();
        }
      } else { //parent != x
        newParent = checkFind(it, ctx, parent, context);
        x.setParent(newParent);
      }
      // TODO(ddn): Uncomment below?
      //    if (useRepCache)	
      //      updateRepCache(x, newParent, it);
      return newParent;
    }

    @Override
    public boolean requiresRelease() {
      return true;
    }
  }

  private static class MapConflictManager extends Gatekeeper {
    private final ConcurrentMap<Iteration, Set<Node>> mapOwnedFindReps;
    private final ConcurrentMap<Iteration, List<Node>> mapOwnedSpecEdgeSources;

    public MapConflictManager() {
      mapOwnedFindReps = new ConcurrentHashMap<Iteration, Set<Node>>();
      mapOwnedSpecEdgeSources = new ConcurrentHashMap<Iteration, List<Node>>();
    }

    private Set<Node> getOwnedFindReps(Iteration it) {
      Set<Node> retval = mapOwnedFindReps.get(it);
      if (retval == null) {
        retval = new HashSet<Node>();
        mapOwnedFindReps.put(it, retval);
      }
      return retval;
    }

    @Override
    protected List<Node> getOwnedSpecEdges(Iteration it) {
      List<Node> retval = mapOwnedSpecEdgeSources.get(it);
      if (retval == null) {
        retval = new ArrayList<Node>();
        mapOwnedSpecEdgeSources.put(it, retval);
      }
      return retval;
    }

    @Override
    public int release(Iteration it, ForeachContext<Node> ctx) {
      int nLocksReleased = 0;
      if (it == null)
        return nLocksReleased;

      Set<Node> findReps = mapOwnedFindReps.remove(it);
      if (findReps != null) {
        for (Node obj : findReps) {
          AtomicInteger findSet = obj.getFindOwners();
          findSet.decrementAndGet();
          nLocksReleased++;
        }
      }
      // XXX: Help gc?
      // findReps.clear();

      List<Node> specEdges = mapOwnedSpecEdgeSources.remove(it);
      if (specEdges != null) {
        for (Node obj : specEdges) {
          obj.getSpecEdgeOwner().set(null);
          nLocksReleased++;
        }
      }

      return nLocksReleased;
    }

    @Override
    protected boolean addOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x) {
      return getOwnedFindReps(it).add(x);
    }

    @Override
    protected boolean hasOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x) {
      return getOwnedFindReps(it).contains(x);
    }
  }

  private static class TLConflictManager extends Gatekeeper {
    private final Set<Node>[] ownedFindReps;
    private final ThreadLocal<List<Node>> ownedSpecEdgeSources;

    @SuppressWarnings("unchecked")
    public TLConflictManager(int numThreads) {
      ownedFindReps = new Set[numThreads];
      for (int i = 0; i < numThreads; i++) {
        ownedFindReps[i] = new HashSet<Node>();
      }

      ownedSpecEdgeSources = new ThreadLocal<List<Node>>() {
        @Override
        protected List<Node> initialValue() {
          return new ArrayList<Node>();
        }
      };
    }

    private final Set<Node> getOwnedFindReps(Iteration it, ForeachContext<Node> ctx) {
      return ownedFindReps[ctx.getThreadId()];
    }

    @Override
    protected final List<Node> getOwnedSpecEdges(Iteration it) {
      return ownedSpecEdgeSources.get();
    }

    @Override
    public int release(Iteration it, ForeachContext<Node> ctx) {
      int nLocksReleased = 0;
      if (it == null)
        return nLocksReleased;

      Set<Node> findReps = ownedFindReps[ctx.getThreadId()];
      for (Node obj : findReps) {
        AtomicInteger findSet = obj.getFindOwners();
        findSet.decrementAndGet();
        nLocksReleased++;
      }
      //      for (Node obj : findReps) {
      //        AtomicInteger findSet = obj.getFindOwners();
      //        findSet.decrementAndGet();
      //        nLocksReleased++;
      //      }
      findReps.clear();

      List<Node> specEdges = ownedSpecEdgeSources.get();
      int sz = specEdges.size();
      for (int i = 0; i < sz; ++i) {
        Node obj = specEdges.get(i);
        obj.getSpecEdgeOwner().set(null);
        nLocksReleased++;
      }
      specEdges.clear();

      return nLocksReleased;
    }

    @Override
    protected boolean addOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x) {
      return getOwnedFindReps(it, ctx).add(x);
    }

    @Override
    protected boolean hasOwnedFindReps(Iteration it, ForeachContext<Node> ctx, Node x) {
      return getOwnedFindReps(it, ctx).contains(x);
    }
  }

  public Node find(Iteration it, ForeachContext<Node> ctx, Node x) {
    if (it == null) {
      return findSerial(x);
    }

    return findConcurrentRec(it, ctx, x, Operation.FIND);
  }

  public int release(Iteration it, ForeachContext<Node> ctx) {
    if (useRepCache) {
      mapRepCache.remove(it);
    }
    return cm.release(it, ctx);
  }

  public void union(Iteration it, ForeachContext<Node> ctx, Node x, Node y) {
    if (it == null) {
      unionSerial(x, y);
    } else {
      unionConcurrent(it, ctx, x, y);
    }
  }

  private void createUnionUndos(final Node loser, final Node repY, final int origRankY, final boolean haveEqualRanks) {
    // Add info to local logs so that we can release the locks later
    GaloisRuntime.getRuntime().onUndo(Iteration.getCurrentIteration(), new Callback() {
      @Override
      public void call() {
        if (haveEqualRanks) {
          repY.setRank(origRankY);
        }
        loser.setParent(loser);
      }
    });
  }

  private Node findConcurrentRec(Iteration it, ForeachContext<Node> ctx, Node x, Operation context) {
    return cm.checkFind(it, ctx, x, context);
  }

  private Node findSerial(Node x) {
    Node parent = x.getParent();
    Node newParent = parent;
    if (parent != x) {
      newParent = findSerial(parent);
      x.setParent(newParent);
    }
    return newParent;
  }

  private Node getRep(Node obj, Iteration currIter) {
    Map<Node, Node> cachedMap = getRepCache(currIter);
    return cachedMap.get(obj);
  }

  private Map<Node, Node> getRepCache(Iteration it) {
    Map<Node, Node> retval;
    retval = mapRepCache.get(it);
    if (retval == null) {
      retval = new HashMap<Node, Node>();
      mapRepCache.put(it, retval);
    }
    return retval;
  }

  private void unionConcurrent(Iteration it, ForeachContext<Node> ctx, Node x, Node y) {
    // Acquire locks
    Node repX;
    Node repY;

    if (useRepCache) {
      repX = getRep(x, it);
      repY = getRep(y, it);

      if (repX != null && repY != null) {
        if (repX.hashCode() < repY.hashCode()) {
          repX.acquireNode();
          repY.acquireNode();
        } else {
          repY.acquireNode();
          repX.acquireNode();
        }
      } else if (repY == null && repX == null) {
        // XXX We would like to impose a canonical ordering on the
        // representatives but we don't have it. Use this for now
        synchronized (this) {
          System.err.println("WHY???");
          repX = findConcurrentRec(it, ctx, x, Operation.UNION);
          repY = findConcurrentRec(it, ctx, y, Operation.UNION);
        }
      } else if (repX != null && repY == null) {
        repY = findConcurrentRec(it, ctx, y, Operation.UNION);
      } else {
        repX = findConcurrentRec(it, ctx, x, Operation.UNION);
      }
    } else {
      //synchronized (this) {
      repX = findConcurrentRec(it, ctx, x, Operation.UNION);
      repY = findConcurrentRec(it, ctx, y, Operation.UNION);
      //}
    }

    // At this point we have concrete locks on both reprOfX, reprOfY
    Node winner = repX;
    Node loser = repY;
    int rankX = repX.getRank();
    int rankY = repY.getRank();

    if (rankX <= rankY) {
      winner = repY;
      loser = repX;
    }

    cm.checkUnion(it, ctx, loser, repX, repY);

    loser.setParent(winner);

    int origRankY = -1;
    if (rankX == rankY) {
      origRankY = rankY;
      repY.setRank(rankY + 1);
    }

    // XXX Experiment...originally releasing them after storing undo actions
    if (cm.requiresRelease()) {
      repX.releaseNode();
      repY.releaseNode();
    }

    createUnionUndos(loser, repY, origRankY, rankX == rankY);

    if (useRepCache)
      updateRepCache(loser, winner, it);
  }

  private void unionSerial(Node x, Node y) {
    Node xRepr = findSerial(x);
    Node yRepr = findSerial(y);
    int xReprRank = xRepr.getRank();
    int yReprRank = yRepr.getRank();
    if (xReprRank > yReprRank) {
      yRepr.setParent(xRepr);
    } else {
      xRepr.setParent(yRepr);
      if (xReprRank == yReprRank)
        yRepr.setRank(yReprRank + 1);
    }
  }

  private void updateRepCache(Node obj, Node rep, Iteration it) {
    Map<Node, Node> cachedMap = getRepCache(it);
    cachedMap.put(obj, rep);
  }
}
