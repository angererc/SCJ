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

File: DataManager.java 

*/



package delaunaytriangulation.main;

import galois.objects.graph.GNode;
import galois.objects.graph.Graph;
import util.fn.LambdaVoid;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class DataManager {

  public static Tuple t1;
  public static Tuple t2;
  public static Tuple t3;

  public static double MIN_X = Double.MAX_VALUE;
  public static double MAX_X = Double.MIN_VALUE;
  public static double MIN_Y = Double.MAX_VALUE;
  public static double MAX_Y = Double.MIN_VALUE;

  /**
   * @param filename the name of the file containing the tuples to be inserted into the mesh
   * @return the collections of tuples
   * @throws IOException
   */
  public static Collection<Tuple> readTuplesFromFile(String filename) throws IOException {
    Scanner s = new Scanner(new GZIPInputStream(new FileInputStream(filename)));
    s.useLocale(Locale.US);
    List<Tuple> tuples = new ArrayList<Tuple>();
    // Number of tuples already processed
    int nbTuplesRead = 0;
    boolean creates = false;
    double x = 0.0;
    while (s.hasNext()) {
      if (s.hasNextDouble()) {
        if (!creates) {
          x = s.nextDouble();
          creates = true;
        } else {
          double y = s.nextDouble();
          // The first 3 tuples are the outermost-triangle bounds
          if (nbTuplesRead < 3) {
            switch (nbTuplesRead) {
            case 0:
              t1 = new Tuple(x, y);
              break;
            case 1:
              t2 = new Tuple(x, y);
              break;
            case 2:
              t3 = new Tuple(x, y);
              break;
            }
            nbTuplesRead++;
          } else {
            if (x < MIN_X) {
              MIN_X = x;
            } else if (x > MAX_X) {
              MAX_X = x;
            }
            if (y < MIN_Y) {
              MIN_Y = y;
            } else if (y > MAX_Y) {
              MAX_Y = y;
            }
            tuples.add(new Tuple(x, y));
          }
          creates = false;
        }
      } else {
        System.err.println("Error: file " + filename + " contains non-double parts");
        return null;
      }
    }
    s.close();
    if (creates) {
      System.err.println("Error: file " + filename + " finished with an x coordinate");
      return null;
    }

    return tuples;
  }
  
  /**
   * a method for formating a double according to the given precision
   * @param d the double number to be formatted 
   * @param precision the precision
   * @return
   */
  public static String formatDouble(double d, int precision) {

    String s = "";
    double d0;
    long decimals[] = new long[precision + 1];
    double abs_d = d;

    decimals[0] = (long) d;

    if (d < 0) {
      abs_d = -d;
    }
    d0 = abs_d - (long) abs_d;

    for (int i = 1; i < precision + 1; i++) {
      decimals[i] = (int) (d0 * 10);
      d0 = d0 * 10 - (int) (d0 * 10);
    }
    // Print
    s += decimals[0] + ".";

    for (int i = 1; i < precision + 1; i++) {
      s += decimals[i];
    }
    return s;
  }

  /**
   * output triangles coordinates(sorted) to the standard output
   * @param el: all the triangles in the mesh
   * @param precision: the precision of the coordinates
   */
  public static void writeElementArray(Element[] el, int precision) {
    PrintStream eleout = System.out;

    for (int i = 0; i < el.length; i++) {
      Element e = el[i];
      if (e.dim == 3) {
        Tuple[] t = e.getRotatedCoords();
        eleout.println("(" + formatDouble(t[0].x, precision) + " " + formatDouble(t[0].y, precision) + ") ("
            + formatDouble(t[1].x, precision) + " " + formatDouble(t[1].y, precision) + ") ("
            + formatDouble(t[2].x, precision) + " " + formatDouble(t[2].y, precision) + ")");
      }
    }
  }
  
  /**
   * sort the triangles in the mesh and output the mesh to standard output
   * @param mesh the mesh to output
   * @param i the precision for the coordinates 
   */
  public static void outputSortedResult(Graph<Element> mesh, int i) {
    final Element[] el = new Element[mesh.size()];
    mesh.map(new LambdaVoid<GNode<Element>>() {
      int k = 0;

      @Override
      public void call(GNode<Element> node) {
        el[k++] = node.getData();
      }
    });
    Arrays.sort(el);
    writeElementArray(el, i);
  }
}
