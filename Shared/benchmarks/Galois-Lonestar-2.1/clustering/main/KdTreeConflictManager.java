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

File: KdTreeConflictManager.java 

*/


package clustering.main;

import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;
import galois.runtime.IterationAbortException;
import galois.runtime.ReleaseCallback;

/**
 * This conflict log checks conflicts for kdtree with the operations add,
 * remove, and findBestMatch.
 * <p/>
 * This implementation is optimized for the case where there are only a few
 * iterations with conflicts at a time. Adding a conflict is fairly lightweight
 * (low memory, little or no locking) and removing all the conflicts for an
 * iteration is cheap (no locking). However checking a new conflict against
 * previous ones requires a linear scan through existing conflicts. This is
 * pretty hard to avoid for findBestMatch searches anyway though.
 * <p/>
 * The general principle behind this conflict manager is that it consists of a
 * list containing a record (KDLocalConflictManager) for each iteration that is
 * currently accessing the data structure. When an iteration attempts to invoke
 * a method on the KD-Tree, this list is scanned for any potential conflicts.
 * <p/>
 * The list of local logs is implemented in a highly concurrent manner, using
 * mostly volatile variables rather than locks. When an iteration commits or
 * aborts, rather than removing the local log from the list, it is simply set to
 * inactive. Inactive logs are lazily clipped from the list when other
 * iterations scan the list.
 * <p/>
 * The conflict log also keeps a list of logs from recently completed
 * iterations. This list is used for checking "return-dependent" commutativity
 * checks. In general, commutativity violations should be detected before a
 * method is invoked. However, if commutativity cannot be determined until the
 * return value of the method is known, a race condition can arise where an
 * iteration can commit and remove its abstract locks before a commutativity
 * violation can be detected. To avoid this, the "finished log" is used to check
 * for violations against any iterations that completed between the invocation
 * of a method and its commutativity check.
 *
 * @author bjw, milind
 */
public final class KdTreeConflictManager implements ReleaseCallback {

  /*
   * The proceeding variables are volatile because they are manipulated by
   * multiple threads at once. Volatile ensures that the values are never cached
   * (nor are accesses re-ordered), so we can be sure each thread sees the most
   * recent value.
   */
  /**
   * head of linked-list of iteration-specific lists of conflicts.
   */
  private volatile LocalEntryLog conflictLists;
  /**
   * tail of linked-list of finished (committed/aborted) conflict lists allows a
   * transaction to grab this pointer, to get a list of completed lists during
   * an interval because we only keep tail, the list is available for garbage
   * collection otherwise
   */
  private volatile LocalEntryLog finishedLists;

  KdTreeConflictManager() {
    // a dummy log so we have something to point to
    final LocalEntryLog dummy = new LocalEntryLog(null);
    dummy.invalid = true;
    conflictLists = dummy;
    finishedLists = dummy;
  }

  /**
   * Insert a new add/remove conflict and check to see if it conflicts with any
   * prior conflicts. Will throw exception if scheduler decides this iteration
   * needs to abort.
   */
  public void addRemoveProlog(final NodeWrapper nodeWrapper) throws IterationAbortException {
    Iteration it = Iteration.getCurrentIteration();
    // first find this iterations local conflict list and add the new conflict
    // its very important to add the new conflict before checking against
    // existing conflicts to prevent race conditions
    LocalEntryLog conflictLog = findLocalConflictLog(it);
    if (!conflictLog.addWrite(nodeWrapper)) {
      return;
    }
    // if conflict already existed, no need for further checking
    // now iterate over all other active conflict lists to find any relevant conflicts
    // we also apply path compression to snip invalid lists out of the active linked-list
    LocalEntryLog curr = conflictLists;
    LocalEntryLog prev = null;
    LocalEntryLog lastValid = null;
    while (curr != null) {
      if (!curr.invalid) {
        if (prev != lastValid && lastValid != null) {
          lastValid.nextActive = curr;
        }
        lastValid = curr;
        if (curr.iteration != it) {
          curr.checkWrite(nodeWrapper, it);
        }
      }
      prev = curr;
      curr = curr.nextActive;
    }
    if (lastValid != prev && lastValid != null) {
      lastValid.nextActive = null;
    }
  }

  /**
   * Record the last-seen LocalConflictLog for the current Iteration
   */
  LocalEntryLog readBestMatchProlog() {
    return finishedLists;
  }

