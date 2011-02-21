/*
 * Copyright 2004 Program of Computer Graphics, Cornell University
 *     580 Rhodes Hall
 *     Cornell University
 *     Ithaca NY 14853
 * Web: http://www.graphics.cornell.edu/
 *
 * Not for commercial use. Do not redistribute without permission.
 */

package clustering.main;

import galois.objects.MethodFlag;
import galois.runtime.GaloisRuntime;

/**
 * Acceleration structure for light tree building
 */
public final class KdTree extends KdCell {

  //minimum intensity of any light or cluster in this node, needed as part of cluster size metric
  private float minLightIntensity;
  private float maxConeCos;
  private float minHalfSizeX;
  private float minHalfSizeY;
  private float minHalfSizeZ;

  private KdTree() {
    super();
    minLightIntensity = Float.MAX_VALUE;
    maxConeCos = -1.0f;
    minHalfSizeX = Float.MAX_VALUE;
    minHalfSizeY = Float.MAX_VALUE;
    minHalfSizeZ = Float.MAX_VALUE;
  }

  //special constructor used internally when space for point list has already been allocated

  private KdTree(int inSplitType, float inSplitValue) {
    super(inSplitType, inSplitValue);
  }

  public static KdTree createTree(NodeWrapper inPoints[]) {
    KdTree root = (KdTree) subdivide(inPoints, 0, inPoints.length, null, new KdTree());
    root.cm = new KdTreeConflictManager();
    return root;
  }

  public NodeWrapper findBestMatch(final NodeWrapper inLight) {
    return findBestMatch(inLight, MethodFlag.ALL);
  }

  public NodeWrapper findBestMatch(final NodeWrapper inLight, byte flags) {
    boolean checkConflict = GaloisRuntime.getRuntime().needMethodFlag(flags, MethodFlag.CHECK_CONFLICT);
    KdTreeConflictManager.LocalEntryLog finishedTail = checkConflict ? cm.readBestMatchProlog() : null;
    PotentialCluster cluster = new PotentialCluster(inLight);
    if (splitType == LEAF) {
      findNearestRecursive(cluster);
    } else if (splitType == SPLIT_X) {
      recurse(cluster, inLight.x);
    } else if (splitType == SPLIT_Y) {
      recurse(cluster, inLight.y);
    } else if (splitType == SPLIT_Z) {
      recurse(cluster, inLight.z);
    } else {
      throw new RuntimeException();
    }
    if (checkConflict) {
      cm.bestMatchEpilog(cluster, finishedTail);
    }
    return cluster.closest;
  }

  private void findNearestRecursive(PotentialCluster potentialCluster) {
    if (!couldBeCloser(potentialCluster)) {
      return;
    }
    NodeWrapper from = potentialCluster.original;
    if (splitType == LEAF) {
      //if it is a leaf then compute potential cluster size with each individual light or cluster
      for (NodeWrapper aPointList : pointList) {
        if (aPointList != null && aPointList != potentialCluster.original) {
          double size = NodeWrapper.potentialClusterSize(from, aPointList);
          if (size < potentialCluster.clusterSize) {
            potentialCluster.closest = aPointList;
            potentialCluster.clusterSize = size;
          }
        }
      }
    } else if (splitType == SPLIT_X) {
      recurse(potentialCluster, from.x);
    } else if (splitType == SPLIT_Y) {
      recurse(potentialCluster, from.y);
    } else if (splitType == SPLIT_Z) {
      recurse(potentialCluster, from.z);
    } else {
      throw new RuntimeException("badness");
    }
  }

  private void recurse(PotentialCluster potentialCluster, float which) {
    //if its a interior node recurse on the closer child first
    if (which <= splitValue) {
      ((KdTree) leftChild).findNearestRecursive(potentialCluster);
      ((KdTree) rightChild).findNearestRecursive(potentialCluster);
    } else {
      ((KdTree) rightChild).findNearestRecursive(potentialCluster);
      ((KdTree) leftChild).findNearestRecursive(potentialCluster);
    }
  }

