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

File: Tuple.java 
*/

public class Tuple implements Comparable<Tuple> {
  public final double x;
  public final double y;


  public Tuple(double a, double b) {
    x = a;
    y = b;
  }


  @Override
  public int compareTo(Tuple t) {
    if (x > t.x)
      return 1;
    if (x < t.x)
      return -1;
    if (y > t.y)
      return 1;
    if (y < t.y)
      return -1;
    return 0;
  }
}
