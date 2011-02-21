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

File: SerialMain.java 

*/



package clustering.main;

import util.Launcher;

import java.util.LinkedList;
import java.util.Queue;

public class SerialMain {

  public static void main(String[] args) throws Exception {
    int count = args.length > 0 ? Integer.valueOf(args[0]) : 10000;
    if (Launcher.getLauncher().isFirstRun()) {
      System.err.println();
      System.err.println("Lonestar Benchmark Suite v2.1");
      System.err.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
      System.err.println("http://iss.ices.utexas.edu/lonestar/");
      System.err.println();
      System.err.println("application: Unordered Agglomerative Clustering (serial version)");
      System.err.println("Unordered Implementation of the well-known data-mining algorithm");
      System.err.println("Agglomerative Clustering");
      System.err.println("http://iss.ices.utexas.edu/lonestar/agglomerativeclustering.html");
      System.err.println();
      System.out.println("input size: " + count);
    }
    LeafNode[] lights = Main.randomGenerate(count);
    new SerialMain().clustering(lights);
  }

  public AbstractNode clustering(LeafNode[] inLights) {
    RandomGenerator repRanGen = new RandomGenerator(4523489623489L);
    //used to choose which light is the representative light
    int tempSize = (1 << NodeWrapper.CONE_RECURSE_DEPTH) + 1;
    float tempFloatArr[] = new float[3 * tempSize]; //temporary arrays used during cluster construction
    ClusterNode tempClusterArr[] = new ClusterNode[tempSize];

    Queue<NodeWrapper> worklist = new LinkedList<NodeWrapper>();
    NodeWrapper baseList[] = new NodeWrapper[inLights.length];
    for (int i = 0; i < baseList.length; i++) {
      baseList[i] = new NodeWrapper(inLights[i]);
      worklist.add(baseList[i]);
    }
    Launcher.getLauncher().startTiming();
    KdTree kdTree = KdTree.createTree(baseList);
    while (!worklist.isEmpty()) {
      NodeWrapper cluster = worklist.remove();
      NodeWrapper currentCluster = cluster;
      while (currentCluster != null && kdTree.contains(currentCluster)) {
        NodeWrapper partner = kdTree.findBestMatch(currentCluster);
        if (partner == null) {
          break;
        }
        if (currentCluster == kdTree.findBestMatch(partner)) {
          if (kdTree.remove(partner)) {
            NodeWrapper newNode = new NodeWrapper(currentCluster, partner, tempFloatArr, tempClusterArr,
                repRanGen);
            kdTree.add(newNode);
            worklist.add(newNode);
            kdTree.remove(currentCluster);
          }
          currentCluster = null;
        } else {
          if (currentCluster == cluster) {
            worklist.add(currentCluster);
          }
          currentCluster = partner;
        }
      }
    }
    Launcher.getLauncher().stopTiming();
    NodeWrapper retval = kdTree.getAny(0.5);
    return retval.light;
  }
}
