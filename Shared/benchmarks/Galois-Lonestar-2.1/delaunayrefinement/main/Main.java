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

package delaunayrefinement.main;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.MorphGraph;
import galois.objects.graph.ObjectGraph;
import galois.objects.graph.ObjectUndirectedEdge;
import galois.runtime.Features;
import galois.runtime.ForeachContext;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.ReplayFeature;
import galois.runtime.WorkNotUsefulException;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.LIFO;
import galois.runtime.wl.Priority;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import util.Launcher;
import util.fn.Lambda2Void;

public class Main {

  public void run(String args[]) throws Exception {
	  FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
	  Features.initialize(FullGaloisRuntime.getFullRuntime().getMaxThreads(), ReplayFeature.Type.NO);
    if (args.length < 1) {
      System.err.println("Arguments: <input file> ");
      System.exit(1);
    }
    if (Launcher.getLauncher().isFirstRun()) {
      System.err.println();
      System.err.println("Lonestar Benchmark Suite v3.0");
      System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
      System.err.println("http://iss.ices.utexas.edu/lonestar/");
      System.err.println();
      System.err.printf("application: Delaunay Mesh Refinement (%s version)\n",
          GaloisRuntime.getRuntime().useSerial() ? "serial" : "Galois");
      System.err.println("Refines a Delaunay triangulation mesh such that no angle");
      System.err.println("in the mesh is less than 30 degrees");
      System.err.println("http://iss.ices.utexas.edu/lonestar/delaunayrefinement.html");
      System.err.println();
    }
    final MorphGraph.ObjectGraphBuilder builder = new MorphGraph.ObjectGraphBuilder();
    final ObjectGraph<Element, Element.Edge> mesh = builder.backedByVector(true).create();
    new Mesh().read(mesh, args[0]);
    Collection<GNode<Element>> badNodes = Mesh.getBad(mesh);

    if (Launcher.getLauncher().isFirstRun()) {
      System.err.printf("configuration: %d total triangles, %d bad triangles\n", mesh.size(), badNodes.size());
      System.err.println("number of threads: " + GaloisRuntime.getRuntime().getMaxThreads());
      System.err.println();
    }

    Launcher.getLauncher().startTiming();
    refine(mesh, badNodes);
    Launcher.getLauncher().stopTiming();

    if (Launcher.getLauncher().isFirstRun()) {
      verify(mesh);
    }
  }

  public void refine(ObjectGraph<Element, Element.Edge> mesh) throws ExecutionException {
    refine(mesh, Mesh.getBad(mesh));
  }

  private void refine(final ObjectGraph<Element, Element.Edge> mesh, Collection<GNode<Element>> badNodes)
      throws ExecutionException {
    // The parallel loop
    FullGaloisRuntime.foreach(badNodes, new Lambda2Void<GNode<Element>, ForeachContext<GNode<Element>>>() {
      @Override
      public void call(GNode<Element> item, ForeachContext<GNode<Element>> ctx) {
        if (!mesh.contains(item, MethodFlag.CHECK_CONFLICT)) {
          WorkNotUsefulException.throwException();
        }
        Cavity cavity = new Cavity(mesh);
        cavity.initialize(item);
        cavity.build();
        cavity.update();
        //remove the old data
        List<GNode<Element>> preNodes = cavity.getPre().getNodes();
        for (int i = 0; i < preNodes.size(); i++) {
          mesh.remove(preNodes.get(i), MethodFlag.NONE);
        }
        //add new data
        Subgraph postSubgraph = cavity.getPost();
        List<GNode<Element>> postNodes = postSubgraph.getNodes();
        for (int i = 0; i < postNodes.size(); i++) {
          GNode<Element> node = postNodes.get(i);
          mesh.add(node, MethodFlag.NONE);
          Element element = node.getData(MethodFlag.NONE);
          if (element.isBad()) {
            ctx.add(node, MethodFlag.NONE);
          }
        }
        List<ObjectUndirectedEdge<Element, Element.Edge>> postEdges = postSubgraph.getEdges();
        for (int i = 0; i < postEdges.size(); i++) {
          ObjectUndirectedEdge<Element, Element.Edge> edge = postEdges.get(i);
          boolean ret = mesh.addEdge(edge.getSrc(), edge.getDst(), edge.getData(), MethodFlag.NONE);
          assert ret;
        }
        if (mesh.contains(item, MethodFlag.NONE)) {
          ctx.add(item, MethodFlag.NONE);
        }
      }
    }, Priority.first(ChunkedFIFO.class).thenLocally(LIFO.class));
  }

  private void verify(ObjectGraph<Element, Element.Edge> result) {
    if (!Mesh.verify(result)) {
      throw new IllegalStateException("refinement failed.");
    }
    int size = Mesh.getBad(result).size();
    if (size != 0) {
      throw new IllegalStateException("refinement failed\n" + "still have " + size + " bad triangles left.\n");
    }
    System.out.println("Refinement OK");
  }

  public static void main(String[] args) throws Exception {
    new Main().run(args);
  }
}
