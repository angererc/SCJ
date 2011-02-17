package benchmarks.lonestar.delauny_triangulation;
/*
Lonestar Benchmark Suite for irregular applications that exhibit 
amorphous data-parallelism.

Center for Grid and Distributed Computing
The University of Texas at Austin

Copyright (C) 2007, 2008, 2009 The University of Texas at Austin

Licensed under the Eclipse Public License, Version 1.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.eclipse.org/legal/epl-v10.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

File: HistoryDAG.java 
*/

import objects.graph.ArrayIndexedGraph;
import objects.graph.IndexedGraph;
import objects.graph.Node;


public class HistoryDAG {

  IndexedGraph<Element> dag;
  Node<Element> root;


  public HistoryDAG(Element n) {
    // Create the root of the DAG
    dag = new ArrayIndexedGraph<Element>(3);
    root = dag.createNode(n);
    n.dagNode = root;
    dag.addNode(root);
  }


  /**
   * Set two children of two different nodes. Create two nodes from 'child1' and
   * 'child2' and assign them as children of nodes representing elements 'node1'
   * and 'node2'
   */
  void setBothTwoChildren(Element node1, Element node2, Element child1, Element child2) {
    // --------- FIRST NODE -------
    Node<Element> n = node1.dagNode;
    Node<Element> childNode1 = dag.createNode(child1);
    dag.addNode(childNode1);
    dag.setNeighbor(n, 0, childNode1);

    Node<Element> childNode2 = dag.createNode(child2);
    dag.addNode(childNode2);
    dag.setNeighbor(n, 1, childNode2);

    // ---------SECOND NODE -------
    n = node2.dagNode;
    dag.setNeighbor(n, 0, childNode1);
    dag.setNeighbor(n, 1, childNode2);

    child1.dagNode = childNode1;
    child2.dagNode = childNode2;
  }


  /**
   * Set three children of one node. Create three nodes from 'child1', 'child2'
   * qnd 'child3' and assign them as children of the node representing the
   * element 'node'.
   */
  void setThreeChildren(Element node, Element child1, Element child2, Element child3) {
    Node<Element> n = node.dagNode;
    // Create the nodes for the children
    Node<Element> childNode1 = dag.createNode(child1);
    Node<Element> childNode2 = dag.createNode(child2);
    Node<Element> childNode3 = dag.createNode(child3);
    dag.addNode(childNode1);
    dag.addNode(childNode2);
    dag.addNode(childNode3);

    // Assign them as children
    dag.setNeighbor(n, 0, childNode1);
    dag.setNeighbor(n, 1, childNode2);
    dag.setNeighbor(n, 2, childNode3);

    // Update the map element->nodes
    child1.dagNode = childNode1;
    child2.dagNode = childNode2;
    child3.dagNode = childNode3;
  }


  /**
   * Find which node in the DAG contains the tuple 't'. Return that graph node
   * (not the corresponding DAG node)
   */
  Node<Element> find(Tuple t) {
    Node<Element> curr = root;

    // While we do not reach a leaf
    while (dag.getNeighbor(curr, 0) != null) {

      Node<Element> temp = curr;

      // Search among the children which one contains the tuple 't'
      for (int i = 0; i < 3; i++) {
        Node<Element> child = dag.getNeighbor(curr, i);
        if (child == null) {
          break;
        }
        if (child.getData().elementContains(t)) {
          temp = child;
        }
      }
      if (temp == curr) {
        return null;
      }
      curr = temp;
    }

    if (dag.getNeighbor(curr, 0) != null || !curr.getData().elementContains(t)) {
      return null;
    }
    return curr.getData().graphNode;
  }

}
