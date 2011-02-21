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

File: Cavity.java 

*/



package delaunayrefinement.main;

import static galois.objects.MethodFlag.CHECK_CONFLICT;
import static galois.objects.MethodFlag.NONE;
import galois.objects.graph.GNode;
import galois.objects.graph.ObjectGraph;
import galois.objects.graph.ObjectUndirectedEdge;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import util.MutableReference;
import util.fn.Lambda2Void;
import util.fn.LambdaVoid;

public class Cavity {
  protected Tuple center;
  protected GNode<Element> centerNode;
  protected Element centerElement;
  protected int dim;
  protected final Queue<GNode<Element>> frontier;
  // the cavity itself
  protected final Subgraph pre;
  // what the new elements should look like
  protected final Subgraph post;
  // the edge-relations that connect the boundary to the cavity
  protected final Set<ObjectUndirectedEdge<Element, Element.Edge>> connections;
  private final ObjectGraph<Element, Element.Edge> graph;

  public Cavity(ObjectGraph<Element, Element.Edge> mesh) {
    graph = mesh;
    center = null;
    frontier = new LinkedList<GNode<Element>>();
    pre = new Subgraph();
    post = new Subgraph();
    connections = new HashSet<ObjectUndirectedEdge<Element, Element.Edge>>();
  }

  /**
   * 
   * @return the sub-graph corresponding to the changed cavity
   */
  public Subgraph getPre() {
    return pre;
  }

  /**
   * 
   * @return the sub-graph corresponding to the newly computed cavity
   */
  public Subgraph getPost() {
    return post;
  }

  /**
   * Initializes the cavity building process by determining the center of the cavity
   * The computed center is inside a non-obtuse triangle
   *  
   * @param node the potential center of the new cavity
   */
  public void initialize(GNode<Element> node) {
    pre.reset();
    post.reset();
    connections.clear();
    frontier.clear();
    centerNode = node;
    centerElement = centerNode.getData(NONE);
    while (graph.contains(centerNode, NONE) && centerElement.isObtuse()) {
      centerNode = getOpposite(centerNode);
      centerElement = centerNode.getData(NONE);
    }
    center = centerElement.getCenter();
    dim = centerElement.getDim();
    pre.addNode(centerNode);
    frontier.add(centerNode);
  }

  /**
   * find the node that is opposite the obtuse angle of the element
   */
  private GNode<Element> getOpposite(final GNode<Element> node) {
    int numOutNeighbors = graph.outNeighborsSize(node, CHECK_CONFLICT);
    if (numOutNeighbors != 3) {
      throw new Error(String.format("neighbors %d for node = %s", numOutNeighbors, node.getData()));
    }

    final MutableReference<GNode<Element>> dst = new MutableReference<GNode<Element>>();

    node.map(new LambdaVoid<GNode<Element>>() {
      @Override
      public void call(GNode<Element> neighbor) {
        Element element = node.getData(NONE);
        Element.Edge edgeData = graph.getEdgeData(node, neighbor, NONE);
        Tuple elementTuple = element.getObtuse();
        if (elementTuple.notEquals(edgeData.getPoint(0)) && elementTuple.notEquals(edgeData.getPoint(1))) {
          assert dst.get() == null;
          dst.set(neighbor);
        }
      }
    });

    if (dst.get() != null) {
      assert node != dst.get();
      return dst.get();
    }
    throw new Error("edge");
  }

  /**
   * Inner loop of cavity expansion.
   * For each neighbor of a node in the cavity, check if its circumcircle contains the center of the cavity.
   * If so, add it to the cavity, and consider it for further expansion of the cavity. Otherwise, mark the 
   * boundary of the cavity.
   * 
   */
  private Lambda2Void<GNode<Element>, GNode<Element>> expand = new Lambda2Void<GNode<Element>, GNode<Element>>() {
    @Override
    public void call(GNode<Element> next, GNode<Element> node) {
      Element nextElement = next.getData(NONE);
      if ((!(dim == 2 && nextElement.getDim() == 2 && next != centerNode)) && nextElement.inCircle(center)) {
        // isMember says next is part of the cavity, and we're not the second
        // segment encroaching on this cavity
        if ((nextElement.getDim() == 2) && (dim != 2)) {
          // is segment, and we are encroaching
          initialize(next);
          build();
        } else {
          if (!pre.containsNode(next)) {
            pre.addNode(next);
            frontier.add(next);
          }
        }
      } else {
        // not a member
        Element.Edge edgeData = graph.getEdgeData(node, next, NONE);
        ObjectUndirectedEdge<Element, Element.Edge> edge = new ObjectUndirectedEdge<Element, Element.Edge>(node, next,
            edgeData);
        if (!connections.contains(edge)) {
          connections.add(edge);
        }
      }
    }
  };

  /**
   * Expand the cavity 
   */
  public void build() {
    while (frontier.size() != 0) {
      final GNode<Element> curr = frontier.poll();
      curr.map(expand, curr, CHECK_CONFLICT);
    }
  }

  /**
   * Create the new cavity based on the data of the old one
   */
  public void update() {
    if (centerElement.getDim() == 2) { // we built around a segment
      Element ele1 = new Element(center, centerElement.getPoint(0));
      GNode<Element> node1 = graph.createNode(ele1);
      post.addNode(node1);
      Element ele2 = new Element(center, centerElement.getPoint(1));
      GNode<Element> node2 = graph.createNode(ele2);
      post.addNode(node2);
    }
    for (ObjectUndirectedEdge<Element, Element.Edge> conn : connections) {
      Element.Edge edge = conn.getData();
      Element new_element = new Element(center, edge.getPoint(0), edge.getPoint(1));
      GNode<Element> ne_node = graph.createNode(new_element);
      GNode<Element> ne_connection;
      if (pre.containsNode(conn.getDst())) {
        ne_connection = conn.getSrc();
      } else {
        ne_connection = conn.getDst();
      }
      Element ne_nodeData = ne_connection.getData(NONE);
      Element.Edge new_edge = new_element.getRelatedEdge(ne_nodeData);
      boolean mod = post.addEdge(new ObjectUndirectedEdge<Element, Element.Edge>(ne_node, ne_connection, new_edge));
      assert mod;
      List<GNode<Element>> postnodes = post.getNodes();
      for (int i = 0; i < postnodes.size(); i++) {
        GNode<Element> node = postnodes.get(i);
        Element element = node.getData(NONE);
        if (element.isRelated(new_element)) {
          Element.Edge ele_edge = new_element.getRelatedEdge(element);
          mod = post.addEdge(new ObjectUndirectedEdge<Element, Element.Edge>(ne_node, node, ele_edge));
          assert mod;
        }
      }
      post.addNode(ne_node);
    }
  }
}
