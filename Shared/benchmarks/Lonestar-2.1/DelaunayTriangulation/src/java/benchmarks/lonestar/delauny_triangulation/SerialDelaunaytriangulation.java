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

File: SerialDelaunaytriangulation.java 
 */


import objects.graph.ArrayIndexedGraph;
import objects.graph.IndexedGraph;
import objects.graph.Node;
import util.Time;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;


public class SerialDelaunaytriangulation {

  private static boolean isFirstRun = true;


  public static void main(String[] args) {
    long runtime, lasttime, mintime, run;

    runtime = 0;
    lasttime = Long.MAX_VALUE;
    mintime = Long.MAX_VALUE;
    run = 0;
    while (((run < 3) || (Math.abs(lasttime - runtime) * 64 > Math.min(lasttime, runtime))) && (run < 7)) {
      System.gc();
      System.gc();
      System.gc();
      System.gc();
      System.gc();
      runtime = run(args);
      if (runtime < mintime)
        mintime = runtime;
      run++;
    }
    System.err.println("minimum runtime: " + mintime + " ms");
    System.err.println("");
  }


  @SuppressWarnings("null")
public static long run(String args[]) {

    if (isFirstRun) {
      System.err.println();
      System.err.println("Lonestar Benchmark Suite v2.1");
      System.err.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
      System.err.println("http://iss.ices.utexas.edu/lonestar/");
      System.err.println();
      System.err.println("application: Delaunay Triangulation (serial version)");
      System.err.println("Produces a Delaunay triangulation from a given a set of points");
      System.err.println("http://iss.ices.utexas.edu/lonestar/delaunaytriangulation.html");
      System.err.println();
    }

    String input_file;
    /**
     * Arg1: Input file containing the set of initial points
     */
    if (args.length < 1) {
      System.err.println("usage: input_file [verify]");
      System.exit(1);
    }
    // Retrieve the point filename from the input parameters
    input_file = args[0];

    // Read the set of initial points for the file 'input_file'
    Collection<Tuple> tuples = null;

    try {
      tuples = DataManager.readTuplesFromFile(input_file);
    } catch (FileNotFoundException e) {
      System.err.println(e);
    }

    if (isFirstRun) {
      System.err.println("Delaunay Triangulation configuration: " + tuples.size() + " points, file " + input_file);
      System.err.println();
    }
    /* The final mesh (containing the triangulation) */
    IndexedGraph<Element> mesh = new ArrayIndexedGraph<Element>(3);

    // BEGIN --- Create the main initial triangle
    // Create the main triangle and add it to the mesh (plus the 3 segments)
    Element large_triangle = new Element(DataManager.t1, DataManager.t2, DataManager.t3);
    Node<Element> large_node = mesh.createNode(large_triangle);
    large_triangle.graphNode = large_node;
    mesh.addNode(large_node);
    // Add the border (3 segments) of the mesh
    Node<Element> border_node1 = mesh.createNode(new Element(DataManager.t1, DataManager.t2));
    Node<Element> border_node2 = mesh.createNode(new Element(DataManager.t2, DataManager.t3));
    Node<Element> border_node3 = mesh.createNode(new Element(DataManager.t3, DataManager.t1));
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

    /* The History DAG used to locate a point inside the mesh */
    HistoryDAG dag = new HistoryDAG(large_triangle);

    /* The worklist containing every point to insert in the mesh */
    Stack<Tuple> worklist = new Stack<Tuple>();
    for (Tuple tuple : tuples) {
      worklist.add(tuple);
    }

    Triangulator triangulator = new Triangulator();
    triangulator.graph = mesh;
    triangulator.dag = dag;

    long id = Time.getNewTimeId();
    int size = Math.min(300, Math.max(tuples.size() / 1000, 50));
    ArrayList<Node<Element>> edge_list = new ArrayList<Node<Element>>(size);
    ArrayList<Tuple> edgeEndPoints = new ArrayList<Tuple>(size);
    ArrayList<Tuple> otherPoints = new ArrayList<Tuple>(size);

    for (Tuple tuple : worklist) {
      // split the triangle that the tuple falls in into three triangles
      triangulator.initialize(tuple, edge_list, edgeEndPoints, otherPoints);
      // Check list of edges and flip if necessary
      triangulator.process_edges(tuple, edge_list, edgeEndPoints, otherPoints);
    }

    long time = Time.elapsedTime(id);
    System.err.println("runtime: " + time + " ms");

    if (isFirstRun && (args.length > 1)) {
      verify(mesh);
    }

    isFirstRun = false;
    return time;
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
