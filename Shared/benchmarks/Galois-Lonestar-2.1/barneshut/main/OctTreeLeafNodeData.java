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

File: OctTreeLeafNodeData.java 

 */

package barneshut.main;

/**
 * This class defines objects to hold the node data in the leaf nodes (the
 * bodies) of the tree built by the Barnes Hut application
 * {@link barneshut.main.Main}.
 */
class OctTreeLeafNodeData extends OctTreeNodeData { // the tree leaves are the bodies
	double velx;
	double vely;
	double velz;
	double accx;
	double accy;
	double accz;

	/**
	 * Constructor that initializes the mass, position, velocity, and acceleration
	 * with zeros.
	 */
	public OctTreeLeafNodeData() {
		super(0.0, 0.0, 0.0);
		velx = 0.0;
		vely = 0.0;
		velz = 0.0;
		accx = 0.0;
		accy = 0.0;
		accz = 0.0;
	}

	/**
	 * This method determines whether a tree node is a leaf.
	 * 
	 * @return a boolean indicating whether the current object is a leaf node
	 */
	@Override
	public boolean isLeaf() {
		return true;
	}

	/**
	 * This method sets the velocity to the passed in values.
	 * 
	 * @param x
	 *          double value used to initialize the x component of the velocity
	 * @param y
	 *          double value used to initialize the y component of the velocity
	 * @param z
	 *          double value used to initialize the z component of the velocity
	 */
	public void setVelocity(double x, double y, double z) {
		velx = x;
		vely = y;
		velz = z;
	}

	/**
	 * This method determines whether the current node has the same position as
	 * the passed node.
	 * 
	 * @param o
	 *          the object (a node) whose position is compared to the current
	 *          node's position
	 * @return a boolean indicating whether the position is identical
	 */
	@Override
	public boolean equals(Object o) {
		OctTreeLeafNodeData n = (OctTreeLeafNodeData) o;
		return (this.posx == n.posx && this.posy == n.posy && this.posz == n.posz);
	}

	/**
	 * This method converts the mass, position, velocity, and acceleration
	 * information into a string.
	 * 
	 * @return a string that summarizes the values in this node
	 */
	@Override
	public String toString() {
		String result = super.toString();
		result += "vel = (" + velx + "," + vely + "," + velz + ")";
		result += "acc = (" + accx + "," + accy + "," + accz + ")";
		return result;
	}

	@Override
	public Object gclone() {
		OctTreeLeafNodeData copy = new OctTreeLeafNodeData();
		copy.posx = posx;
		copy.posy = posy;
		copy.posz = posz;
		copy.mass = mass;
		copy.velx = velx;
		copy.vely = vely;
		copy.velz = velz;
		copy.accx = accx;
		copy.accy = accy;
		copy.accz = accz;
		return copy;
	}

	@Override
	public void restoreFrom(Object copy) {
		OctTreeLeafNodeData data = (OctTreeLeafNodeData) copy;
		posx = data.posx;
		posy = data.posy;
		posz = data.posz;
		mass = data.mass;
		velx = data.velx;
		vely = data.vely;
		velz = data.velz;
		accx = data.accx;
		accy = data.accy;
		accz = data.accz;
	}

	@Override
	public int hashCode() {
		return (int)(2011 * this.posx + 1999 * this.posy + this.posz);
	}
}
