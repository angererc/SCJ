/*
 * Copyright 2001 Program of Computer Graphics, Cornell University
 *     580 Rhodes Hall
 *     Cornell University
 *     Ithaca NY 14853
 * Web: http://www.graphics.cornell.edu/
 * 
 * Not for commercial use. Do not redistribute without permission.
 */

package clustering.main;

import galois.objects.MethodFlag;
import galois.runtime.Callback;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a KDTree for storing points in 3D and allowing fast
 * spatial queries such as nearest neighbor.  Updated to allow concurrent
 * access by multiple threads.  In particular strives to allow read-only
 * accesses (eg, findNearest) without any locking and use fine-grained
 * locks for write access with aggressive elimination of unnecessary updates.
 * <p/>
 * The general contract is that the tree must always be in a consistent state
 * such that commutative concurrent accesses will always produce correct output.
 * Non-commutative accesses (such as a remove and a findNearest that would return
 * the element being removed) have no guarantees about exact outcome but should
 * still leave the tree in a consistent state and not throw errors.
 * <p/>
 * To reduce the need to update fields, this version removes the totalPoints
 * field. (A total counter can be kept in an enclosing object if needed.)  We
 * also don't compact the point arrays in the leaf nodes, empty slots are null
 * but you must scan the whole array (they are small anyway).
 * <p/>
 * Note the concurrent methods should be safe under the Java 1.5 model and most
 * likely under Java 1.4 too though that is harder to check as Java 1.4 is less
 * precise about the multi-thread behavior and ordering.
 */
public class KdCell {

  private Logger logger = Logger.getLogger("apps.clustering");
  private boolean isFineLoggable = Logger.getLogger("apps.clustering").isLoggable(Level.FINE);

  // only set for the root
  KdTreeConflictManager cm;

  private static final int MAX_POINTS_IN_CELL = 4;
  static final int SPLIT_X = 0;
  static final int SPLIT_Y = 1;
  static final int SPLIT_Z = 2;
  static final int LEAF = 3;

  //bounding box of points contained in this cell (and all descendents)
  float xMin;
  float yMin;
  float zMin;
  float xMax;
  float yMax;
  float zMax;
  //X,Y,Z, or LEAF
  final int splitType;
  //pointers points if this is a leaf node
  final NodeWrapper[] pointList;
  //split value if its not a leaf cell
  final float splitValue;
  //else if its not a leaf node, we need children
  volatile KdCell leftChild;
  volatile KdCell rightChild;
  //set to true when a node is removed from the main kdtree
  private boolean removedFromTree;
  private static final int RETRY_LIMIT = 100;

  /**
   * Create a new empty KDTree
   */
  KdCell() {
    xMin = yMin = zMin = Float.MAX_VALUE;
    xMax = yMax = zMax = -Float.MAX_VALUE;
    splitType = LEAF;
    splitValue = Float.MAX_VALUE;
    pointList = new NodeWrapper[MAX_POINTS_IN_CELL];
    leftChild = null;
    rightChild = null;
  }

  //special constructor used internally

  KdCell(int inSplitType, float inSplitValue) {
    //we don't set the bounding box as we assume it will be set next
    splitType = inSplitType;
    splitValue = inSplitValue;
    pointList = (inSplitType == LEAF) ? new NodeWrapper[MAX_POINTS_IN_CELL] : null;
  }

  /**
   * We provide this factory method so that KDCell can be subclassed.  Returns a new
   * uninitialized cell (also tried to reuse any preallocated array for holding children)
   * Used during cell subdivision.
   */
  KdCell createNewBlankCell(int inSplitType, float inSplitValue) {
    return new KdCell(inSplitType, inSplitValue);
  }

  //These methods are provided in case KDCell is subclassed.  Will be called after KDCell
  //has already been updated for the relevant operation
  //Because we want to prune unnecessary updates, these methods are passed a boolean
  //stating if the cell's statistics (eg, bounding box, etc) are known to have actually
  //changed and should return a boolean indicating if they found any changes.

