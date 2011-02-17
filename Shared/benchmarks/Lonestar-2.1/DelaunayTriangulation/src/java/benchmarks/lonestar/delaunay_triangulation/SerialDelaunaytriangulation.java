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

package benchmarks.lonestar.delaunay_triangulation;

import util.Launcher;

import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutionException;

import objects.graph.ArrayIndexedGraph;
import objects.graph.IndexedGraph;
import objects.graph.Node;

public class SerialDelaunayTriangulation extends AbstractMain {

  public static void main(String[] args) throws ExecutionException, IOException {
    new SerialDelaunayTriangulation().run(args);
    System.err.println("Time: " + Launcher.getLauncher().elapsedTime(true));
  }

  @Override
  protected String getVersion() {
    return "handwritten serial";
  }

  @Override
  protected void triangulate(IndexedGraph<Element> mesh, Node<Element> largeNode) throws ExecutionException {
    Stack<Node<Element>> worklist = new Stack<Node<Element>>();
    worklist.add(largeNode);
    Launcher.getLauncher().startTiming();
    while (!worklist.isEmpty()) {
      Node<Element> curr_node = worklist.pop();
      if (curr_node.getData().processed) {
        continue;
      }
      Cavity cavity = new Cavity(mesh, curr_node);
      cavity.build();
      List<Node<Element>> newNodes = cavity.update();
      for (Node<Element> node : newNodes) {
        if (node.getData().tuples != null) {
          worklist.add(node);
        }
      }
    }
    Launcher.getLauncher().stopTiming();
  }

  @Override
  protected IndexedGraph<Element> createGraph() {
    return new ArrayIndexedGraph<Element>(3);
  }
}
