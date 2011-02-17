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

package benchmarks.lonestar.delaunay_triangulation;

import util.Launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import objects.graph.IndexedGraph;
import objects.graph.Node;

public abstract class AbstractMain {
	protected abstract String getVersion();

	protected abstract void triangulate(IndexedGraph<Element> mesh, Node<Element> largeNode) throws ExecutionException;

	protected abstract IndexedGraph<Element> createGraph();

	@SuppressWarnings("null")
	public void run(String args[]) throws ExecutionException, IOException {

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
			System.err.println("input file:  " + input_file);
			System.err.println("configuration: " + tuples.size() + " points");
		}

		IndexedGraph<Element> mesh = triangulate(tuples, DataManager.t1, DataManager.t2, DataManager.t3);

		// Generate the mesh (sorted) on the standard output
		// DataManager.outputSortedResult(mesh, 6 ) ;
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("mesh size: " + mesh.getNumNodes() + " triangles");
			verify(mesh);
		}
		System.err.println();
	}

	public IndexedGraph<Element> triangulate(Collection<Tuple> tuples, Tuple t1, Tuple t2, Tuple t3)
	throws ExecutionException {
		/* The final mesh (containing the triangulation) */
		IndexedGraph<Element> mesh = createGraph();

		// BEGIN --- Create the main initial triangle
		// Create the main triangle and add it to the mesh (plus the 3 segments)
		Element large_triangle = new Element(t1, t2, t3);
		Node<Element> large_node = mesh.createNode(large_triangle);
		mesh.addNode(large_node);
		// Add the border (3 segments) of the mesh
		Node<Element> border_node1 = mesh.createNode(new Element(t1, t2));
		Node<Element> border_node2 = mesh.createNode(new Element(t2, t3));
		Node<Element> border_node3 = mesh.createNode(new Element(t3, t1));
		mesh.addNode(border_node1);
		mesh.addNode(border_node2);
		mesh.addNode(border_node3);

		mesh.setNeighbor(large_node, 0, border_node1);
		mesh.setNeighbor(large_node, 1, border_node2);
		mesh.setNeighbor(large_node, 2, border_node3);
		mesh.setNeighbor(border_node1, 0, large_node);
		mesh.setNeighbor(border_node2, 0, large_node);
		mesh.setNeighbor(border_node3, 0, large_node);

		// END --- Create the main initial triangle

		large_triangle.tuples = new ArrayList<Tuple>();
		for (Tuple tuple : tuples) {
			large_triangle.tuples.add(tuple);
		}
		triangulate(mesh, large_node);

		return mesh;
	}

	@SuppressWarnings("unchecked")
	public static void verify(Object res) {
		IndexedGraph<Element> result = (IndexedGraph<Element>) res;
		if (!Verification.verify(result)) {
			throw new IllegalStateException("triangulation failed.");
		}
		System.out.println("triangulation okay");
		System.out.println();
	}
}
