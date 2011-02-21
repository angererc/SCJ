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

import galois.runtime.Features;
import galois.runtime.Replayable;

/**
 * This class builds a light tree hierarchy using a bottom-up greedy approach
 * A tree builder object can only be used once
 */
public class NodeWrapper extends Box3d implements Replayable {

  static public final int CONE_RECURSE_DEPTH = 4;
  static final double GLOBAL_SCENE_DIAGONAL = 2.0;

  //The position bounding box is stored in the inherited min/max fields
  //If light directions are relevant then they are stored in the dirBox and coneCos

  /**
   * Wrapper for lights or clusters so we can put them into a kd-tree for fast searching for their
   * best clustering candidate
   */
  //light or cluster
  public final AbstractNode light;
  //bounding box of light directions on the unit sphere
  private Box3d dirBox;
  //cosine of cone of light directions
  public final float coneCos;
  public final float x;
  public final float y;
  public final float z;
  //objects to be used when computing direction cone angles
  private float[] coneDirs;
  private ClusterNode[] coneClusters;
  private final int descendents;

  private long rid;

  public NodeWrapper(ClusterNode inNode) {
    coneDirs = null;
    coneClusters = null;
    light = inNode;
    x = inNode.getX();
    y = inNode.getY();
    z = inNode.getZ();
    this.xMin = x - inNode.getBoxRadiusX();
    this.xMax = x + inNode.getBoxRadiusX();
    this.yMin = y - inNode.getBoxRadiusY();
    this.yMax = y + inNode.getBoxRadiusY();
    this.zMin = z - inNode.getBoxRadiusZ();
    this.zMax = z + inNode.getBoxRadiusZ();
    descendents = inNode.size();
    coneCos = inNode.getConeCos();
    if (coneCos != 1.0) {
      throw new RuntimeException("not yet implemented");
    }
    if (dirBox == null) {
      dirBox = new Box3d();
    }
    dirBox.addPoint(inNode.getConeDirX(), inNode.getConeDirY(), inNode.getConeDirZ());
    coneDirs = new float[3];
    coneDirs[0] = inNode.getConeDirX();
    coneDirs[1] = inNode.getConeDirY();
    coneDirs[2] = inNode.getConeDirZ();
    Features.getReplayFeature().onCreateReplayable(this);
  }

  public NodeWrapper(LeafNode inNode) {
    light = inNode;
    coneDirs = null;
    coneClusters = null;
    descendents = 1;
    setBox(inNode.getX(), inNode.getY(), inNode.getZ());
    if (dirBox == null) {
      dirBox = new Box3d();
    }
    dirBox.setBox(inNode.getDirX(), inNode.getDirY(), inNode.getDirZ());
    coneCos = 1.0f;
    coneDirs = new float[3];
    coneDirs[0] = inNode.getDirX();
    coneDirs[1] = inNode.getDirY();
    coneDirs[2] = inNode.getDirZ();
    x = 0.5f * (xMax + xMin);
    y = 0.5f * (yMax + yMin);
    z = 0.5f * (zMax + zMin);
    Features.getReplayFeature().onCreateReplayable(this);
  }

  public NodeWrapper(NodeWrapper leftChild, NodeWrapper rightChild, float tempFloatArr[], ClusterNode tempClusterArr[],
      RandomGenerator modRanGen) {
    if ((leftChild.x > rightChild.x) || ((leftChild.x == rightChild.x) && (leftChild.y > rightChild.y))
        || ((leftChild.x == rightChild.x) && (leftChild.y == rightChild.y) && (leftChild.z > rightChild.z))) {
      //swap them to make sure we are consistent about which node becomes the left and the right
      //this makes the trees easier to compare for equivalence when checking against another building method
      NodeWrapper temp = leftChild;
      leftChild = rightChild;
      rightChild = temp;
    }
    addBox(rightChild);
    addBox(leftChild);
    x = 0.5f * (xMax + xMin);
    y = 0.5f * (yMax + yMin);
    z = 0.5f * (zMax + zMin);
    descendents = leftChild.descendents + rightChild.descendents;
    //create new cluster 
    ClusterNode cluster = new ClusterNode();
    cluster.setBox(xMin, xMax, yMin, yMax, zMin, zMax);
    cluster.setChildren(leftChild.light, rightChild.light, modRanGen.nextDouble());
    light = cluster;
    //compute direction cone and set it in the cluster
    coneCos = computeCone(leftChild, rightChild, cluster);
    if (coneCos > -0.9f) {
      dirBox = new Box3d();
      dirBox.addBox(leftChild.dirBox);
      dirBox.addBox(rightChild.dirBox);
      cluster.findConeDirsRecursive(tempFloatArr, tempClusterArr);
      int numClus = 0;
      for (; tempClusterArr[numClus] != null; numClus++) {
      }
      if (numClus > 0) {
        coneClusters = new ClusterNode[numClus];
        for (int j = 0; j < numClus; j++) {
          coneClusters[j] = tempClusterArr[j];
          tempClusterArr[j] = null;
        }
      }
    }
    Features.getReplayFeature().onCreateReplayable(this);
  }

