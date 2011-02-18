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



package delaunaytriangulation.main;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.LongGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import util.fn.Lambda3Void;

public class Cavity {
  private LongGraph<Element> graph;
  private HashSet<GNode<Element>> deletingNodes;
  private LinkedList<GNode<Element>> oldNodes;
  private LinkedList<GNode<Element>> connectionNodes;
  private Tuple t;
  private GNode<Element> n;

  /**
   * Constructor
   * @param graph: the graph the cavity is build from
   * @param node: the starting triangle to build the cavity
   */
  public Cavity(LongGraph<Element> graph, GNode<Element> node) {
    this.graph = graph;
    t = node.getData().sample();
    n = node;
    deletingNodes = new LinkedHashSet<GNode<Element>>();
    oldNodes = new LinkedList<GNode<Element>>();
    connectionNodes = new LinkedList<GNode<Element>>();
  }

  /**
   * the method for building the cavity
   */
  public void build() {
    final Queue<GNode<Element>> frontier = new LinkedList<GNode<Element>>();
    frontier.add(n);
    while (!frontier.isEmpty()) {
      GNode<Element> node = frontier.poll();
      node.map(buildClosure, node, frontier, MethodFlag.CHECK_CONFLICT);
    }
  }

  private Lambda3Void<GNode<Element>, GNode<Element>, Queue<GNode<Element>>> buildClosure = new Lambda3Void<GNode<Element>, GNode<Element>, Queue<GNode<Element>>>() {
    @Override
    public void call(GNode<Element> neighbor, GNode<Element> src, Queue<GNode<Element>> frontier) {
      if (neighbor == n || deletingNodes.contains(neighbor)) {
        return;
      }
      Element neighborElement = neighbor.getData(MethodFlag.NONE);
      if (neighborElement.dim == 3 && neighborElement.inCircle(t)) {
        deletingNodes.add(neighbor);
        frontier.add(neighbor);
      } else {
        oldNodes.add(src);
        connectionNodes.add(neighbor);
      }
    }
  };

  /**
   * replace the triangles(elements) in the cavity with a set of new triangles which satify the delaunay property
   * @param ctx: the executor context, not null when running in parallel
   * @return the list of the new created triangles(elments)
   */
  public List<GNode<Element>> update() {
    Element nodeData = n.getData();
    nodeData.tuples.remove(nodeData.tuples.size() - 1);
    ArrayList<GNode<Element>> newNodes = new ArrayList<GNode<Element>>();
    ArrayList<Element> newElements = new ArrayList<Element>();
    while (!connectionNodes.isEmpty()) {
      GNode<Element> neighbor = connectionNodes.removeFirst();
      GNode<Element> oldNode = oldNodes.removeFirst();
      int index = (int) (graph.getEdgeData(neighbor, oldNode, MethodFlag.NONE));
      Element neighborElement = neighbor.getData(MethodFlag.NONE);
      Element e = new Element(t, neighborElement.coords[index], neighborElement.coords[(index + 1) % 3]);
      GNode<Element> node = graph.createNode(e);

      graph.add(node, MethodFlag.NONE);
      graph.addEdge(node, neighbor, 1, MethodFlag.NONE);
      graph.addEdge(neighbor, node, index, MethodFlag.NONE);

      int numNeighborsFound = 0;

      for (GNode<Element> newNode : newNodes) {
        boolean found = false;
        int indexForNewNode = -1;
        int indexForNode = -1;
        Element newNodeData = newNode.getData(MethodFlag.NONE);
        if (newNodeData.coords[1] == e.coords[1]) {
          indexForNewNode = 0;
          indexForNode = 0;
          found = true;
        } else if (newNodeData.coords[1] == e.coords[2]) {
          indexForNewNode = 0;
          indexForNode = 2;
          found = true;
        } else if (newNodeData.coords[2] == e.coords[1]) {
          indexForNewNode = 2;
          indexForNode = 0;
          found = true;
        } else if (newNodeData.coords[2] == e.coords[2]) {
          indexForNewNode = 2;
          indexForNode = 2;
          found = true;
        }

        if (found) {
          graph.addEdge(newNode, node, indexForNewNode, MethodFlag.NONE);
          graph.addEdge(node, newNode, indexForNode, MethodFlag.NONE);
          numNeighborsFound++;
        }
        if (numNeighborsFound == 2) {
          break;
        }
      }

      newNodes.add(node);
      newElements.add(e);

      Element oldNodeData = oldNode.getData(MethodFlag.NONE);
      ArrayList<Tuple> tuples = oldNodeData.tuples;
      if (tuples != null) {
        ArrayList<Tuple> newTuples = new ArrayList<Tuple>();
        for (Tuple tuple : tuples) {
          if (e.elementContains(tuple)) {
            if (e.tuples == null) {
              e.tuples = new ArrayList<Tuple>();
            }
            e.tuples.add(tuple);
          } else {
            newTuples.add(tuple);
          }
        }
        oldNodeData.tuples = newTuples;
        tuples.clear();
      }
    }
    deletingNodes.add(n);
    dispatchTuples(newElements);

    for (GNode<Element> node : deletingNodes) {
      node.getData(MethodFlag.NONE).processed = true;
      graph.remove(node, MethodFlag.NONE);
    }
    return newNodes;
  }

  /**
   * dispatch the tuples in the original triangles in the cavity to the new triangles created by updating cavity
   * @param newElements: the new created triangles by updating cavity
   */
  protected void dispatchTuples(ArrayList<Element> newElements) {
    int size = newElements.size();
    for (GNode<Element> node : deletingNodes) {
      ArrayList<Tuple> tuples = node.getData(MethodFlag.NONE).tuples;
      if (tuples == null) {
        continue;
      }
      int tupleSize = tuples.size() - 1;
      while (tupleSize >= 0) {
        Tuple tuple = tuples.remove(tupleSize--);
        for (int i = 0; i < size; i++) {
          Element newNodeData = newElements.get(i);
          if (newNodeData.elementContains(tuple)) {
            if (newNodeData.tuples == null) {
              newNodeData.tuples = new ArrayList<Tuple>();
            }
            newNodeData.tuples.add(tuple);
            if (i != 0) {
              newElements.set(i, newElements.get(0));
              newElements.set(0, newNodeData);
            }
            break;
          }
        }
      }
    }
  }
}
