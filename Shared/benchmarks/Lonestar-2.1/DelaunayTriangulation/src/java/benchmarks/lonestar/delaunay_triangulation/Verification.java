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

File: Verification.java 
*/

import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;
import objects.graph.IndexedGraph;
import objects.graph.Node;


public class Verification {

  public static boolean verify(IndexedGraph<Element> mesh) {
    return isConsistent(mesh) && isReachable(mesh) && isDelaunay(mesh);
  }


  public static boolean isConsistent(IndexedGraph<Element> mesh) {

    Iterator<Node<Element>> iter = mesh.iterator();
    while (iter.hasNext()) {
      Node<Element> n = iter.next();
      Element element = mesh.getNodeData(n);
      if (element.dim == 2) {
        if ((mesh.getNeighbor(n, 0) != null && mesh.getNeighbor(n, 1) != null) || (mesh.getNeighbor(n, 0) == null && mesh.getNeighbor(n, 1) == null)) {
          System.out.println("-> Segment " + element + " has " + 2 + " relation(s)");
          return false;
        }
      } else if (element.dim == 3) {
        if (mesh.getNeighbor(n, 0) == null || mesh.getNeighbor(n, 1) == null || mesh.getNeighbor(n, 2) == null) {
          System.out.println("-> Triangle " + element + " doesn't have 3 relations");
          return false;
        }
      } else {
        System.out.println("-> Figures with " + element.dim + " edges");
        return false;
      }
    }
    return true;
  }


  public static boolean isReachable(IndexedGraph<Element> mesh) {
    Node<Element> start = mesh.getRandom();
    Stack<Node<Element>> remaining = new Stack<Node<Element>>();
    HashSet<Node<Element>> found = new HashSet<Node<Element>>();
    remaining.push(start);
    while (!remaining.isEmpty()) {
      Node<Element> node = remaining.pop();
      if (!found.contains(node)) {
        found.add(node);
        for (int i = 0; i < 3; i++) {
          if (mesh.getNeighbor(node, i) != null)
            remaining.push(mesh.getNeighbor(node, i));
        }
      }
    }
    if (found.size() != mesh.getNumNodes()) {
      System.out.println("Not all elements are reachable");
      return false;
    }
    return true;
  }


  public static boolean isDelaunay(IndexedGraph<Element> mesh) {

    Iterator<Node<Element>> iter = mesh.iterator();
    while (iter.hasNext()) {
      Node<Element> n = iter.next();
      Element e = mesh.getNodeData(n);
      if (e.dim == 3) {
        for (int i = 0; i < 3; i++) {
          // Get the 2 triangles related to the current edge
          Node<Element> n2 = mesh.getNeighbor(n, i);
          Element e2 = mesh.getNodeData(n2);

          // To check the delaunay property, both elements must be triangles
          if (e.dim == 3 && e2.dim == 3) {
            Tuple t2 = getTupleT2OfRelatedEdge(e, e2);
            ;
            if (t2 == null)
              return false;
            if (e.inCircle(t2)) {
              return false;
            }
          }
        }

      }
    }
    return true;
  }


  public static Tuple getTupleT2OfRelatedEdge(Element e1, Element e2) {
    // Scans all the edges of the two elements and if it finds one that is equal
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
