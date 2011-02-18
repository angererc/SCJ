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

File: OctTreeNodeData.java 

 */

package barneshut.main;

import galois.objects.AbstractNoConflictBaseObject;

/**
 * This class defines objects to hold the node data in the internal tree nodes
 * of the Barnes Hut application {@link barneshut.main.Main}.
 */
class OctTreeNodeData extends AbstractNoConflictBaseObject { // the internal nodes are cells that summarize their children's properties
  double mass;
  double posx;
  double posy;
  double posz;

  /**
   * Constructor that initializes the mass with zero and the position with the
   * passed in values.
   * 
   * @param px
   *          double value used to initialize the x coordinate
   * @param py
   *          double value used to initialize the y coordinate
   * @param pz
   *          double value used to initialize the z coordinate
   */
  public OctTreeNodeData(double px, double py, double pz) {
    mass = 0.0;
    posx = px;
    posy = py;
    posz = pz;
  }

  /**
   * This method determines whether a tree node is a leaf.
   * 
   * @return a boolean indicating whether the current object is a leaf node
   */
  public boolean isLeaf() {
    return false;
  }

  /**
   * This method converts the mass and position information into a string.
   * 
   * @return a string that summarizes the values in this node
   */
  @Override
  public String toString() {
    String result = "mass = " + mass;
    result += "pos = (" + posx + "," + posy + "," + posz + ")";
    return result;
  }

  /**
   * This method reads the x coordinate of this node.
   * 
   * @return a double value that represents the x coordinate
   */
  public double posx() {
    return posx;
  }

  /**
   * This method reads the y coordinate of this node.
   * 
   * @return a double value that represents the y coordinate
   */
  public double posy() {
    return posy;
  }

  /**
   * This method reads the z coordinate of this node.
   * 
   * @return a double value that represents the z coordinate
   */
  public double posz() {
    return posz;
  }

  @Override
  public Object gclone() {
    OctTreeNodeData copy = new OctTreeNodeData(posx, posy, posz);
    copy.mass = mass;
    return copy;
  }

  @Override
  public void restoreFrom(Object copy) {
    OctTreeNodeData data = (OctTreeNodeData) copy;
    posx = data.posx;
    posy = data.posy;
    posz = data.posz;
    mass = data.mass;
  }
}
