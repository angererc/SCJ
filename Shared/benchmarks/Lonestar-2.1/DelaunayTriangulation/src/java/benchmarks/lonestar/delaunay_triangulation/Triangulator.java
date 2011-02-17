package benchmarks.lonestar.delaunay_triangulation;
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

File: Triangulator.java 
*/

import java.util.ArrayList;

import objects.graph.IndexedGraph;
import objects.graph.Node;


public class Triangulator {

  private final static int[] mod3 = { 0, 1, 2, 0, 1 };
  HistoryDAG dag;
  IndexedGraph<Element> graph;


  /**
   * Initialize the insertion of a point 't' inside the mesh 'm'. This function
   * only split the corresponding triangle in 3, adding good links to the mesh.
   */
  @SuppressWarnings("null")
public void initialize(Tuple t, ArrayList<Node<Element>> edge_list, ArrayList<Tuple> edgeEndPoints, ArrayList<Tuple> otherPoints) {

    Node<Element> curr_node = dag.find(t);

    if (curr_node == null) {
      System.out.println("Problem with the DAG: can not find surrounding triangle!!");
      System.exit(-1);
    }

    Element curr = curr_node.getData();
    // Every new triangle (from 't')
    @SuppressWarnings("unchecked")
    Node<Element> new_node[] = new Node[3];

    // Iterate on edge of the surrounding triangle
    for (int i = 0; i < 3; i++) {

      Node<Element> neighbor_node = graph.getNeighbor(curr_node, i);
      Element neighbor = graph.getNodeData(neighbor_node);

      int pos = mod3[i + 1];
      Element new_triangle = new Element(curr.coords[i], curr.coords[pos], t);
      new_node[i] = graph.createNode(new_triangle);
      new_triangle.graphNode = new_node[i];
      graph.addNode(new_node[i]);

      graph.setNeighbor(new_node[i], 0, neighbor_node);
      // Create the new relation of the edge
      int index = 2;
      if (graph.getNeighbor(neighbor_node, 1) == curr_node)
        index = 1;
      if (graph.getNeighbor(neighbor_node, 0) == curr_node)
        index = 0;

      graph.setNeighbor(neighbor_node, index, new_node[i]);
      if (neighbor.dim == 3) {
        edge_list.add(new_node[i]);
        edge_list.add(neighbor_node);
        edgeEndPoints.add(curr.coords[i]);
        edgeEndPoints.add(curr.coords[pos]);
        otherPoints.add(t);
        otherPoints.add(neighbor.coords[mod3[index + 2]]);
      }
    }
    // Add the relations of the newly created triangles
    graph.setNeighbor(new_node[0], 1, new_node[1]);
    graph.setNeighbor(new_node[0], 2, new_node[2]);
    graph.setNeighbor(new_node[1], 2, new_node[0]);
    graph.setNeighbor(new_node[1], 1, new_node[2]);
    graph.setNeighbor(new_node[2], 1, new_node[0]);
    graph.setNeighbor(new_node[2], 2, new_node[1]);

    // Add the new triangles inside the DAG
    dag.setThreeChildren(curr_node.getData(), new_node[0].getData(), new_node[1].getData(), new_node[2].getData());
    graph.removeNode(curr_node);
  }


