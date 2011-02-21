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

/**
 * An axis aligned bounding box that uses floats
 *
 * @author bjw +latest $Author: bjw $
 * @version $Revision: 1.1 $ $Date: 2005/12/16 23:08:50 $
 */
class Box3d {
  float xMin;
  float xMax;
  float yMin;
  float yMax;
  float zMin;
  float zMax;

  /**
   * Creates a new instance of TreeBuilderFloatBox
   */
  Box3d() {
    xMin = yMin = zMin = Float.MAX_VALUE;
    xMax = yMax = zMax = -Float.MAX_VALUE;
  }

  void setBox(float x, float y, float z) {
    xMin = xMax = x;
    yMin = yMax = y;
    zMin = zMax = z;
  }

  void addPoint(float x, float y, float z) {
    xMin = xMin >= x ? x : xMin;
    xMax = xMax >= x ? xMax : x;
    yMin = yMin >= y ? y : yMin;
    yMax = yMax >= y ? yMax : y;
    zMin = zMin >= z ? z : zMin;
    zMax = zMax >= z ? zMax : z;
  }

  void addBox(final Box3d box) {
    xMin = xMin >= box.xMin ? box.xMin : xMin;
    xMax = xMax >= box.xMax ? xMax : box.xMax;
    yMin = yMin >= box.yMin ? box.yMin : yMin;
    yMax = yMax >= box.yMax ? yMax : box.yMax;
    zMin = zMin >= box.zMin ? box.zMin : zMin;
    zMax = zMax >= box.zMax ? zMax : box.zMax;
  }

}