  /**
   * Determines if any element of this cell could be closer to the the cluster, outCluster, using
   * the metrics defined in inBuilder.
   *
   * @param outCluster the cluster to test
   * @param inBuilder  the builder defining closeness
   * @return true if an element could be closer, false otherwise
   */
  boolean couldBeCloser(PotentialCluster outCluster) {
    //first check to see if we can prove that none of our contents could be closer than the current closest
    NodeWrapper from = outCluster.original;
    //compute minumum offset to bounding box
    float a2 = xMin - from.x >= from.x - xMax ? xMin - from.x : from.x - xMax;
    //more than twice as fast as Math.max(a,0)
    float dx = (a2 >= 0) ? a2 : 0;
    float a1 = (yMin - from.y >= from.y - yMax) ? yMin - from.y : from.y - yMax;
    float dy = a1 >= 0 ? a1 : 0;
    float a = (zMin - from.z >= from.z - zMax) ? zMin - from.z : from.z - zMax;
    float dz = a >= 0 ? a : 0;
    //expand distance by half size of from's bounding box (distance is min to center of box)
    //and by half the minimum bounding box extents of any node in this cell
    dx += from.getHalfSizeX() + minHalfSizeX;
    dy += from.getHalfSizeY() + minHalfSizeY;
    dz += from.getHalfSizeZ() + minHalfSizeZ;
    //cone must be at least as big as the larger of from's and the smallest in this cell
    float coneCos = (maxConeCos >= from.coneCos) ? from.coneCos : maxConeCos;
    //minimum cluster intensity would be from's intensity plus smallest intensity inside this cell
    float intensity = minLightIntensity + from.light.getScalarTotalIntensity();
    double testSize = NodeWrapper.clusterSizeMetric(dx, dy, dz, coneCos, intensity);
    //return if our contents could be closer and so need to be checked
    //extra factor of 0.9999 is to correct for any roundoff error in computing minimum size
    return (outCluster.clusterSize >= 0.9999 * testSize);
  }

  /*--- Methods needed to implement as an extended KDCell in a KDTree ---*/

  /**
   * We provide this factory method so that KDCell can be subclassed.  Returns a new
   * uninitialized cell (also tried to reuse any preallocated array for holding children)
   * Used during cell subdivision.
   */
  @Override
  protected KdCell createNewBlankCell(int inSplitType, float inSplitValue) {
    return new KdTree(inSplitType, inSplitValue);
  }

  @Override
  protected boolean notifyContentsRebuilt(boolean changed) {
    //must recompute the min light intensity since the cells contents have changed
    if (splitType == LEAF) {
      float newMinInten = Float.MAX_VALUE;
      float newMaxCos = -1.0f;
      float newMinHX = Float.MAX_VALUE, newMinHY = Float.MAX_VALUE, newMinHZ = Float.MAX_VALUE;
      for (NodeWrapper aPointList : pointList) {
        if (aPointList == null) {
          continue;
        }
        float b3 = aPointList.light.getScalarTotalIntensity();
        newMinInten = (newMinInten >= b3) ? b3 : newMinInten;
        newMaxCos = (newMaxCos >= aPointList.coneCos) ? newMaxCos : aPointList.coneCos;
        float b2 = aPointList.getHalfSizeX();
        newMinHX = (newMinHX >= b2) ? b2 : newMinHX;
        float b1 = aPointList.getHalfSizeY();
        newMinHY = (newMinHY >= b1) ? b1 : newMinHY;
        float b = aPointList.getHalfSizeZ();
        newMinHZ = (newMinHZ >= b) ? b : newMinHZ;
      }
      if (changed) {
        minLightIntensity = newMinInten;
        maxConeCos = newMaxCos;
        minHalfSizeX = newMinHX;
        minHalfSizeY = newMinHY;
        minHalfSizeZ = newMinHZ;
      } else {
        if (minLightIntensity != newMinInten) {
          minLightIntensity = newMinInten;
          changed = true;
        }
        if (maxConeCos != newMaxCos) {
          maxConeCos = newMaxCos;
          changed = true;
        }
        if (minHalfSizeX != newMinHX) {
          minHalfSizeX = newMinHX;
          changed = true;
        }
        if (minHalfSizeY != newMinHY) {
          minHalfSizeY = newMinHY;
          changed = true;
        }
        if (minHalfSizeZ != newMinHZ) {
          minHalfSizeZ = newMinHZ;
          changed = true;
        }
      }
    } else {
      //its a split node
      KdTree left = (KdTree) leftChild;
      KdTree right = (KdTree) rightChild;
      if (changed) {
        minLightIntensity = (left.minLightIntensity >= right.minLightIntensity) ? right.minLightIntensity
            : left.minLightIntensity;
        maxConeCos = (left.maxConeCos >= right.maxConeCos) ? left.maxConeCos : right.maxConeCos;
        minHalfSizeX = (left.minHalfSizeX >= right.minHalfSizeX) ? right.minHalfSizeX : left.minHalfSizeX;
        minHalfSizeY = (left.minHalfSizeY >= right.minHalfSizeY) ? right.minHalfSizeY : left.minHalfSizeY;
        minHalfSizeZ = (left.minHalfSizeZ >= right.minHalfSizeZ) ? right.minHalfSizeZ : left.minHalfSizeZ;
      } else {
        float newMinInten = (left.minLightIntensity >= right.minLightIntensity) ? right.minLightIntensity
            : left.minLightIntensity;
        float newMaxCos = (left.maxConeCos >= right.maxConeCos) ? left.maxConeCos : right.maxConeCos;
        float newMinHX = (left.minHalfSizeX >= right.minHalfSizeX) ? right.minHalfSizeX : left.minHalfSizeX;
        float newMinHY = (left.minHalfSizeY >= right.minHalfSizeY) ? right.minHalfSizeY : left.minHalfSizeY;
        float newMinHZ = (left.minHalfSizeZ >= right.minHalfSizeZ) ? right.minHalfSizeZ : left.minHalfSizeZ;
        if (minLightIntensity != newMinInten) {
          minLightIntensity = newMinInten;
          changed = true;
        }
        if (maxConeCos != newMaxCos) {
          maxConeCos = newMaxCos;
          changed = true;
        }
        if (minHalfSizeX != newMinHX) {
          minHalfSizeX = newMinHX;
          changed = true;
        }
        if (minHalfSizeY != newMinHY) {
          minHalfSizeY = newMinHY;
          changed = true;
        }
        if (minHalfSizeZ != newMinHZ) {
          minHalfSizeZ = newMinHZ;
          changed = true;
        }
      }
    }
    return changed;
  }

