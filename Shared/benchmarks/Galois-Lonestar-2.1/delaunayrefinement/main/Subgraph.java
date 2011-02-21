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

File: Subgraph.java 

*/



package delaunayrefinement.main;

import galois.objects.graph.GNode;
import galois.objects.graph.ObjectUndirectedEdge;

import java.util.ArrayList;
import java.util.List;

/**
 *  A sub-graph of the mesh. Used to store information about the original 
 *  and updated cavity  
 */
public class Subgraph {
  // the nodes in the graph before updating
  private final List<GNode<Element>> nodes;
  // the edges that connect the subgraph to the rest of the graph
  private final List<ObjectUndirectedEdge<Element, Element.Edge>> edges;

  public Subgraph() {
    nodes = new ArrayList<GNode<Element>>();
    edges = new ArrayList<ObjectUndirectedEdge<Element, Element.Edge>>();
  }

  public boolean containsNode(GNode<Element> n) {
    return nodes.contains(n);
  }

  public boolean addNode(GNode<Element> n) {
    return nodes.add(n);
  }

  public boolean addEdge(ObjectUndirectedEdge<Element, Element.Edge> e) {
    return edges.add(e);
  }

  public List<GNode<Element>> getNodes() {
    return nodes;
  }

  public List<ObjectUndirectedEdge<Element, Element.Edge>> getEdges() {
    return edges;
  }

  public void reset() {
    nodes.clear();
    edges.clear();
  }
}
