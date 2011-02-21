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

class ClusterNode extends AbstractNode {

	private AbstractNode leftChild;
	private AbstractNode rightChild;
	private float boxRadiusX;
	private float boxRadiusY;
	private float boxRadiusZ;
	private LeafNode[] reps;
	private float coneDirX;
	private float coneDirY;
	private float coneDirZ;
	private float coneCos;


	ClusterNode() {
	}

	public void setBox(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
		x = 0.5f * (minX + maxX);
		y = 0.5f * (minY + maxY);
		z = 0.5f * (minZ + maxZ);
		boxRadiusX = 0.5f * (maxX - minX);
		boxRadiusY = 0.5f * (maxY - minY);
		boxRadiusZ = 0.5f * (maxZ - minZ);
	}

	public void setChildren(AbstractNode inLeft, AbstractNode inRight, double repRandomNum) {
		leftChild = inLeft;
		rightChild = inRight;
		setSummedIntensity(leftChild, rightChild);
		setCombinedFlags(leftChild, rightChild);
		//we only apply clamping to nodes that are low in the tree
		float ranVec[] = repRandomNums[(int) (repRandomNum * repRandomNums.length)];
		if (globalMultitime) {
			int numReps = endTime - startTime + 1;
			if (reps == null || reps.length < numReps) {
				reps = new LeafNode[numReps];
			} else {
				for (int j = numReps; j < reps.length; j++) {
					reps[j] = null;
				} //fill unused values will nulls
			}
			if (leftChild.isLeaf()) {
				LeafNode leftLeaf = (LeafNode) leftChild;
				if (rightChild.isLeaf()) {
					chooseRepsWithTime(reps, this, ranVec, leftLeaf, (LeafNode) rightChild);
				} else {
					chooseRepsWithTime(reps, this, ranVec, (ClusterNode) rightChild, leftLeaf); //note: operation is symmectric so we just interchange the children in the call
				}
			} else {
				ClusterNode leftClus = (ClusterNode) leftChild;
				if (rightChild.isLeaf()) {
					chooseRepsWithTime(reps, this, ranVec, leftClus, (LeafNode) rightChild);
				} else {
					chooseRepsWithTime(reps, this, ranVec, leftClus, (ClusterNode) rightChild);
				}
			}
		} else {
			if (reps == null || reps.length != globalNumReps) {
				reps = new LeafNode[globalNumReps];
			}
			if (leftChild.isLeaf()) {
				LeafNode leftLeaf = (LeafNode) leftChild;
				if (rightChild.isLeaf()) {
					chooseRepsNoTime(reps, this, ranVec, leftLeaf, (LeafNode) rightChild);
				} else {
					chooseRepsNoTime(reps, this, ranVec, (ClusterNode) rightChild, leftLeaf); //note: operation is symmectric so we just interchange the children in the call
				}
			} else {
				ClusterNode leftClus = (ClusterNode) leftChild;
				if (rightChild.isLeaf()) {
					chooseRepsNoTime(reps, this, ranVec, leftClus, (LeafNode) rightChild);
				} else {
					chooseRepsNoTime(reps, this, ranVec, leftClus, (ClusterNode) rightChild);
				}
			}
		}
	}

	private static void chooseRepsNoTime(LeafNode repArr[], AbstractNode parent, float ranVec[], LeafNode left,
			LeafNode right) {
		float totalInten = parent.getScalarTotalIntensity();
		float leftInten = left.getScalarTotalIntensity();
		float nextTest = ranVec[0] * totalInten;
		for (int i = 0; i < repArr.length - 1; i++) {
			float test = nextTest;
			nextTest = ranVec[i + 1] * totalInten;
			repArr[i] = (test < leftInten) ? left : right;
		}
		repArr[repArr.length - 1] = (nextTest < leftInten) ? left : right;
	}

