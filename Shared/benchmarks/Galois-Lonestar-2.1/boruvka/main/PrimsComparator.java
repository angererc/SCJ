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

File: PrimsComparator.java 

*/

package boruvka.main;

import galois.objects.graph.GNode;

import java.util.Comparator;
import java.util.Map;


public class PrimsComparator<N> implements Comparator<GNode<N>> {
  private Map<GNode<N>, Integer> ordering;

  public PrimsComparator(Map<GNode<N>, Integer> ordering) {
    this.ordering = ordering;
  }

  @Override
  public int compare(GNode<N> o1, GNode<N> o2) {
    return ordering.get(o1).compareTo(ordering.get(o2));
  }

}