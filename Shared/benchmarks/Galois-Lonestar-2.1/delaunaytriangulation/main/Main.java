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

File: Main.java 

 */

package delaunaytriangulation.main;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.LongGraph;
import galois.objects.graph.MorphGraph;
import galois.runtime.ForeachContext;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.LIFO;
import galois.runtime.wl.Priority;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import util.Launcher;
import util.fn.Lambda2Void;

public class Main extends AbstractMain {

  public static void main(String[] args) throws ExecutionException, IOException {
    Main main = new Main();
    main.run(args);
  }

  @Override
  public void triangulate(final LongGraph<Element> mesh, GNode<Element> largeNode) throws ExecutionException {
    Collection<GNode<Element>> initWL = new ArrayList<GNode<Element>>();
    initWL.add(largeNode);
    Launcher.getLauncher().startTiming();
    FullGaloisRuntime.foreach(initWL, new Lambda2Void<GNode<Element>, ForeachContext<GNode<Element>>>() {
      @Override
      public void call(GNode<Element> currNode, ForeachContext<GNode<Element>> ctx) {
    	  System.out.println("Main: called triangulate computation");
        final Element data = currNode.getData(MethodFlag.CHECK_CONFLICT);
        if (data.processed) {
          return;
        }
        Cavity cavity = new Cavity(mesh, currNode);
        cavity.build();
        List<GNode<Element>> newNodes = cavity.update();
        for (GNode<Element> node : newNodes) {
          if (node.getData(MethodFlag.NONE).tuples != null) {
            ctx.add(node, MethodFlag.NONE);
          }
        }
      }
    }, Priority.first(ChunkedFIFO.class).then(LIFO.class));
    Launcher.getLauncher().stopTiming();
  }

  @Override
  protected String getVersion() {
    return GaloisRuntime.getRuntime().useSerial() ? "serial" : "Galois";
  }

  @Override
  protected LongGraph<Element> createGraph() {
    final MorphGraph.LongGraphBuilder builder = new MorphGraph.LongGraphBuilder();
    return builder.directed(true).backedByVector(true).create();
  }

}
