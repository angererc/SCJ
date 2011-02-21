package delaunayrefinement.main;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.MorphGraph;
import galois.objects.graph.ObjectGraph;
import galois.objects.graph.ObjectUndirectedEdge;
import galois.runtime.Features;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.ReplayFeature;
import galois.runtime.WorkNotUsefulException;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.LIFO;
import galois.runtime.wl.Priority;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.ReducedGaloisRuntime;
import galois_scj.UnorderedGaloisSCJComputation;

import java.util.Collection;
import java.util.List;

import scj.Task;
import util.Launcher;

public class SCJMain {

	public void run(String args[]) throws Exception {
		//FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		ReducedGaloisRuntime.initialize(false, false);
		Features.initialize(GaloisRuntime.getRuntime().getMaxThreads(), ReplayFeature.Type.NO);
		
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
			System.err.printf("application: Delaunay Mesh Refinement (SCJ version)\n");
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
		this.scjMainTask_refine(new Task<Void>(), mesh, badNodes);
		Launcher.getLauncher().stopTiming();

		if (Launcher.getLauncher().isFirstRun()) {
			verify(mesh);
		}
	}

	public void scjMainTask_refine(Task<Void> now, final ObjectGraph<Element, Element.Edge> mesh, Collection<GNode<Element>> badNodes)
	throws Exception {
		
		GaloisSCJComputation<GNode<Element>> computation = new UnorderedGaloisSCJComputation<GNode<Element>>(badNodes, Priority.first(ChunkedFIFO.class).thenLocally(LIFO.class));
		
		for(int i = 0; i < computation.getNumTasks(); i++) {
			GaloisSCJProcess<GNode<Element>> process = new GaloisSCJProcess<GNode<Element>>(computation, i){

				@Override
				protected void body(GNode<Element> item,
						ForeachContext<GNode<Element>> ctx) {
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
				
			};
			
			Task<Void> procTask = new Task<Void>();
			process.scjTask_process(procTask);
		}		
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
		new SCJMain().run(args);
	}
}
