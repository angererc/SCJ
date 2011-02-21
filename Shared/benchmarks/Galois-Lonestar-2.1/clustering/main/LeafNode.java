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

/**
 * @author bjw +latest $Author: bjw $
 * @version $Revision: 1.6 $ $Date: 2006/01/13 17:32:53 $
 */
class LeafNode extends AbstractNode {

  //direction of maximum emission
  final float dirX;
  final float dirY;
  final float dirZ;

  /**
   * Creates a new instance of MLTreeLeafNode
   */
  public LeafNode(float x, float y, float z, float dirX, float dirY, float dirZ) {
    this.x = x;
    this.y = y;
    this.z = z;
    setIntensity(1.0 / Math.PI, (short) 0);
    this.dirX = dirX;
    this.dirY = dirY;
    this.dirZ = dirZ;
  }

  public float getDirX() {
    return dirX;
  }

  public float getDirY() {
    return dirY;
  }

  public float getDirZ() {
    return dirZ;
  }

  @Override
  boolean isLeaf() {
    return true;
  }

  @Override
  public int size() {
    return 1;
  }
}