  boolean notifyPointAdded(NodeWrapper inPoint, boolean inChanged) {
    return inChanged;
  }

  boolean notifyContentsRebuilt(boolean inChanged) {
    return inChanged;
  }

  /**
   * Check to see if adding this point changes the bounding box and enlarge
   * bounding box if necessary.  Returns if the bounding box changed.
   */
  private boolean addToBoundingBoxIfChanges(NodeWrapper cluster) {
    float x = cluster.getX();
    float y = cluster.getY();
    float z = cluster.getZ();
    boolean retval = false;
    if (x < xMin) {
      xMin = x;
      retval = true;
    }
    if (x > xMax) {
      xMax = x;
      retval = true;
    }
    if (y < yMin) {
      yMin = y;
      retval = true;
    }
    if (y > yMax) {
      yMax = y;
      retval = true;
    }
    if (z < zMin) {
      zMin = z;
      retval = true;
    }
    if (z > zMax) {
      zMax = z;
      retval = true;
    }
    return retval;
  }

  private boolean recomputeLeafBoundingBoxIfChanges() {
    float xMinNew = Float.MAX_VALUE, yMinNew = Float.MAX_VALUE, zMinNew = Float.MAX_VALUE;
    float xMaxNew = -Float.MAX_VALUE, yMaxNew = -Float.MAX_VALUE, zMaxNew = -Float.MAX_VALUE;
    for (NodeWrapper pt : pointList) {
      if (pt == null) {
        continue;
      }
      float x = pt.getX();
      float y = pt.getY();
      float z = pt.getZ();
      xMinNew = Math.min(x, xMinNew);
      yMinNew = Math.min(y, yMinNew);
      zMinNew = Math.min(z, zMinNew);
      xMaxNew = Math.max(x, xMaxNew);
      yMaxNew = Math.max(y, yMaxNew);
      zMaxNew = Math.max(z, zMaxNew);
    }
    return updateBoundingBox(xMinNew, yMinNew, zMinNew, xMaxNew, yMaxNew, zMaxNew);
  }

  private boolean recomputeParentBoundingBoxIfChanges() {
    KdCell left = leftChild;
    KdCell right = rightChild;
    float xMinNew = Math.min(left.xMin, right.xMin);
    float xMaxNew = Math.max(left.xMax, right.xMax);
    float yMinNew = Math.min(left.yMin, right.yMin);
    float yMaxNew = Math.max(left.yMax, right.yMax);
    float zMinNew = Math.min(left.zMin, right.zMin);
    float zMaxNew = Math.max(left.zMax, right.zMax);
    return updateBoundingBox(xMinNew, yMinNew, zMinNew, xMaxNew, yMaxNew, zMaxNew);
  }

  private boolean updateBoundingBox(float xMinNew, float yMinNew, float zMinNew, float xMaxNew, float yMaxNew,
      float zMaxNew) {
    boolean retval = false;
    if (xMinNew != xMin) {
      xMin = xMinNew;
      retval = true;
    }
    if (xMaxNew != xMax) {
      xMax = xMaxNew;
      retval = true;
    }
    if (yMinNew != yMin) {
      yMin = yMinNew;
      retval = true;
    }
    if (yMaxNew != yMax) {
      yMax = yMaxNew;
      retval = true;
    }
    if (zMinNew != zMin) {
      zMin = zMinNew;
      retval = true;
    }
    if (zMaxNew != zMax) {
      zMax = zMaxNew;
      retval = true;
    }
    return retval;
  }

