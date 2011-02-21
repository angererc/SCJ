package delaunaytriangulation.main;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.LongGraph;
import galois.objects.graph.MorphGraph;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.LIFO;
import galois.runtime.wl.Priority;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.ReducedGaloisRuntime;
import galois_scj.UnorderedGaloisSCJComputation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import scj.Task;
import util.Launcher;

public class SCJMain {

	public void scjMainTask_triangulate(Task<Void> now, final LongGraph<Element> mesh, GNode<Element> largeNode) throws Exception {
		Collection<GNode<Element>> initWL = new ArrayList<GNode<Element>>();
		initWL.add(largeNode);
		Launcher.getLauncher().startTiming();
		
		Task<Void> finish = new Task<Void>();
		this.scjTask_finishTriangulation(finish);
		
		//
		GaloisSCJComputation<GNode<Element>> computation = new UnorderedGaloisSCJComputation<GNode<Element>>(initWL, 2, Priority.first(ChunkedFIFO.class).then(LIFO.class));		
		for(int i = 0; i < computation.getNumTasks(); i++) {
			GaloisSCJProcess<GNode<Element>> process = new GaloisSCJProcess<GNode<Element>>(computation, i) {

				@Override
				protected void body(GNode<Element> currNode, ForeachContext<GNode<Element>> ctx) {					
					//System.out.println("SCJMain: called triangulate computation");
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
				
			};
			
			Task<Void> triangulateItem = new Task<Void>();
			process.scjTask_process(triangulateItem);
			
			triangulateItem.hb(finish);
		}
		
	}

	public void scjTask_finishTriangulation(Task<Void> now) {
		Launcher.getLauncher().stopTiming();
	}


	protected LongGraph<Element> createGraph() {
		final MorphGraph.LongGraphBuilder builder = new MorphGraph.LongGraphBuilder();
		return builder.directed(true).backedByVector(true).create();
	}

	public LongGraph<Element> triangulate(Collection<Tuple> tuples, Tuple t1, Tuple t2, Tuple t3)
	throws Exception {
		/* The final mesh (containing the triangulation) */
		LongGraph<Element> mesh = createGraph();

		// BEGIN --- Create the main initial triangle
		// Create the main triangle and add it to the mesh (plus the 3 segments)
		Element large_triangle = new Element(t1, t2, t3);
		GNode<Element> large_node = mesh.createNode(large_triangle);
		mesh.add(large_node);
		// Add the border (3 segments) of the mesh
		GNode<Element> border_node1 = mesh.createNode(new Element(t1, t2));
		GNode<Element> border_node2 = mesh.createNode(new Element(t2, t3));
		GNode<Element> border_node3 = mesh.createNode(new Element(t3, t1));
		mesh.add(border_node1);
		mesh.add(border_node2);
		mesh.add(border_node3);
		mesh.addEdge(large_node, border_node1, 0);
		mesh.addEdge(large_node, border_node2, 1);
		mesh.addEdge(large_node, border_node3, 2);

		mesh.addEdge(border_node1, large_node, 0);
		mesh.addEdge(border_node2, large_node, 0);
		mesh.addEdge(border_node3, large_node, 0);
		// END --- Create the main initial triangle

		large_triangle.tuples = new ArrayList<Tuple>();
		for (Tuple tuple : tuples) {
			large_triangle.tuples.add(tuple);
		}
		
		this.scjMainTask_triangulate(new Task<Void>(), mesh, large_node);

		return mesh;
	}

	public static void verify(LongGraph<Element> result) {
		System.err.print("verifying... ");
		Verification.checkConsistency(result);
		Verification.checkReachability(result);
		Verification.checkDelaunayProperty(result);
		System.err.println("okay.");
	}

	public void run(String args[]) throws Exception {
		ReducedGaloisRuntime.initialize(false, false);
		//FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("Lonestar Benchmark Suite v3.0");
			System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
			System.err.println("http://iss.ices.utexas.edu/lonestar/");
			System.err.println();
			System.err.printf("Application: BowyerWaston DelaunayTriangulation (%s version)\n", "SCJ version");
			System.err.println("Produces a Delaunay triangulation from a given a set of points");
			System.err.println("http://iss.ices.utexas.edu/lonestar/delaunaytriangulation.html");
			System.err.println();
		}

		// Arg1: Input file containing the set of initial points
		if (args.length < 1) {
			System.err.println("usage: input_file");
			System.exit(1);
		}
		//Retrieve the point filename from the input parameters
		String input_file = args[0];

		// Read the set of initial points for the file 'input_file'
		Collection<Tuple> tuples = null;

		tuples = DataManager.readTuplesFromFile(input_file);
		if (tuples == null) {
			System.exit(-1);
		}
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("number of threads: " + GaloisRuntime.getRuntime().getMaxThreads());
			System.err.println("input file:  " + input_file);
			System.err.println("configuration: " + tuples.size() + " points");
		}

		LongGraph<Element> mesh = triangulate(tuples, DataManager.t1, DataManager.t2, DataManager.t3);

		// Generate the mesh (sorted) on the standard output
		// DataManager.outputSortedResult(mesh, 6 ) ;
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("mesh size: " + mesh.size() + " triangles");
			verify(mesh);
		}
		System.err.println();
	}

	public static void main(String[] args) throws Exception {
		SCJMain main = new SCJMain();
		main.run(args);
	}
}
