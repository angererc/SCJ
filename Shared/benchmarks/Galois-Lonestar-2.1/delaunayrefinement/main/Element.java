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

File: Element.java 

 */

package delaunayrefinement.main;

import galois.objects.AbstractNoConflictBaseObject;

/**
 * The triangle or segment class that is the building block of the mesh
 *
 */
public final class Element extends AbstractNoConflictBaseObject {
  private final boolean bObtuse; // if the triangle has an obtuse angle
  private final boolean bBad;

  private final Tuple obtuse;
  private final Tuple[] coords; // The three endpoints of the triangle
  private final Element.Edge[] edges; // The edges connecting it to neighboring triangles
  private final int dim; // (=3 is a triangle, =2 is a segment)

  private final Tuple center; // The coordinates of the center of the circumcircle the triangle
  private final double radius_squared;

  private static final double MINANGLE = 30.0;

  public Element(Tuple a, Tuple b, Tuple c) {
    dim = 3;
    coords = new Tuple[3];
    coords[0] = a;
    coords[1] = b;
    coords[2] = c;
    if (b.lessThan(a) || c.lessThan(a)) {
      if (b.lessThan(c)) {
        coords[0] = b;
        coords[1] = c;
        coords[2] = a;
      } else {
        coords[0] = c;
        coords[1] = a;
        coords[2] = b;
      }
    }
    edges = new Element.Edge[3];
    edges[0] = new Element.Edge(coords[0], coords[1]);
    edges[1] = new Element.Edge(coords[1], coords[2]);
    edges[2] = new Element.Edge(coords[2], coords[0]);
    boolean l_bObtuse = false;
    boolean l_bBad = false;
    Tuple l_obtuse = null;
    for (int i = 0; i < 3; i++) {
      double angle = getAngle(i);
      if (angle > 90.1) {
        l_bObtuse = true;
        l_obtuse = new Tuple(coords[i]);
      } else if (angle < MINANGLE) {
        l_bBad = true;
      }
    }
    bBad = l_bBad;
    bObtuse = l_bObtuse;
    obtuse = l_obtuse;
    Tuple x = b.subtract(a);
    Tuple y = c.subtract(a);
    double xlen = a.distance(b);
    double ylen = a.distance(c);
    double cosine = x.dotp(y) / (xlen * ylen);
    double sine_sq = 1.0 - cosine * cosine;
    double plen = ylen / xlen;
    double s = plen * cosine;
    double t = plen * sine_sq;
    double wp = (plen - cosine) / (2 * t);
    double wb = 0.5 - (wp * s);
    Tuple tmpval = a.scale(1 - wb - wp);
    tmpval = tmpval.add(b.scale(wb));
    center = tmpval.add(c.scale(wp));
    radius_squared = center.distance_squared(a);
  }

  public Element(Tuple a, Tuple b) {
    dim = 2;
    coords = new Tuple[2];
    coords[0] = a;
    coords[1] = b;
    if (b.lessThan(a)) {
      coords[0] = b;
      coords[1] = a;
    }
    edges = new Element.Edge[2];
    edges[0] = new Element.Edge(coords[0], coords[1]);
    edges[1] = new Element.Edge(coords[1], coords[0]);
    bBad = false;
    bObtuse = false;
    obtuse = null;
    center = (a.add(b)).scale(0.5);
    radius_squared = center.distance_squared(a);
  }

  public static class Edge extends AbstractNoConflictBaseObject {
    private final Tuple p1;
    private final Tuple p2;
    private final int hashvalue;

    public Edge() {
      p1 = null;
      p2 = null;
      hashvalue = 1;
    }

    public Edge(Tuple a, Tuple b) {
      if (a.lessThan(b)) {
        p1 = a;
        p2 = b;
      } else {
        p1 = b;
        p2 = a;
      }
      int tmphashval = 17;
      tmphashval = 37 * tmphashval + p1.hashCode();
      tmphashval = 37 * tmphashval + p2.hashCode();
      hashvalue = tmphashval;
    }