  /**
   * Computes this cells bounding box to just contain the specified points
   */
  private void computeBoundingBoxFromPoints(NodeWrapper list[], int size) {
    float xMinNew = Float.MAX_VALUE, yMinNew = Float.MAX_VALUE, zMinNew = Float.MAX_VALUE;
    float xMaxNew = -Float.MAX_VALUE, yMaxNew = -Float.MAX_VALUE, zMaxNew = -Float.MAX_VALUE;
    for (int i = 0; i < size; i++) {
      float x = list[i].getX();
      float y = list[i].getY();
      float z = list[i].getZ();
      xMinNew = Math.min(x, xMinNew);
      yMinNew = Math.min(y, yMinNew);
      zMinNew = Math.min(z, zMinNew);
      xMaxNew = Math.max(x, xMaxNew);
      yMaxNew = Math.max(y, yMaxNew);
      zMaxNew = Math.max(z, zMaxNew);
    }
    xMin = xMinNew;
    xMax = xMaxNew;
    yMin = yMinNew;
    yMax = yMaxNew;
    zMin = zMinNew;
    zMax = zMaxNew;
  }

  /**
   * Return the appropriate splitting component (x,y, or z) which is relevant for this node
   */
  private static float findSplitComponent(NodeWrapper cluster, int splitType) {
    switch (splitType) {
    case SPLIT_X:
      return cluster.getX();
    case SPLIT_Y:
      return cluster.getY();
    case SPLIT_Z:
      return cluster.getZ();
    default:
      throw new RuntimeException("badness");
    }
  }

  /**
   * Given a list of points, and a split plane defined by the splitType and splitValue,
   * partition the list into points below (<=) and above (>) the plane.  Returns the number of points
   * which fell below the plane.
   */
  private static int splitList(NodeWrapper list[], int startIndex, int size, float splitValue, int splitType) {
    int lo = startIndex;
    int hi = startIndex + size - 1;
    //split into a low group that contains all points <= the split value and
    //a high group with all the points > the split value
    //note: after splitting, (lo - startIndex) will be the size of the low group
    while (lo <= hi) {
      while (lo <= hi && splitValue >= findSplitComponent(list[lo], splitType)) {
        lo++;
      }
      while (lo <= hi && splitValue < findSplitComponent(list[hi], splitType)) {
        hi--;
      }
      if (lo < hi) {
        int index1 = lo++;
        int index2 = hi--;
        NodeWrapper temp = list[index1];
        list[index1] = list[index2];
        list[index2] = temp;
      }
    }
    return lo - startIndex;
  }