  /**
   * Insert a new add/remove conflict and check to see if it conflicts with any
   * prior conflicts. Will throw exception if scheduler decides this iteration
   * needs to abort.
   */
  public void bestMatchEpilog(final PotentialCluster match, LocalEntryLog oldFinishedTail) throws IterationAbortException {
    Iteration it = Iteration.getCurrentIteration();
    // first find this iterations local conflict list and add the new conflict
    // its very important to add the new conflict before checking against
    // existing conflicts to prevent race conditions
    LocalEntryLog conflictLog = findLocalConflictLog(it);
    if (!conflictLog.addRead(match)) {
      return;
    }
    // if conflict already existed, no need for further checking now iterate over all other
    // active conflict lists to find any relevant conflicts we also apply path compression
    // to snip invalid lists out of the linked list
    LocalEntryLog curr = conflictLists;
    LocalEntryLog prev = null;
    LocalEntryLog lastValid = null;
    while (curr != null) {
      if (!curr.invalid) {
        if (prev != lastValid && lastValid != null) {
          lastValid.nextActive = curr;
        }
        lastValid = curr;
        if (curr.iteration != it) {
          curr.checkRead(match, it);
        }
      }
      prev = curr;
      curr = curr.nextActive;
    }
    if (lastValid != prev && lastValid != null) {
      lastValid.nextActive = null;
    }
    // now check against any conflict logs that finished during the operation
    LocalEntryLog newFinishedTail = finishedLists;
    while (newFinishedTail != oldFinishedTail) {
      oldFinishedTail = oldFinishedTail.nextFinished;
      oldFinishedTail.checkRead(match, it);
    }
  }

  /**
   * Insert a new read conflict and check to see if it conflicts with any prior
   * conflicts. Will throw exception if scheduler decides this iteration needs
   * to abort. Note: currently we treat these like write-conflicts, which
   * results in overly conservative conflict checking, but is safe and requires
   * less extra cases
   */
  public void readEpilog(final NodeWrapper obj, LocalEntryLog oldFinishedTail) throws IterationAbortException {
    Iteration it = Iteration.getCurrentIteration();
    // first find this iterations local conflict list and add the new conflict
    // its very important to add the new conflict before checking against
    // existing conflicts to prevent race conditions
    LocalEntryLog conflictLog = findLocalConflictLog(it);
    if (!conflictLog.addWrite(obj)) {
      return;
    }
    // if conflict already existed, no need for further checking now iterate over all other active
    // conflict lists to find any relevant conflicts; we also apply path compression to snip invalid lists
    // out of the active linked-list
    LocalEntryLog curr = conflictLists;
    LocalEntryLog prev = null;
    LocalEntryLog lastValid = null;
    while (curr != null) {
      if (!curr.invalid) {
        if (prev != lastValid && lastValid != null) {
          lastValid.nextActive = curr;
        }
        lastValid = curr;
        if (curr.iteration != it) {
          curr.checkWrite(obj, it);
        }
      }
      prev = curr;
      curr = curr.nextActive;
    }
    if (lastValid != prev && lastValid != null) {
      lastValid.nextActive = null;
    }
    // now check against any conflict logs that finished during the operation
    LocalEntryLog newFinishedTail = finishedLists;
    while (newFinishedTail != oldFinishedTail) {
      oldFinishedTail = oldFinishedTail.nextFinished;
      oldFinishedTail.checkWrite(obj, it);
    }
  }

  @Override
  public int release(Iteration it) {
    // find local conflict log for this iteration
    LocalEntryLog curr = conflictLists;
    while (curr.iteration != it) {
      curr = curr.nextActive;
    }
    // add it to the linked-list of finished conflict logs
    synchronized (this) {
      finishedLists.nextFinished = curr;
      finishedLists = curr;
    }
    // inactivate it, will be clipped from active list lazily
    curr.invalid = true;
    return curr.numConflicts;
  }

  private LocalEntryLog findLocalConflictLog(final Iteration it) {
    // first find this iterations local conflict list
    LocalEntryLog curr = conflictLists;
    while (curr != null) {
      // if it is there, but is invalid, it's because we are recycling a previous iteration
      // which has not been cleaned up yet. Instead of trying to reuse it (dangerous, someone
      // might be trying to concurrently delete it from the linked list), we simply skip it
      if (curr.iteration == it && !curr.invalid) {
        return curr;
      }
      curr = curr.nextActive;
    }
    // if it doesn't exist, then create it
    // create an iteration-local conflict log and add it to list of conflict logs
    LocalEntryLog retval = new LocalEntryLog(it);
    synchronized (this) {
      retval.nextActive = conflictLists;
      conflictLists = retval;
    }
    // if we just created a local conflict log, that means this is the first time we've touched
    // this CM -- register it with the iteration.
    GaloisRuntime.getRuntime().onRelease(it, this);
    return retval;
  }


  /**
   * Conflict record for a best match conflict
   */
  private static class BestMatchEntry {
    final NodeWrapper original;
    final double distance;
    volatile BestMatchEntry next;

