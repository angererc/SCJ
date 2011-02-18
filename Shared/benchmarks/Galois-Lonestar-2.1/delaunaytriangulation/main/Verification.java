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

File: Verification.java 

*/



package delaunaytriangulation.main;

import galois.objects.graph.GNode;
import galois.objects.graph.Graph;
import galois.objects.graph.Graphs;
import galois.objects.graph.LongGraph;
import util.fn.Lambda2Void;

import java.util.HashSet;
import java.util.Stack;

public class Verification {
	/**
	 * check if in the mesh, every triangle has three outgoing neighbors
	 * and every segment has one outgoing neighbor 
	 */
	public static void checkConsistency(LongGraph<Element> mesh) {
		mesh.map(new Lambda2Void<GNode<Element>, Graph<Element>>() {
			@Override
			public void call(GNode<Element> n, Graph<Element> graph) {
				Element element = n.getData();
				if (element.dim == 2) {
					if (graph.outNeighborsSize(n) == 2 || graph.outNeighborsSize(n) == 0) {
						throw new IllegalStateException("-> Segment " + element + " has " + 2 + " relation(s)");
					}
				} else if (element.dim == 3) {
					if (graph.outNeighborsSize(n) != 3) {
						throw new IllegalStateException("-> Triangle " + element + " doesn't have 3 relations");
					}
				} else {
					throw new IllegalStateException("-> Figures with " + element.dim + " edges");
				}
			}
		}, mesh);
	}

	/**
	 * check if the mesh is a connected graph
	 */
	public static void checkReachability(LongGraph<Element> mesh) {
		GNode<Element> start = Graphs.getRandom(mesh);
		final Stack<GNode<Element>> remaining = new Stack<GNode<Element>>();
		HashSet<GNode<Element>> found = new HashSet<GNode<Element>>();
		remaining.push(start);
		AddNeighborClosure closure = new AddNeighborClosure();
		while (!remaining.isEmpty()) {
			GNode<Element> node = remaining.pop();
			if (!found.contains(node)) {
				found.add(node);
				node.map(closure, remaining);
			}
		}
		if (found.size() != mesh.size()) {
			throw new IllegalStateException("Not all elements are reachable");
		}
	}

	private static final class AddNeighborClosure implements Lambda2Void<GNode<Element>, Stack<GNode<Element>>> {
		@Override
		public void call(GNode<Element> dst, Stack<GNode<Element>> remaining) {
			remaining.push(dst);
		}
	}

	/**
	 * check if the mesh satisfy the delaunay propery
	 */
	public static void checkDelaunayProperty(LongGraph<Element> mesh) {
		final CheckNeighborDelaunay checkNeigbhbors = new CheckNeighborDelaunay();
		mesh.map(new Lambda2Void<GNode<Element>, LongGraph<Element>>() {
			@Override
			public void call(GNode<Element> n, LongGraph<Element> graph) {
				final Element e = n.getData();
				if (e.dim == 3) {
					n.map(checkNeigbhbors, n);
				}
			}
		}, mesh);
	}

	private static final class CheckNeighborDelaunay implements Lambda2Void<GNode<Element>, GNode<Element>> {
		@Override
		public void call(GNode<Element> dst, GNode<Element> src) {
			Element e = src.getData();
			Element e2 = dst.getData();

			// To check the delaunay property, both elements must be triangles
			if (e.dim == 3 && e2.dim == 3) {
				Tuple t2 = getTupleT2OfRelatedEdge(e, e2);
				if (t2 == null) {
					throw new IllegalStateException();
				}
				if (e.inCircle(t2)) {
					throw new IllegalStateException();
				}
			}
		}
	}

	 /**
   * given two triangles e1 and e2, find the tuple in e2 which is not 
   * the endpoint of the common edge between them
   */
	public static Tuple getTupleT2OfRelatedEdge(Element e1, Element e2) {
		int e2_0 = -1;
		int e2_1 = -1;
		int phase = 0;

		for (int i = 0; i < e1.dim; i++) {
			for (int j = 0; j < e2.dim; j++) {
				if (e1.coords[i].equals(e2.coords[j])) {
					if (phase == 0) {
						e2_0 = j;
						phase = 1;
						break;
					} else {
						e2_1 = j;
						for (int k = 0; k < 3; k++) {
							if (k != e2_0 && k != e2_1) {
								return e2.coords[k];
							}
						}
					}
				}
			}
		}
		return null;
	}
}