  /**
   * Sets the contents of this cell to the specified list and size.  Then sudivides the cell as
   * necessary to build an appropriate hierarchy.  Val is a temporary array of floats that
   * we can pass in to reduce the allocation of additional temporary space.
   */
  static KdCell subdivide(final NodeWrapper list[], int offset, int size, float floatArr[], KdCell factory) {
    if (size <= MAX_POINTS_IN_CELL) {
      KdCell cell = factory.createNewBlankCell(LEAF, Float.MAX_VALUE);
      System.arraycopy(list, offset, cell.pointList, 0, size);
      cell.computeBoundingBoxFromPoints(cell.pointList, size);
      cell.notifyContentsRebuilt(true);
      return cell;
    }
    //otherwise its an interior node and we need to choose a split plane
    if (floatArr == null) {
      floatArr = new float[size];
    }
    //compute bounding box of points
    float xMin = Float.MAX_VALUE, yMin = Float.MAX_VALUE, zMin = Float.MAX_VALUE;
    float xMax = -Float.MAX_VALUE, yMax = -Float.MAX_VALUE, zMax = -Float.MAX_VALUE;
    for (int i = offset; i < size + offset; i++) {
      float x = list[i].getX();
      float y = list[i].getY();
      float z = list[i].getZ();
      xMin = Math.min(x, xMin);
      yMin = Math.min(y, yMin);
      zMin = Math.min(z, zMin);
      xMax = Math.max(x, xMax);
      yMax = Math.max(y, yMax);
      zMax = Math.max(z, zMax);
    }
    //choose split plane
    float sx = xMax - xMin;
    float sy = yMax - yMin;
    float sz = zMax - zMin;
    int type;
    float value;
    int type0, type1, type2;
    if (sz > sx && sz > sy) {
      type0 = SPLIT_Z;
      boolean cond = sx > sy;
      type1 = cond ? SPLIT_X : SPLIT_Y;
      type2 = cond ? SPLIT_Y : SPLIT_X;
    } else if (sy > sx) {
      type0 = SPLIT_Y;
      boolean cond = sx > sz;
      type1 = cond ? SPLIT_X : SPLIT_Z;
      type2 = cond ? SPLIT_Z : SPLIT_X;
    } else {
      type0 = SPLIT_X;
      boolean cond = sy > sz;
      type1 = cond ? SPLIT_Y : SPLIT_Z;
      type2 = cond ? SPLIT_Z : SPLIT_Y;
    }
    type = type0;
    value = computeSplitValue(list, offset, size, type0, floatArr);
    if (value == Float.MAX_VALUE) {
      //attempt to split failed so try another axis
      type = type1;
      value = computeSplitValue(list, offset, size, type1, floatArr);
      if (value == Float.MAX_VALUE) {
        type = type2;
        value = computeSplitValue(list, offset, size, type2, floatArr);
      }
    }
    if (value == Float.MAX_VALUE) {
      throw new RuntimeException("badness splittype:" + type + " value:" + value + " size:" + size + " sx:" + sx
          + " sy:" + sy + " sz:" + sz);
    }
    int leftCount = splitList(list, offset, size, value, type);
    if (leftCount <= 1 || leftCount >= size - 1) {
      throw new RuntimeException("badness splittype:" + type + " value:" + value + " leftCount:" + leftCount
          + " rightCount: " + (size - leftCount) + " sx:" + sx + " sy:" + sy + " sz:" + sz);
    }
    KdCell cell = factory.createNewBlankCell(type, value);
    cell.xMin = xMin;
    cell.xMax = xMax;
    cell.yMin = yMin;
    cell.yMax = yMax;
    cell.zMin = zMin;
    cell.zMax = zMax;
    cell.leftChild = subdivide(list, offset, leftCount, floatArr, factory);
    cell.rightChild = subdivide(list, offset + leftCount, size - leftCount, floatArr, factory);
    cell.notifyContentsRebuilt(true);
    return cell;
  }

  private static float computeSplitValue(NodeWrapper list[], int offset, int size, int splitType, float floatArr[]) {
    for (int i = 0; i < size; i++) {
      floatArr[i] = findSplitComponent(list[offset + i], splitType);
    }
    return findMedianGapSplit(floatArr, size);
  }

  /**
   * Given an array of floats, sorts the list, finds the largest gap in values
   * near the median, and returns a value in the middle of that gap
   */
  private static float findMedianGapSplit(float val[], int size) {
    //this is not very efficient at the moment, there are faster median finding algorithms
    Arrays.sort(val, 0, size);
    int start = ((size - 1) >> 1) - ((size + 7) >> 3);
    int end = (size >> 1) + ((size + 7) >> 3);
    if (start == end) {
      //should never happen
      throw new RuntimeException();
    }
    float largestGap = 0;
    float splitValue = 0;
    float nextValue = val[start];
    for (int i = start; i < end; i++) {
      float curValue = nextValue; //ie val[i]
      nextValue = val[i + 1];
      if ((nextValue - curValue) > largestGap) {
        largestGap = nextValue - curValue;
        splitValue = 0.5f * (curValue + nextValue);
        if (splitValue == nextValue) {
          splitValue = curValue;
        } //if not between then choose smaller value
      }
    }
    if (largestGap <= 0) {
      //indicate that the attempt to find a good split value failed
      return Float.MAX_VALUE;
    }
    return splitValue;
  }

  public final boolean add(final NodeWrapper inAdd) {
    return add(inAdd, MethodFlag.ALL);
  }