    BestMatchEntry(PotentialCluster match) {
      original = match.original;
      distance = match.clusterSize;
    }
  }

  /**
   * This class stores the conflicts recorded by a single iteration/transaction.
   * It tries to make operations lightweight, but assumes there will be a small
   * number of conflicts per iteration and so uses linear search.
   */
  public static class LocalEntryLog {
    /**
     * Iteration owning this local conflict log
     */
    final Iteration iteration;
    // array with add/remove conflicts
    volatile NodeWrapper[] addRemoveConflicts;
    // linked list of best match conflicts
    volatile BestMatchEntry matchHead;
    // this flag is set to true when the iteration commits/aborts and these
    // conflicts become invalid
    volatile boolean invalid; // note: false is the default value for
    // a boolean
    // next conflict log in linked list, controlled by RWObjectConflictLog
    volatile LocalEntryLog nextActive;
    // next in linked list of finished conflict logs (once log is
    // finished/inactivated)
    volatile LocalEntryLog nextFinished;
    // total number of conflicts
    int numConflicts;

    LocalEntryLog(Iteration it) {
      iteration = it;
      addRemoveConflicts = new NodeWrapper[5];
    }

    /**
     * Adds a new add/remove conflict to list of conflicts. Returns false if
     * conflict already existed.
     */
    boolean addWrite(NodeWrapper obj) {
      // because only the owning iteration can call this, we don't need to
      // worry about concurrent modifications, only concurrent readers
      // look for an empty slot in the array to add this conflict
      NodeWrapper arr[] = addRemoveConflicts;
      final int length = arr.length;
      for (int i = 0; i < length; i++) {
        if (arr[i] == null) {
          // write conflict into existing empty slot
          arr[i] = obj;
          numConflicts++;
          return true;
        } else if (arr[i] == obj) {
          // conflict already exists
          return false;
        }
      }
      // otherwise there wasn't room in existing array, so create a bigger one
      NodeWrapper newArr[] = new NodeWrapper[2 * length + 3];
      System.arraycopy(arr, 0, newArr, 0, length);
      newArr[length] = obj;
      synchronized (this) {
        // could just use volatile but synchronized is safer for older JVMs
        addRemoveConflicts = newArr;
      }
      numConflicts++;
      return true;
    }

    /**
     * Check to see if add/remove of obj would conflict with any previously
     * recorded conflicts
     */
    void checkWrite(NodeWrapper obj, Iteration currIteration) throws IterationAbortException {
      // first check for equality in add/remove conflicts
      for (NodeWrapper addRemoveConflict : addRemoveConflicts) {
        if (addRemoveConflict == null) {
          break;
        }
        if (addRemoveConflict == obj) {
          GaloisRuntime.getRuntime().raiseConflict(currIteration, iteration);
        }
      }
      // next check to see if currIteration conflicts with any best match searches
      BestMatchEntry curr = matchHead;
      while (curr != null) {
        double dist = NodeWrapper.potentialClusterSize(curr.original, obj);
        if (dist <= curr.distance) {
          GaloisRuntime.getRuntime().raiseConflict(currIteration, iteration);
        }
        curr = curr.next;
      }
      // no conflict found
    }

    /**
     * Adds a new best match conflict to list of conflicts. Returns false if
     * conflict already existed.
     */
    @SuppressWarnings("unchecked")
    boolean addRead(PotentialCluster match) {
      // note because only the owning iteration can call this, we don't need to
      // worry about concurrent modifications, only concurrent readers
      BestMatchEntry newConflict = new BestMatchEntry(match);
      // could check to see if conflict already exists, but don't do so yet
      if (matchHead == null) {
        matchHead = newConflict;
      } else {
        BestMatchEntry curr = matchHead;
        while (curr.next != null) {
          if (curr.original == match.original && curr.distance == match.clusterSize) {
            return false;
          }
          curr = curr.next;
        }
        curr.next = newConflict;
      }
      numConflicts++;
      return true;
    }

    /**
     * Update provisional best match conflict against existing conflicts
     */
    void checkRead(PotentialCluster match, Iteration currIteration) throws IterationAbortException {
      // check to see if any add/removes would have been as close or closer
      for (NodeWrapper anArr : addRemoveConflicts) {
        if (anArr == null) {
          break;
        }
        double dist = NodeWrapper.potentialClusterSize(match.original, anArr);
        if (dist <= match.clusterSize) {
          GaloisRuntime.getRuntime().raiseConflict(currIteration, iteration);
          throw new RuntimeException("must abort when deferred check fails");
        }
      }
      // best match search are read-only so they can't conflict with each other
    }
  }
}
