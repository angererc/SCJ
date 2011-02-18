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

File: AbstractMain.java 

 */

package delaunaytriangulation.main;

import galois.objects.graph.GNode;
import galois.objects.graph.LongGraph;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.ReplayFeature;
import util.Launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

public abstract class AbstractMain {
	protected abstract String getVersion();

	protected abstract void triangulate(LongGraph<Element> mesh, GNode<Element> largeNode) throws ExecutionException;

	protected abstract LongGraph<Element> createGraph();

	public void run(String args[]) throws ExecutionException, IOException {
		FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
				
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("Lonestar Benchmark Suite v3.0");
			System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
			System.err.println("http://iss.ices.utexas.edu/lonestar/");
			System.err.println();
			System.err.printf("Application: BowyerWaston DelaunayTriangulation (%s version)\n", getVersion());
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

	public LongGraph<Element> triangulate(Collection<Tuple> tuples, Tuple t1, Tuple t2, Tuple t3)
	throws ExecutionException {
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
		triangulate(mesh, large_node);

		return mesh;
	}

	public static void verify(LongGraph<Element> result) {
		System.err.print("verifying... ");
		Verification.checkConsistency(result);
		Verification.checkReachability(result);
		Verification.checkDelaunayProperty(result);
		System.err.println("okay.");
	}
}
