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

File: DataManager.java 
*/

import objects.graph.IndexedGraph;
import objects.graph.Node;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;


public class DataManager {

  public static Tuple t1;
  public static Tuple t2;
  public static Tuple t3;


  public static Collection<Tuple> readTuplesFromFile(String filename) throws FileNotFoundException {
    Scanner s = new Scanner(new BufferedReader(new FileReader(filename)));
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


  public static String formatDouble(double d, int precision) {

    String s = "";
    double d0;
    long decimals[] = new long[precision + 1];
    double abs_d = d;

    decimals[0] = (long) d;

    if (d < 0)
      abs_d = -d;
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


  public static void writeElementArray(Element[] el, int precision) {
    PrintStream eleout = System.out;
    // DecimalFormat df = new DecimalFormat("0.0000000000") ;

    for (int i = 0; i < el.length; i++) {
      Element e = el[i];
      if (e.dim == 3) {
        Tuple[] t = e.getRotatedCoords();
        eleout.println("(" + formatDouble(t[0].x, precision) + " " + formatDouble(t[0].y, precision) + ") (" + formatDouble(t[1].x, precision) + " " + formatDouble(t[1].y, precision) + ") (" + formatDouble(t[2].x, precision) + " " + formatDouble(t[2].y, precision) + ")");
      }
    }
  }


  public static void outputSortedResult(IndexedGraph<Element> mesh, int i) {
    Element[] el = new Element[mesh.getNumNodes()];
    int k = 0;
    for (Node<Element> n : mesh) {
      el[k++] = n.getData();
    }
    // Arrays.sort(el);
    writeElementArray(el, i);
  }
}