  public void process_edges(Tuple added_point, ArrayList<Node<Element>> edge_list, ArrayList<Tuple> edgeEndPoints, ArrayList<Tuple> otherPoints) {

    while (!edge_list.isEmpty()) {
      // Get the 2 triangles related to the current edge
      int tos = edge_list.size() - 1;
      Node<Element> n1 = edge_list.remove(tos);
      Node<Element> n2 = edge_list.remove(tos - 1);

      // Retrieve the two points of the tested edge
      Tuple ed_0 = edgeEndPoints.remove(tos);
      Tuple ed_1 = edgeEndPoints.remove(tos - 1);

      Tuple t1 = otherPoints.remove(tos);
      Tuple t2 = otherPoints.remove(tos - 1);

      Element e1 = graph.getNodeData(n1);
      Element e2 = graph.getNodeData(n2);

      // Check the delaunay property
      // (Notice only one test has to be done)
      if (e1.inCircle(t2)) {

        // Create the two new triangles
        Element new_e1 = new Element(t1, t2, ed_0);
        Element new_e2 = new Element(t1, t2, ed_1);

        Node<Element> new_n1 = graph.createNode(new_e1);
        Node<Element> new_n2 = graph.createNode(new_e2);

        new_e1.graphNode = new_n1;
        new_e2.graphNode = new_n2;

        graph.setNeighbor(new_n1, 0, new_n2);
        graph.setNeighbor(new_n2, 0, new_n1);

        // Update the relation of every edges (except [t1,t2])
        for (int i = 0; i < 3; i++) {
          Node<Element> neighbor_node = graph.getNeighbor(n1, i);
          Element neighbor = graph.getNodeData(neighbor_node);

          if (neighbor_node != n2) {
            Node<Element> newNode;
            Tuple newEndPoint;

            if (e1.coords[i] == ed_0 || e1.coords[mod3[i + 1]] == ed_0) {
              newNode = new_n1;
              newEndPoint = ed_0;
            } else {
              newNode = new_n2;
              newEndPoint = ed_1;
            }
            graph.setNeighbor(newNode, 2, neighbor_node);
            int index = 2;
            if (graph.getNeighbor(neighbor_node, 1) == n1)
              index = 1;
            if (graph.getNeighbor(neighbor_node, 0) == n1)
              index = 0;
            graph.setNeighbor(neighbor_node, index, newNode);

            if (neighbor.dim == 3 && e1.coords[0] != added_point && e1.coords[1] != added_point && e1.coords[2] != added_point) {
              // Add this new relation to the list
              edge_list.add(newNode);
              edge_list.add(neighbor_node);
              edgeEndPoints.add(newEndPoint);
              edgeEndPoints.add(t1);
              otherPoints.add(t2);
              otherPoints.add(neighbor.coords[mod3[index + 2]]);
            }
          }
        }

        for (int i = 0; i < 3; i++) {
          Node<Element> neighbor_node = graph.getNeighbor(n2, i);
          Element neighbor = graph.getNodeData(neighbor_node);
          if (neighbor_node != n1) {
            Node<Element> newNode;
            Tuple newEndPoint;

            if (e2.coords[i] == ed_0 || e2.coords[mod3[i + 1]] == ed_0) {
              newNode = new_n1;
              newEndPoint = ed_0;
            } else {
              newNode = new_n2;
              newEndPoint = ed_1;
            }
            graph.setNeighbor(newNode, 1, neighbor_node);

            int index = 2;
            if (graph.getNeighbor(neighbor_node, 1) == n2)
              index = 1;
            if (graph.getNeighbor(neighbor_node, 0) == n2)
              index = 0;
            graph.setNeighbor(neighbor_node, index, newNode);

            if (neighbor.dim == 3 && e2.coords[0] != added_point && e2.coords[1] != added_point && e2.coords[2] != added_point) {
              // Add this new relation to the list
              edge_list.add(newNode);
              edge_list.add(neighbor_node);
              edgeEndPoints.add(newEndPoint);
              edgeEndPoints.add(t2);
              otherPoints.add(t1);
              otherPoints.add(neighbor.coords[mod3[index + 2]]);
            }
          }
        }
        // Update DAG information
        // Children of old triangles (n1 and n2) are new nodes
        // formed by new_n1 and new_n2
        dag.setBothTwoChildren(n1.getData(), n2.getData(), new_n1.getData(), new_n2.getData());
        graph.removeNode(n1);
        graph.removeNode(n2);
        graph.addNode(new_n1);
        graph.addNode(new_n2);
      }
    }
  }
}