    public Edge(Edge rhs) {
      p1 = rhs.p1;
      p2 = rhs.p2;
      hashvalue = rhs.hashvalue;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof Edge)) {
        return false;
      }
      Edge edge = (Edge) obj;
      return p1.equals(edge.p1) && p2.equals(edge.p2);
    }

    public int hashCode() {
      return hashvalue;
    }

    public boolean notEqual(Edge rhs) {
      return !equals(rhs);
    }

    public boolean lessThan(Edge rhs) {
      return p1.lessThan(rhs.p1) || (p1.equals(rhs.p1) && p2.lessThan(rhs.p2));
    }

    public boolean greaterThan(Edge rhs) {
      return p1.greaterThan(rhs.p1) || (p1.equals(rhs.p1) && p2.greaterThan(rhs.p2));
    }

    public Tuple getPoint(int i) {
      if (i == 0) {
        return p1;
      } else if (i == 1) {
        return p2;
      } else {
        System.exit(-1);
        return null;
      }
    }

    public String toString() {
      return "<" + p1.toString() + ", " + p2.toString() + ">";
    }

    @Override
    public Object gclone() {
      // Immutable type
      return null;
    }

    @Override
    public void restoreFrom(Object copy) {
    }
  }

  public boolean lessThan(Element e) {
    if (dim < e.getDim()) {
      return false;
    }
    if (dim > e.getDim()) {
      return true;
    }
    for (int i = 0; i < dim; i++) {
      if (coords[i].lessThan(e.coords[i])) {
        return true;
      } else if (coords[i].greaterThan(e.coords[i])) {
        return false;
      }
    }
    return false;
  }

  /**
   * 
   * @param e a segment or triangle 
   * @return if the current triangle has a common edge with e 
   */
  public boolean isRelated(Element e) {
    int edim = e.getDim();
    Element.Edge my_edge, e_edge0, e_edge1, e_edge2 = null;
    my_edge = edges[0];
    e_edge0 = e.edges[0];
    if (my_edge.equals(e_edge0)) {
      return true;
    }
    e_edge1 = e.edges[1];
    if (my_edge.equals(e_edge1)) {
      return true;
    }
    if (edim == 3) {
      e_edge2 = e.edges[2];
      if (my_edge.equals(e_edge2)) {
        return true;
      }
    }
    my_edge = edges[1];
    if (my_edge.equals(e_edge0)) {
      return true;
    }
    if (my_edge.equals(e_edge1)) {
      return true;
    }
    if (edim == 3) {
      if (my_edge.equals(e_edge2)) {
        return true;
      }
    }
    if (dim == 3) {
      my_edge = edges[2];
      if (my_edge.equals(e_edge0)) {
        return true;
      }
      if (my_edge.equals(e_edge1)) {
        return true;
      }
      if (edim == 3) {
        if (my_edge.equals(e_edge2)) {
          return true;
        }
      }
    }
    return false;
  }

  public String toString() {
    String ret = "[";
    for (int i = 0; i < dim; i++) {
      ret += coords[i].toString();
      if (i != (dim - 1)) {
        ret += ", ";
      }
    }
    ret += "]";
    return ret;
  }

  public Tuple getCenter() {
    return center;
  }

  public boolean inCircle(Tuple p) {
    double ds = center.distance_squared(p);
    return ds <= radius_squared;
  }

  public double getAngle(int i) {
    int j = i + 1;
    if (j == dim) {
      j = 0;
    }
    int k = j + 1;
    if (k == dim) {
      k = 0;
    }
    Tuple a = coords[i];
    Tuple b = coords[j];
    Tuple c = coords[k];
    return Tuple.angle(b, a, c);
  }

  public Element.Edge getEdge(int i) {
    return edges[i];
  }

  public Tuple getPoint(int i) {
    return coords[i];
  }

  public Tuple getObtuse() {
    return obtuse;
  }

  // should the node be processed?
  public boolean isBad() {
    return bBad;
  }

  public int getDim() {
    return dim;
  }

  public int numEdges() {
    return dim + dim - 3;
  }

  public boolean isObtuse() {
    return bObtuse;
  }

  /**
   * Scans all the edges of the two elements and if it finds one that is
   * equal, then sets this as the Edge of the EdgeRelation
   */
  public Edge getRelatedEdge(Element e) {
    int edim = e.getDim();
    Element.Edge my_edge, e_edge0, e_edge1, e_edge2 = null;
    my_edge = edges[0];
    e_edge0 = e.edges[0];
    if (my_edge.equals(e_edge0)) {
      return my_edge;
    }
    e_edge1 = e.edges[1];
    if (my_edge.equals(e_edge1)) {
      return my_edge;
    }
    if (edim == 3) {
      e_edge2 = e.edges[2];
      if (my_edge.equals(e_edge2)) {
        return my_edge;
      }
    }
    my_edge = edges[1];
    if (my_edge.equals(e_edge0)) {
      return my_edge;
    }
    if (my_edge.equals(e_edge1)) {
      return my_edge;
    }
    if (edim == 3) {
      if (my_edge.equals(e_edge2)) {
        return my_edge;
      }
    }
    if (dim == 3) {
      my_edge = edges[2];
      if (my_edge.equals(e_edge0)) {
        return my_edge;
      }
      if (my_edge.equals(e_edge1)) {
        return my_edge;
      }
      if (edim == 3) {
        if (my_edge.equals(e_edge2)) {
          return my_edge;
        }
      }
    }
    return null;
  }

  @Override
  public Object gclone() {
    // Immutable type
    return null;
  }

  @Override
  public void restoreFrom(Object copy) {

  }
}