	private static void chooseRepsNoTime(LeafNode repArr[], AbstractNode parent, float ranVec[],
			ClusterNode left, LeafNode right) {
		float totalInten = parent.getScalarTotalIntensity();
		float leftInten = left.getScalarTotalIntensity();
		float nextTest = ranVec[0] * totalInten;
		for (int i = 0; i < repArr.length - 1; i++) {
			float test = nextTest;
			nextTest = ranVec[i + 1] * totalInten;
			repArr[i] = (test < leftInten) ? (left.reps[i]) : right;
		}
		repArr[repArr.length - 1] = (nextTest < leftInten) ? (left.reps[repArr.length - 1]) : right;
	}

	private static void chooseRepsNoTime(LeafNode repArr[], AbstractNode parent, float ranVec[],
			ClusterNode left, ClusterNode right) {
		float totalInten = parent.getScalarTotalIntensity();
		float leftInten = left.getScalarTotalIntensity();
		float nextTest = ranVec[0] * totalInten;
		for (int i = 0; i < repArr.length - 1; i++) {
			float test = nextTest;
			nextTest = ranVec[i + 1] * totalInten;
			repArr[i] = (test < leftInten) ? (left.reps[i]) : (right.reps[i]);
		}
		repArr[repArr.length - 1] = (nextTest < leftInten) ? (left.reps[repArr.length - 1])
				: (right.reps[repArr.length - 1]);
	}

	private static void chooseRepsWithTime(LeafNode repArr[], AbstractNode parent, float ranVec[],
			LeafNode left, LeafNode right) {
		int startTime = parent.startTime;
		int endTime = parent.endTime;
		float parentTotal = parent.getScalarTotalIntensity();
		float leftTotal = left.getScalarTotalIntensity();
		float nextTest = ranVec[startTime] * parentTotal * parent.getRelativeIntensity(startTime);
		float nextLeftInten = leftTotal * left.getRelativeIntensity(startTime);
		for (int t = startTime; t < endTime; t++) {
			float test = nextTest;
			float leftInten = nextLeftInten;
			nextTest = ranVec[t + 1] * parentTotal * parent.getRelativeIntensity(t + 1);
			nextLeftInten = leftTotal * left.getRelativeIntensity(t + 1);
			if (test == 0) {
				repArr[t - startTime] = null;
			} else {
				repArr[t - startTime] = (test < leftInten) ? left : right;
			}
		}
		if (nextTest == 0) {
			repArr[endTime - startTime] = null;
		} else {
			repArr[endTime - startTime] = (nextTest < nextLeftInten) ? left : right;
		}
	}

	private static void chooseRepsWithTime(LeafNode repArr[], AbstractNode parent, float ranVec[],
			ClusterNode left, LeafNode right) {
		int startTime = parent.startTime;
		int endTime = parent.endTime;
		float parentTotal = parent.getScalarTotalIntensity();
		float leftTotal = left.getScalarTotalIntensity();
		float nextTest = ranVec[startTime] * parentTotal * parent.getRelativeIntensity(startTime);
		float nextLeftInten = leftTotal * left.getRelativeIntensity(startTime);
		for (int t = startTime; t < endTime; t++) {
			float test = nextTest;
			float leftInten = nextLeftInten;
			nextTest = ranVec[t + 1] * parentTotal * parent.getRelativeIntensity(t + 1);
			nextLeftInten = leftTotal * left.getRelativeIntensity(t + 1);
			if (test == 0) {
				repArr[t - startTime] = null;
			} else {
				repArr[t - startTime] = (test < leftInten) ? (left.reps[t - left.startTime]) : right;
			}
		}
		if (nextTest == 0) {
			repArr[endTime - startTime] = null;
		} else {
			repArr[endTime - startTime] = (nextTest < nextLeftInten) ? (left.reps[endTime - left.startTime]) : right;
		}
	}

