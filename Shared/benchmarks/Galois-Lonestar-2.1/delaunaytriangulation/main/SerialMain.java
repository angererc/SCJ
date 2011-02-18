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

package delaunaytriangulation.main;

import galois.objects.graph.GNode;
import galois.objects.graph.LongGraph;
import galois.objects.graph.MorphGraph;
import util.Launcher;

import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutionException;

public class SerialMain extends AbstractMain {

  public static void main(String[] args) throws ExecutionException, IOException {
    new SerialMain().run(args);
  }

  @Override
  protected String getVersion() {
    return "handwritten serial";
  }

  @Override
  protected void triangulate(LongGraph<Element> mesh, GNode<Element> largeNode) throws ExecutionException {
    Stack<GNode<Element>> worklist = new Stack<GNode<Element>>();
    worklist.add(largeNode);
    Launcher.getLauncher().startTiming();
    while (!worklist.isEmpty()) {
      GNode<Element> curr_node = worklist.pop();
      if (curr_node.getData().processed) {
        continue;
      }
      Cavity cavity = new Cavity(mesh, curr_node);
      cavity.build();
      List<GNode<Element>> newNodes = cavity.update();
      for (GNode<Element> node : newNodes) {
        if (node.getData().tuples != null) {
          worklist.add(node);
        }
      }
    }
    Launcher.getLauncher().stopTiming();
  }

  @Override
  protected LongGraph<Element> createGraph() {
    final MorphGraph.LongGraphBuilder builder = new MorphGraph.LongGraphBuilder();
    return builder.directed(true).backedByVector(true).serial(true).create();
  }
}