  static float computeCone(NodeWrapper a, NodeWrapper b, ClusterNode outCluster) {
    if (a.dirBox == null || b.dirBox == null) {
      return -1.0f;
    }
    //we use the circumscribed sphere around the dirBox to compute a conservative bounding cone
    float xMin = a.dirBox.xMin >= b.dirBox.xMin ? b.dirBox.xMin : a.dirBox.xMin;
    float yMin = a.dirBox.yMin >= b.dirBox.yMin ? b.dirBox.yMin : a.dirBox.yMin;
    float zMin = a.dirBox.zMin >= b.dirBox.zMin ? b.dirBox.zMin : a.dirBox.zMin;
    float xMax = a.dirBox.xMax >= b.dirBox.xMax ? a.dirBox.xMax : b.dirBox.xMax;
    float yMax = a.dirBox.yMax >= b.dirBox.yMax ? a.dirBox.yMax : b.dirBox.yMax;
    float zMax = a.dirBox.zMax >= b.dirBox.zMax ? a.dirBox.zMax : b.dirBox.zMax;

    float rad2 = ((xMax - xMin) * (xMax - xMin) + (yMax - yMin) * (yMax - yMin) + (zMax - zMin) * (zMax - zMin));
    float coneX = (xMin + xMax);
    float coneY = (yMin + yMax);
    float coneZ = (zMin + zMax);
    float center2 = coneX * coneX + coneY * coneY + coneZ * coneZ;
    if (center2 < 0.01) {
      return -1.0f; //too large to produce any reasonable cone smaller than the whole sphere
    }
    float invLen = 1.0f / (float) Math.sqrt(center2);
    //given the unit sphere and another sphere width radius^2 (rad2) and center^2 (center2)
    //we can compute the cos(angle) cone defined by its intersection with the unit sphere
    //note we actually keep 4*cetner2 and 4*rad2 to reduce number of multipications needed
    float minCos = (center2 + 4.0f - rad2) * 0.25f * invLen;
    if (minCos < -1.0f) {
      minCos = -1.0f;
    }
    if (outCluster != null) {
      outCluster.setDirectionCone(coneX * invLen, coneY * invLen, coneZ * invLen, minCos);
    }
    return minCos;
  }

  public float getX() {
    return x;
  }

  public float getY() {
    return y;
  }

  public float getZ() {
    return z;
  }

  float getHalfSizeX() {
    return xMax - x;
  }

  float getHalfSizeY() {
    return yMax - y;
  }

  float getHalfSizeZ() {
    return zMax - z;
  }

  static double potentialClusterSize(NodeWrapper a, NodeWrapper b) {
    float dx = (a.xMax >= b.xMax ? a.xMax : b.xMax) - (a.xMin >= b.xMin ? b.xMin : a.xMin);
    float dy = (a.yMax >= b.yMax ? a.yMax : b.yMax) - (a.yMin >= b.yMin ? b.yMin : a.yMin);
    float dz = (a.zMax >= b.zMax ? a.zMax : b.zMax) - (a.zMin >= b.zMin ? b.zMin : a.zMin);
    float minCos = computeCone(a, b, null);
    float maxIntensity = a.light.getScalarTotalIntensity() + b.light.getScalarTotalIntensity();
    return clusterSizeMetric(dx, dy, dz, minCos, maxIntensity);
  }

  /**
   * Compute a measure of the size of a light cluster
   */
  static double clusterSizeMetric(double xSize, double ySize, double zSize, double cosSemiAngle, double intensity) {
    double len2 = xSize * xSize + ySize * ySize + zSize * zSize;
    double angleFactor = (1 - cosSemiAngle) * GLOBAL_SCENE_DIAGONAL;
    return intensity * (len2 + angleFactor * angleFactor);
  }

  @Override
  public long getRid() {
    return rid;
  }

  @Override
  public void setRid(long rid) {
    this.rid = rid;
  }
}
