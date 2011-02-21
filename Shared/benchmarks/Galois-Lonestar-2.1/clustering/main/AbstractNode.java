/*
 * Copyright 2005 Program of Computer Graphics, Cornell University
 *     580 Rhodes Hall
 *     Cornell University
 *     Ithaca NY 14853
 * Web: http://www.graphics.cornell.edu/
 *
 * Not for commercial use. Do not redistribute without permission.
 */

package clustering.main;

import java.util.Arrays;

/**
 * A Intensity spectrum which can vary over both color (RGB) and time (discrete instants)
 */
public abstract class AbstractNode {

  /**
   * Each node in a light cut tree is given a unique id between zero and the total number of nodes in the tree
   */
  int nodeIdAndFlags;
  /**
   * position of point or center of cluster's bounding box (or to-vector for infinite lights)
   */
  float x;
  float y;
  float z;
  /**
   * Intensity or strength of this point or cluster
   */
  private float intensityRed, intensityGreen, intensityBlue; //total intensity
  short startTime;
  short endTime;
  //start and end times represented in timeVector vector
  private float timeVector[]; //fractional intensity per time interval

  //if startTime==endTime then the Intensity time is always the same and points to this one
  private static final float singleTimeVector[] = { 1.0f };
  private static final int ML_CLAMP = 0x02; //subject to clamping?
  private static final int ML_COMBO_MASK = 0xFC; //flags that should be shared with a parent
  private static final int ML_MATCH_MASK = 0xFC;
  private static final int ML_ID_SHIFT = 8;
  /**
   * Number of representatives stored per node
   */
  static int globalNumReps = -1;
  /**
   * Does each representative represent a different time instant or are all at the same time instant
   */
  static boolean globalMultitime = false;
  static float[][] repRandomNums;

  /**
   * Creates a new instance of MLIntensity
   */
  AbstractNode() {
    startTime = -1; //deliberately invalid value
    nodeIdAndFlags = -1 << ML_ID_SHIFT; //must be set to a correct value later
  }

  /**
   * Get the total intensity of this light or cluster
   * as a scalar (ie averaged down to a single number)
   *
   * @return Scalar total intensity
   */
  public float getScalarTotalIntensity() {
    return (1.0f / 3.0f) * (intensityRed + intensityGreen + intensityBlue);
  }

  /**
   * What fraction of the total intensity was at the given time instant
   */
  float getRelativeIntensity(int inTime) {
    if (inTime < startTime || inTime > endTime) {
      return 0;
    }
    return timeVector[inTime - startTime];
  }

  /**
   * Set the Intensity of this node to be equal to the specified
   * spectrum scaled by the specified factor and at the single instant specified
   *
   * @param inScaleFactor Factor to scale spectrum by before setting Intensity
   */
  void setIntensity(double inScaleFactor, short inTime) {
    intensityRed = (float) inScaleFactor;
    intensityGreen = (float) inScaleFactor;
    intensityBlue = (float) inScaleFactor;
    if (inTime == -1) {
      inTime = 0;
    }
    if (inTime >= 0) {
      startTime = inTime;
      endTime = inTime;
      timeVector = singleTimeVector;
    } else {
      //negative value used as signal that should be uniform across all time
      int len = -inTime;
      startTime = 0;
      endTime = (short) (len - 1);
      timeVector = new float[len];
      Arrays.fill(timeVector, 1.0f / len);
      scaleIntensity(len);
    }
  }

  /**
   * Set this nodes maximum intensity to be the sum of the maximum intensities
   * of the two specified nodes.
   *
   * @param inNodeA Node to sum maximum intensity from
   * @param inNodeB Node to sum maximum intensity from
   */
  void setSummedIntensity(AbstractNode inA, AbstractNode inB) {
    intensityRed = inA.intensityRed + inB.intensityRed;
    intensityGreen = inA.intensityGreen + inB.intensityGreen;
    intensityBlue = inA.intensityBlue + inB.intensityBlue;
    startTime = inA.startTime < inB.startTime ? inA.startTime : inB.endTime;
    endTime = inA.startTime < inB.startTime ? inB.startTime : inA.endTime;
    if (startTime != endTime) {
      int len = endTime - startTime + 1;
      if (timeVector == null || timeVector.length < len) {
        timeVector = new float[len];
      } else {
        for (int i = 0; i < timeVector.length; i++) {
          timeVector[i] = 0;
        }
      }
      float weightA = inA.getScalarTotalIntensity();
      float weightB = inB.getScalarTotalIntensity();
      float invDenom = 1.0f / (weightA + weightB);
      weightA *= invDenom;
      weightB *= invDenom;
      for (int i = inA.startTime; i <= inA.endTime; i++) {
        timeVector[i - startTime] += weightA * inA.timeVector[i - inA.startTime];
      }
      for (int i = inB.startTime; i <= inB.endTime; i++) {
        timeVector[i - startTime] += weightB * inB.timeVector[i - inB.startTime];
      }
    } else {
      timeVector = singleTimeVector;
    }
  }

  /**
   * Scale the maximum intensity of this node by a given factor
   *
   * @param inScale Factor to scale maximum intensity by
   */
  void scaleIntensity(double inScale) {
    float scale = (float) inScale;
    intensityRed *= scale;
    intensityGreen *= scale;
    intensityBlue *= scale;
  }

  public static void setGlobalNumReps() {
    if (globalNumReps == 1) {
      //nothing changed
      return;
    }
    //trees must be rebuilt for this to take effect
    globalNumReps = 1;
    double inc = 1.0f / 1;

    RandomGenerator ranGen = new RandomGenerator();
    ranGen.setSeed(452389425623145845L);
    repRandomNums = new float[256][];
    for (int i = 0; i < repRandomNums.length; i++) {
      float ranVec[] = new float[1];
      //fill vector with uniform randomized numbers (uniformly distributed, jittered)
      for (int j = 0; j < ranVec.length; j++) {
        ranVec[j] = (float) ((j + ranGen.nextDouble()) * inc);
      }
      //now randomly permute the numbers
      for (int j = ranVec.length - 1; j > 0; j--) {
        int index = (int) ((j + 1) * ranGen.nextDouble());
        if (index > j) {
          throw new RuntimeException("badness " + index);
        }
        //swap index element with jth element
        float temp = ranVec[j];
        ranVec[j] = ranVec[index];
        ranVec[index] = temp;
      }
      //that's all now store the random vector for later use
      repRandomNums[i] = ranVec;
    }
  }

  public static void setGlobalMultitime() {
    //trees must be rebuilt for this to take effect
    globalMultitime = false;
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

  final void setCombinedFlags(AbstractNode a, AbstractNode b) {
    nodeIdAndFlags = (a.nodeIdAndFlags | b.nodeIdAndFlags) & ML_COMBO_MASK;
    nodeIdAndFlags |= a.nodeIdAndFlags & b.nodeIdAndFlags & ML_CLAMP;
    //clamp only if both children use clamping
    if ((a.nodeIdAndFlags & ML_MATCH_MASK) != (b.nodeIdAndFlags & ML_MATCH_MASK)) {
      throw new RuntimeException();
    }
  }

  abstract boolean isLeaf();

  protected abstract int size();
}