	private static void chooseRepsWithTime(LeafNode repArr[], AbstractNode parent, float ranVec[],
			ClusterNode left, ClusterNode right) {
		int startTime = parent.startTime;
		int endTime = parent.endTime;
		float parentTotal = parent.getScalarTotalIntensity();
		float leftTotal = left.getScalarTotalIntensity();
		float nextTest = ranVec[startTime] * parentTotal * parent.getRelativeIntensity(startTime);
		float nextLeftInten = leftTotal * left.getRelativeIntensity(startTime);
		for (int t = startTime; t < endTime; t++) {
			float test = nextTest;
			float leftInten = nextLeftInten;
			nextTest = ranVec[t + 1] * parentTotal * parent.getRelativeIntensity(t + 1);
			nextLeftInten = leftTotal * left.getRelativeIntensity(t + 1);
			if (test == 0) {
				repArr[t - startTime] = null;
			} else {
				repArr[t - startTime] = (test < leftInten) ? (left.reps[t - left.startTime])
						: (right.reps[t - right.startTime]);
			}
		}
		if (nextTest == 0) {
			repArr[endTime - startTime] = null;
		} else {
			repArr[endTime - startTime] = (nextTest < nextLeftInten) ? (left.reps[endTime - left.startTime])
					: (right.reps[endTime - right.startTime]);
		}
	}

	public float getBoxRadiusX() {
		return boxRadiusX;
	}

	public float getBoxRadiusY() {
		return boxRadiusY;
	}

	public float getBoxRadiusZ() {
		return boxRadiusZ;
	}

	public void setDirectionCone(float dirX, float dirY, float dirZ, float inConeCos) {
		coneDirX = dirX;
		coneDirY = dirY;
		coneDirZ = dirZ;
		coneCos = inConeCos;
	}

	public float getConeDirX() {
		return coneDirX;
	}

	public float getConeDirY() {
		return coneDirY;
	}

	public float getConeDirZ() {
		return coneDirZ;
	}

	public float getConeCos() {
		return coneCos;
	}

	public void findConeDirsRecursive(float fArr[], ClusterNode cArr[]) {
		findConeDirsRecursive(leftChild, fArr, 0, cArr, NodeWrapper.CONE_RECURSE_DEPTH - 1);
		findConeDirsRecursive(rightChild, fArr, 0, cArr, NodeWrapper.CONE_RECURSE_DEPTH - 1);
	}

	private static int findConeDirsRecursive(AbstractNode node, float fArr[], int numDirs, ClusterNode cArr[],
			int recurseDepth) {
		if (!node.isLeaf()) {
			ClusterNode clus = (ClusterNode) node;
			if (clus.coneCos == 1.0) {
				numDirs = addConeDir(fArr, numDirs, clus.coneDirX, clus.coneDirY, clus.coneDirZ);
			} else if (recurseDepth <= 0) {
				//find first empty slot and add this cluster there
				for (int i = 0; ; i++) {
					if (cArr[i] == null) {
						cArr[i] = clus;
						if (cArr[i + 1] != null) {
							throw new RuntimeException();
						}
						break;
					}
				}
			} else {
				numDirs = findConeDirsRecursive(clus.leftChild, fArr, numDirs, cArr, recurseDepth - 1);
				numDirs = findConeDirsRecursive(clus.rightChild, fArr, numDirs, cArr, recurseDepth - 1);
			}
		} else {
			LeafNode light = (LeafNode) node;
			numDirs = addConeDir(fArr, numDirs, light.dirX, light.dirY, light.dirZ);
		}
		return numDirs;
	}

	private static int addConeDir(float fArr[], int numDirs, float x, float y, float z) {
		//only add direction if it does not match any existing directions
		for (int i = 0; i < 3 * numDirs; i++) {
			if ((fArr[i] == x) && (fArr[i + 1] == y) && (fArr[i + 2] == z)) {
				return numDirs;
			}
		}
		int index = 3 * numDirs;
		fArr[index] = x;
		fArr[index + 1] = y;
		fArr[index + 2] = z;
		return numDirs + 1;
	}

	@Override
	boolean isLeaf() {
		return false;
	}

	@Override
	public int size() {
		// only leafs are counted
		return leftChild.size() + rightChild.size();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ClusterNode)) {
			return false;
		}
		ClusterNode other = (ClusterNode) obj;
		return leftChild.equals(other.leftChild) && rightChild.equals(other.rightChild);
	}

	@Override
	public int hashCode() {
		int left = leftChild == null ? super.hashCode() : leftChild.hashCode();
		int right = rightChild == null ? super.hashCode() : rightChild.hashCode();
		return 2011 * left + right;
	}
}