  @Override
  protected boolean notifyPointAdded(NodeWrapper inPoint, boolean changed) {
    if (changed) {
      float b3 = inPoint.light.getScalarTotalIntensity();
      minLightIntensity = (minLightIntensity >= b3) ? b3 : minLightIntensity;
      maxConeCos = (maxConeCos >= inPoint.coneCos) ? maxConeCos : inPoint.coneCos;
      float b2 = inPoint.getHalfSizeX();
      minHalfSizeX = (minHalfSizeX >= b2) ? b2 : minHalfSizeX;
      float b1 = inPoint.getHalfSizeY();
      minHalfSizeY = (minHalfSizeY >= b1) ? b1 : minHalfSizeY;
      float b = inPoint.getHalfSizeZ();
      minHalfSizeZ = (minHalfSizeZ >= b) ? b : minHalfSizeZ;
    } else {
      float newInten = inPoint.light.getScalarTotalIntensity();
      float hx = inPoint.getHalfSizeX();
      float hy = inPoint.getHalfSizeY();
      float hz = inPoint.getHalfSizeZ();
      if (minLightIntensity > newInten) {
        minLightIntensity = newInten;
        changed = true;
      }
      if (maxConeCos < inPoint.coneCos) {
        maxConeCos = inPoint.coneCos;
        changed = true;
      }
      if (minHalfSizeX > hx) {
        minHalfSizeX = hx;
        changed = true;
      }
      if (minHalfSizeY > hy) {
        minHalfSizeY = hy;
        changed = true;
      }
      if (minHalfSizeZ > hz) {
        minHalfSizeZ = hz;
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Perform a variety of consistency checks on the tree and throws an error if any of them fail
   */
  @Override
  public boolean isOkay() {
    super.isOkay();
    float minLight = Float.MAX_VALUE;
    float maxCos = -1.0f;
    float minHX = Float.MAX_VALUE;
    float minHY = Float.MAX_VALUE;
    float minHZ = Float.MAX_VALUE;
    if (splitType == LEAF) {
      for (NodeWrapper aPointList : pointList) {
        if (aPointList == null) {
          continue;
        }
        minLight = Math.min(minLight, aPointList.light.getScalarTotalIntensity());
        maxCos = Math.max(maxCos, aPointList.coneCos);
        minHX = Math.min(minHX, aPointList.getHalfSizeX());
        minHY = Math.min(minHY, aPointList.getHalfSizeY());
        minHZ = Math.min(minHZ, aPointList.getHalfSizeZ());
      }
    } else {
      KdTree left = (KdTree) leftChild;
      KdTree right = (KdTree) rightChild;
      minLight = Math.min(left.minLightIntensity, right.minLightIntensity);
      maxCos = Math.max(left.maxConeCos, right.maxConeCos);
      minHX = Math.min(left.minHalfSizeX, right.minHalfSizeX);
      minHY = Math.min(left.minHalfSizeY, right.minHalfSizeY);
      minHZ = Math.min(left.minHalfSizeZ, right.minHalfSizeZ);
    }
    if (minLight != this.minLightIntensity) {
      throw new IllegalStateException("bad min light intensity");
    }
    if (maxCos != this.maxConeCos) {
      throw new IllegalStateException("bad max cone cos");
    }
    if (minHX != this.minHalfSizeX || minHY != this.minHalfSizeY || minHZ != this.minHalfSizeZ) {
      throw new IllegalStateException("bad min half size");
    }
    return true;
  }
}