  /**
   * Add a ClusterKDWrapper to the kdtree, subdividing if necessary
   */
  public boolean add(final NodeWrapper inPoint, byte flags) {
    if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.CHECK_CONFLICT)) {
      cm.addRemoveProlog(inPoint);
    }
    if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.SAVE_UNDO)) {
      GaloisRuntime.getRuntime().onUndo(Iteration.getCurrentIteration(), new Callback() {
        @Override
        public void call() {
          remove(inPoint, MethodFlag.NONE);
        }
      });
    }
    for (int i = 0; i < RETRY_LIMIT; i++) {
      int ret = addPoint(inPoint, null);
      if (ret == -1) {
        if (isFineLoggable) {
          logger.info("retrying addPoint");
        }
      } else if (ret == 0 || ret == 1) {
        return true;
      } else {
        throw new RuntimeException();
      }
    }
    throw new RuntimeException("repeated retries of concurrent op still failed");
  }

  //return value is true if child stats changed (and so need to potentially update this node)

  private int addPoint(NodeWrapper cluster, KdCell parent) {
    if (splitType == LEAF) {
      synchronized (this) {
        if (removedFromTree) {
          //this leaf node is no longer in the tree
          return -1;
        }
        final int numPoints = pointList.length;
        for (int i = 0; i < numPoints; i++) {
          if (pointList[i] == null) {
            pointList[i] = cluster;
            boolean changed = addToBoundingBoxIfChanges(cluster);
            return notifyPointAdded(cluster, changed) ? 1 : 0;
          }
        }
        //if we get here the point list was full so we need to subdivide the node
        NodeWrapper fullList[] = new NodeWrapper[numPoints + 1];
        System.arraycopy(pointList, 0, fullList, 0, numPoints);
        fullList[numPoints] = cluster;
        KdCell subtree = subdivide(fullList, 0, numPoints + 1, null, this);
        //substitute refined subtree for ourself by changing parent's child ptr
        synchronized (parent) {
          if (parent.removedFromTree) {
            //if parent no longer valid, retry from beginning
            return -1;
          }
          if (parent.leftChild == this) {
            parent.leftChild = subtree;
          } else if (parent.rightChild == this) {
            parent.rightChild = subtree;
          } else {
            //pointer was changed by someone else
            throw new RuntimeException();
          }
          this.removedFromTree = true;
        }
      }
      //assume changed as its not easy to check for changes when refining leaf to subtree
      return 1;
    }
    //its an interior node, so see which child should receive this new point
    float val = findSplitComponent(cluster, splitType);
    KdCell child = val <= splitValue ? leftChild : rightChild;
    int status = child.addPoint(cluster, this);
    if (status == 1) {
      synchronized (this) {
        if (removedFromTree) {
          return 1;
        }
        //if node is no longer in the tree, tell parent to check for changes, but don't bother updating this node
        boolean changed = addToBoundingBoxIfChanges(cluster);
        changed = notifyPointAdded(cluster, changed);
        status = changed ? 1 : 0;
      }
    }
    return status;
  }

  public final boolean remove(final NodeWrapper cluster) {
    return remove(cluster, MethodFlag.ALL);
  }

  /**
   * Remove a ClusterKDWrapper from the octree.  Returns true if found and removed
   * and false otherwise.  Will un-subdivide if count is low enough but does not
   * trigger rebalancing of the tree.
   */
  boolean remove(final NodeWrapper cluster, byte flags) {
    if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.CHECK_CONFLICT)) {
      cm.addRemoveProlog(cluster);
    }
    if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.SAVE_UNDO)) {
      GaloisRuntime.getRuntime().onUndo(Iteration.getCurrentIteration(), new Callback() {
        @Override
        public void call() {
          add(cluster, MethodFlag.NONE);
        }
      });
    }
    for (int i = 0; i < RETRY_LIMIT; i++) {
      int ret = removePoint(cluster, null, null);
      if (ret == -2) {
        throw new RuntimeException("cannot remove cluster");
      } else if (ret == -1) {
        if (isFineLoggable) {
          logger.fine("retrying removal");
        }
      } else if (ret == 0 || ret == 1) {
        return true;
      } else {
        throw new RuntimeException();
      }
    }
    throw new RuntimeException("remove failed after repeated retries");
  }

  private int removePoint(NodeWrapper inRemove, KdCell parent, KdCell grandparent) {
    if (splitType == LEAF) {
      //look for it in list of points
      synchronized (this) {
        if (removedFromTree) {
          //this leaf node is no longer in the tree
          return -1;
        }
        int index = -1;
        int count = 0;
        for (int i = 0; i < pointList.length; i++) {
          if (pointList[i] == inRemove) {
            index = i;
          }
          if (pointList[i] != null) {
            count++;
          }
        }
        if (index < 0) {
          // instead of throwing NoSuchElementException
          return -2;
        }
        if (count == 1 && parent != null && grandparent != null) {
          //snip parent and this node out of the tree and replace with parent's other child
          synchronized (parent) {
            synchronized (grandparent) {
              if (parent.removedFromTree || grandparent.removedFromTree) {
                //tree structure status, so retry op
                return -1;
              }
              KdCell otherChild = null;
              if (parent.leftChild == this) {
                otherChild = parent.rightChild;
              } else if (parent.rightChild == this) {
                otherChild = parent.leftChild;
              } else {
                throw new RuntimeException();
              }
              this.removedFromTree = true;
              parent.removedFromTree = true;
              if (grandparent.leftChild == parent) {
                grandparent.leftChild = otherChild;
              } else if (grandparent.rightChild == parent) {
                grandparent.rightChild = otherChild;
              } else {
                throw new RuntimeException();
              }
              return 1;
            }
          }
        }
        //once found, remove the point and recompute our bounding box
        pointList[index] = null;
        boolean changed = recomputeLeafBoundingBoxIfChanges();
        changed = notifyContentsRebuilt(changed);
        return changed ? 1 : 0;
      }
    }
    //otherwise its an interior node, so find which child should contain the point
    float val = findSplitComponent(inRemove, splitType);
    KdCell child = val <= splitValue ? leftChild : rightChild;
    int status = child.removePoint(inRemove, this, parent);
    if (status == 1) {
      synchronized (this) {
        if (removedFromTree) {
          return 1;
        }
        //if node is no longer in the tree, tell parent to check for changes, but don't bother updating this node
        boolean changed = recomputeParentBoundingBoxIfChanges();
        status = notifyContentsRebuilt(changed) ? 1 : 0;
      }
    } else if (status == 0) {
      //not sure this check is necessary, but leaving it in as a precaution for now
      status = removedFromTree ? 1 : 0;
    }
    return status;
  }

  public final NodeWrapper getAny(double ranNum) {
    return getAny(ranNum, MethodFlag.ALL);
  }

  NodeWrapper getAny(double ranNum, byte flags) {
    boolean checkConflict = GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.CHECK_CONFLICT);
    KdTreeConflictManager.LocalEntryLog finishedTail = checkConflict ? cm.readBestMatchProlog() : null;
    NodeWrapper retval = internalGetAny(ranNum);
    if (checkConflict) {
      cm.readEpilog(retval, finishedTail);
    }
    return retval;
  }

  /**
   * Returns some point from the kdtree.  Will not return null unless there are no points left
   * in the tree.  Random number helps randomize the selection from the tree, but there is no
   * guarantee about exactly which point will be returned
   */
  NodeWrapper internalGetAny(double ranNum) {
    NodeWrapper retval = null;
    if (splitType == LEAF) {
      int length = pointList.length;
      int i = (int) (ranNum * length);
      for (int j = 0; j < length; j++) {
        retval = pointList[i];
        if (retval != null) {
          return retval;
        }
        i = (i + 1) % length;
      }
    } else {
      if (ranNum < 0.5) {
        ranNum *= 2;
        retval = leftChild.internalGetAny(ranNum);
        if (retval == null) {
          retval = rightChild.internalGetAny(ranNum);
        }
      } else {
        ranNum = 2 * ranNum - 1.0;
        retval = rightChild.internalGetAny(ranNum);
        if (retval == null) {
          retval = leftChild.internalGetAny(ranNum);
        }
      }
    }
    return retval;
  }

  public final boolean contains(final NodeWrapper inPoint) {
    return contains(inPoint, MethodFlag.ALL);
  }

  boolean contains(final NodeWrapper point, byte flags) {
    if (GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.CHECK_CONFLICT)) {
      cm.addRemoveProlog(point);
    }
    boolean retval = internalContains(point);
    return retval;
  }

  boolean internalContains(NodeWrapper point) {
    if (splitType == LEAF) {
      //look for it in list of points
      for (NodeWrapper aPointList : pointList) {
        if (aPointList == point) {
          return true;
        }
      }
      return false;
    }
    //otherwise its an interior node, so find which child should contain the point
    float val = findSplitComponent(point, splitType);
    KdCell child = val <= splitValue ? leftChild : rightChild;
    return child.internalContains(point);
  }

  /**
   * Perform a variety of consistency checks on the tree and throws an error if any of them fail
   * This method is not concurrent safe.
   */
  boolean isOkay() {
    if (removedFromTree) {
      throw new IllegalStateException("removed flag set for node still in tree");
    }
    if (splitType == LEAF) {
      if (leftChild != null || rightChild != null) {
        throw new IllegalStateException("leaf has child");
      }
      if (pointList.length != MAX_POINTS_IN_CELL) {
        throw new IllegalStateException("point list is wrong size");
      }
      //check that the bounding box is right
      float xMinNew = Float.MAX_VALUE, yMinNew = Float.MAX_VALUE, zMinNew = Float.MAX_VALUE;
      float xMaxNew = -Float.MAX_VALUE, yMaxNew = -Float.MAX_VALUE, zMaxNew = -Float.MAX_VALUE;
      for (NodeWrapper aPointList : pointList) {
        if (aPointList == null) {
          continue;
        }
        float x = aPointList.getX();
        float y = aPointList.getY();
        float z = aPointList.getZ();
        xMinNew = Math.min(x, xMinNew);
        yMinNew = Math.min(y, yMinNew);
        zMinNew = Math.min(z, zMinNew);
        xMaxNew = Math.max(x, xMaxNew);
        yMaxNew = Math.max(y, yMaxNew);
        zMaxNew = Math.max(z, zMaxNew);
      }
      if (xMin != xMinNew || yMin != yMinNew || zMin != zMinNew) {
        throw new IllegalStateException("bad bounding box");
      }
      if (xMax != xMaxNew || yMax != yMaxNew || zMax != zMaxNew) {
        throw new IllegalStateException("bad bounding box");
      }
    } else { //its an interior node
      leftChild.isOkay();
      rightChild.isOkay();
      if (pointList != null) {
        throw new IllegalStateException("split nodes should not contain points");
      }
      if (xMin != Math.min(leftChild.xMin, rightChild.xMin)) {
        throw new IllegalStateException("bad bounding box");
      }
      if (yMin != Math.min(leftChild.yMin, rightChild.yMin)) {
        throw new IllegalStateException("bad bounding box");
      }
      if (zMin != Math.min(leftChild.zMin, rightChild.zMin)) {
        throw new IllegalStateException("bad bounding box");
      }
      if (xMax != Math.max(leftChild.xMax, rightChild.xMax)) {
        throw new IllegalStateException("bad bounding box");
      }
      if (yMax != Math.max(leftChild.yMax, rightChild.yMax)) {
        throw new IllegalStateException("bad bounding box");
      }
      if (zMax != Math.max(leftChild.zMax, rightChild.zMax)) {
        throw new IllegalStateException("bad bounding box");
      }
      switch (splitType) {
      case SPLIT_X:
        if (leftChild.xMax > splitValue || rightChild.xMin < splitValue) {
          throw new IllegalStateException("incorrect split");
        }
        break;
      case SPLIT_Y:
        if (leftChild.yMax > splitValue || rightChild.yMin < splitValue) {
          throw new IllegalStateException("incorrect split");
        }
        break;
      case SPLIT_Z:
        if (leftChild.zMax > splitValue || rightChild.zMin < splitValue) {
          throw new IllegalStateException("incorrect split");
        }
        break;
      default:
        throw new IllegalStateException("bad split type");
      }
    }
    return true;
  }

}
