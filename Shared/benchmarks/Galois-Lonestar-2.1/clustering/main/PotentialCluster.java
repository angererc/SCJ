/*
 * Copyright 2006 Program of Computer Graphics, Cornell University
 *     580 Rhodes Hall
 *     Cornell University
 *     Ithaca NY 14853
 * Web: http://www.graphics.cornell.edu/
 *
 * Not for commercial use. Do not redistribute without permission.
 */

package clustering.main;

final class PotentialCluster {

  final NodeWrapper original;
  NodeWrapper closest;
  double clusterSize;

  PotentialCluster(NodeWrapper original) {
    this.original = original;
    closest = null;
    clusterSize = Double.MAX_VALUE;
  }

}
