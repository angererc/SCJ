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

File: Mesh.java 

*/



package delaunayrefinement.main;

import galois.objects.graph.GNode;
import galois.objects.graph.ObjectGraph;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Stack;
import java.util.zip.GZIPInputStream;

import util.MutableBoolean;
import util.MutableReference;
import util.fn.LambdaVoid;

/**
 * Helper class used providing methods to read in information and create the graph 
 *
 */
public class Mesh {
  // used during reading of the data and creation of the graph, to record edges between nodes of the graph 
  protected static final HashMap<Element.Edge, GNode<Element>> edge_map = new HashMap<Element.Edge, GNode<Element>>();

  /**
   * 
   * @param mesh The graph representing the mesh
   * @return the bad triangles in the graph
   */
  public static Collection<GNode<Element>> getBad(ObjectGraph<Element, Element.Edge> mesh) {
    final Collection<GNode<Element>> ret = new HashSet<GNode<Element>>();

    mesh.map(new LambdaVoid<GNode<Element>>() {
      @Override
      public void call(GNode<Element> node) {
        Element element = node.getData();
        if (element.isBad()) {
          ret.add(node);
        }
      }
    });

    return ret;
  }

  private static Scanner getScanner(String filename) throws Exception {
    try {
      return new Scanner(new GZIPInputStream(new FileInputStream(filename + ".gz")));
    } catch (FileNotFoundException _) {
      return new Scanner(new FileInputStream(filename));
    }
  }

  private Tuple[] readNodes(String filename) throws Exception {
    Scanner scanner = getScanner(filename + ".node");

    int ntups = scanner.nextInt();
    scanner.nextInt();
    scanner.nextInt();
    scanner.nextInt();

    Tuple[] tuples = new Tuple[ntups];
    for (int i = 0; i < ntups; i++) {
      int index = scanner.nextInt();
      double x = scanner.nextDouble();
      double y = scanner.nextDouble();
      scanner.nextDouble(); // z
      tuples[index] = new Tuple(x, y, 0);
    }

    return tuples;
  }

  private void readElements(ObjectGraph<Element, Element.Edge> mesh, String filename, Tuple[] tuples) throws Exception {
    Scanner scanner = getScanner(filename + ".ele");

    int nels = scanner.nextInt();
    scanner.nextInt();
    scanner.nextInt();
    Element[] elements = new Element[nels];
    for (int i = 0; i < nels; i++) {
      int index = scanner.nextInt();
      int n1 = scanner.nextInt();
      int n2 = scanner.nextInt();
      int n3 = scanner.nextInt();
      elements[index] = new Element(tuples[n1], tuples[n2], tuples[n3]);
      addElement(mesh, elements[index]);
    }
  }

  private void readPoly(ObjectGraph<Element, Element.Edge> mesh, String filename, Tuple[] tuples) throws Exception {
    Scanner scanner = getScanner(filename + ".poly");

    scanner.nextInt();
    scanner.nextInt();
    scanner.nextInt();
    scanner.nextInt();
    int nsegs = scanner.nextInt();
    scanner.nextInt();
    Element[] segments = new Element[nsegs];
    for (int i = 0; i < nsegs; i++) {
      int index = scanner.nextInt();
      int n1 = scanner.nextInt();
      int n2 = scanner.nextInt();
      scanner.nextInt();
      segments[index] = new Element(tuples[n1], tuples[n2]);
      addElement(mesh, segments[index]);
    }
  }

  // .poly contains the perimeter of the mesh; edges basically, which is why it contains pairs of nodes
  public void read(ObjectGraph<Element, Element.Edge> mesh, String basename) throws Exception {
    Tuple[] tuples = readNodes(basename);
    readElements(mesh, basename, tuples);
    readPoly(mesh, basename, tuples);
  }

  protected GNode<Element> addElement(ObjectGraph<Element, Element.Edge> mesh, Element element) {
    GNode<Element> node = mesh.createNode(element);
    mesh.add(node);
    for (int i = 0; i < element.numEdges(); i++) {
      Element.Edge edge = element.getEdge(i);
      if (!edge_map.containsKey(edge)) {
        edge_map.put(edge, node);
      } else {
        mesh.addEdge(node, edge_map.get(edge), edge);
        edge_map.remove(edge);
      }
    }
    return node;
  }

  public static boolean verify(final ObjectGraph<Element, Element.Edge> mesh) {
    // ensure consistency of elements
    final MutableBoolean error = new MutableBoolean();
    final MutableReference<GNode<Element>> someNode = new MutableReference<GNode<Element>>();

    mesh.map(new LambdaVoid<GNode<Element>>() {
      @Override
      public void call(GNode<Element> node) {

        if (someNode.get() == null) {
          someNode.set(node);
        }

        Element element = node.getData();
        if (element.getDim() == 2) {
          if (mesh.outNeighborsSize(node) != 1) {
            System.out.println("-> Segment " + element + " has " + mesh.outNeighborsSize(node) + " relation(s)");
            error.set(true);
          }
        } else if (element.getDim() == 3) {
          if (mesh.outNeighborsSize(node) != 3) {
            System.out.println("-> Triangle " + element + " has " + mesh.outNeighborsSize(node) + " relation(s)");
            error.set(true);
          }
        } else {
          System.out.println("-> Figures with " + element.getDim() + " edges");
          error.set(true);
        }
      }
    });

    if (error.get()) {
      return false;
    }

    // ensure reachability
    final Stack<GNode<Element>> remaining = new Stack<GNode<Element>>();
    HashSet<GNode<Element>> found = new HashSet<GNode<Element>>();
    remaining.push(someNode.get());

    final LambdaVoid<GNode<Element>> body = new LambdaVoid<GNode<Element>>() {
      @Override
      public void call(GNode<Element> node) {
        remaining.add(node);
      }
    };

    while (!remaining.isEmpty()) {
      GNode<Element> node = remaining.pop();
      if (!found.contains(node)) {
        found.add(node);
        node.map(body);
      }
    }
    if (found.size() != mesh.size()) {
      System.out.println("Not all elements are reachable");
      return false;
    }
    return true;
  }
}
