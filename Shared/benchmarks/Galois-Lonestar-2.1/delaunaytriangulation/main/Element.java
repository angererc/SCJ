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

package delaunaytriangulation.main;

import galois.objects.AbstractNoConflictBaseObject;

import java.util.ArrayList;

public final class Element extends AbstractNoConflictBaseObject implements Comparable<Element> {
  public final Tuple[] coords;
  public final int dim;
  public ArrayList<Tuple> tuples;
  public boolean processed;

  public Element(Tuple a, Tuple b, Tuple c) {
    dim = 3;
    coords = new Tuple[3];
    coords[0] = a;
    coords[1] = b;
    coords[2] = c;
    processed = false;
  }

  public Element(Tuple a, Tuple b) {
    dim = 2;
    coords = new Tuple[2];
    coords[0] = a;
    coords[1] = b;
    processed = false;
  }

  /**
   * get one tuple from the tuple list to insert into the mesh
   */
  protected Tuple sample() {
    return tuples.get(tuples.size() - 1);
  }

  @Override
  public int compareTo(Element e) {
    if (lessThan(e)) {
      return -1;
    } else {
      return 1;
    }
  }

  public boolean lessThan(Element e) {
    if (dim < e.dim) {
      return false;
    }
    if (dim > e.dim) {
      return true;
    }
    Tuple[] t1 = getRotatedCoords();
    Tuple[] t2 = e.getRotatedCoords();
    for (int i = 0; i < dim; i++) {
      if (t1[i].compareTo(t2[i]) == -1) {
        return true;
      } else if (t1[i].compareTo(t2[i]) == 1) {
        return false;
      }
    }
    return false;
  }

  /**
   * sort the tuples(coordinates) of the triangle
   * @return the sorted tuples, tuples[0] < tuples[1] < tuples[2]
   */
  public Tuple[] getRotatedCoords() {
    if (dim == 3) {
      Tuple[] t = new Tuple[3];
      if (coords[0].compareTo(coords[1]) == -1) {
        t[0] = coords[0];
        t[1] = coords[1];
      } else {
        t[0] = coords[1];
        t[1] = coords[0];
      }
      t[2] = coords[2];
      if (t[0].compareTo(coords[2]) < 1) {
        if (t[1].compareTo(coords[2]) == 1) {
          t[2] = t[1];
          t[1] = coords[2];
        }
      } else {
        t[2] = t[1];
        t[1] = t[0];
        t[0] = coords[2];
      }
      return t;
    } else {
      Tuple[] t = new Tuple[2];
      if (coords[0].compareTo(coords[1]) == -1) {
        t[0] = coords[0];
        t[1] = coords[1];
      } else {
        t[0] = coords[1];
        t[1] = coords[0];
      }
      return t;
    }

  }

  /**
   * determine if a tuple is inside the triangle
   */
  public boolean elementContains(Tuple p) {
    Tuple p1 = coords[0];
    Tuple p2 = coords[1];
    Tuple p3 = coords[2];

    if ((p1 == p) || (p2 == p) || (p3 == p)) {
      return false;
    }

    int count = 0;
    double px = p.x;
    double py = p.y;
    double p1x = p1.x;
    double p1y = p1.y;
    double p2x = p2.x;
    double p2y = p2.y;
    double p3x = p3.x;
    double p3y = p3.y;

    if (p2x < p1x) {
      if ((p2x < px) && (p1x >= px)) {
        if (((py - p2y) * (p1x - p2x)) < ((px - p2x) * (p1y - p2y))) {
          count = 1;
        }
      }
    } else {
      if ((p1x < px) && (p2x >= px)) {
        if (((py - p1y) * (p2x - p1x)) < ((px - p1x) * (p2y - p1y))) {
          count = 1;
        }
      }
    }

    if (p3x < p2x) {
      if ((p3x < px) && (p2x >= px)) {
        if (((py - p3y) * (p2x - p3x)) < ((px - p3x) * (p2y - p3y))) {
          if (count == 1) {
            return false;
          }
          count++;
        }
      }
    } else {
      if ((p2x < px) && (p3x >= px)) {
        if (((py - p2y) * (p3x - p2x)) < ((px - p2x) * (p3y - p2y))) {
          if (count == 1) {
            return false;
          }
          count++;
        }
      }
    }

    if (p1x < p3x) {
      if ((p1x < px) && (p3x >= px)) {
        if (((py - p1y) * (p3x - p1x)) < ((px - p1x) * (p3y - p1y))) {
          if (count == 1) {
            return false;
          }
          count++;
        }
      }
    } else {
      if ((p3x < px) && (p1x >= px)) {
        if (((py - p3y) * (p1x - p3x)) < ((px - p3x) * (p1y - p3y))) {
          if (count == 1) {
            return false;
          }
          count++;
        }
      }
    }

    return count == 1;
  }

  /**
   * determine if the circumcircle of the triangle contains the tuple
   */
  public boolean inCircle(Tuple p) {
    // This version computes the determinant of a matrix including the
    // coordinates of each points + distance of these points to the origin
    // in order to check if a point is inside a triangle or not
    double t1_x = coords[0].x;
    double t1_y = coords[0].y;

    double t2_x = coords[1].x;
    double t2_y = coords[1].y;

    double t3_x = coords[2].x;
    double t3_y = coords[2].y;

    double p_x = p.x;
    double p_y = p.y;

    // Check if the points (t1,t2,t3) are sorted clockwise or
    // counter-clockwise:
    // -> counter_clockwise > 0 => counter clockwise
    // -> counter_clockwise = 0 => degenerated triangle
    // -> counter_clockwise < 0 => clockwise
    double counter_clockwise = (t2_x - t1_x) * (t3_y - t1_y) - (t3_x - t1_x) * (t2_y - t1_y);

    // If the triangle is degenerated, then the triangle should be updated
    if (counter_clockwise == 0.0) {
      return true;
    }

    // Compute the following determinant:
    // | t1_x-p_x  t1_y-p_y  (t1_x-p_x)^2+(t1_y-p_y)^2 |
    // | t2_x-p_x  t2_y-p_y  (t2_x-p_x)^2+(t2_y-p_y)^2 |
    // | t3_x-p_x  t3_y-p_y  (t3_x-p_x)^2+(t3_y-p_y)^2 |
    //
    // If the determinant is >0 then the point (p_x,p_y) is inside the
    // circumcircle of the triangle (t1,t2,t3).

    // Value of columns 1 and 2 of the matrix
    double t1_p_x, t1_p_y, t2_p_x, t2_p_y, t3_p_x, t3_p_y;
    // Determinant of minors extracted from columns 1 and 2
    // (det_t3_t1_m corresponds to the opposite)
    double det_t1_t2, det_t2_t3, det_t3_t1_m;
    // Values of the column 3 of the matrix
    double t1_col3, t2_col3, t3_col3;

    t1_p_x = t1_x - p_x;
    t1_p_y = t1_y - p_y;
    t2_p_x = t2_x - p_x;
    t2_p_y = t2_y - p_y;
    t3_p_x = t3_x - p_x;
    t3_p_y = t3_y - p_y;

    det_t1_t2 = t1_p_x * t2_p_y - t2_p_x * t1_p_y;
    det_t2_t3 = t2_p_x * t3_p_y - t3_p_x * t2_p_y;
    det_t3_t1_m = t3_p_x * t1_p_y - t1_p_x * t3_p_y;
    t1_col3 = t1_p_x * t1_p_x + t1_p_y * t1_p_y;
    t2_col3 = t2_p_x * t2_p_x + t2_p_y * t2_p_y;
    t3_col3 = t3_p_x * t3_p_x + t3_p_y * t3_p_y;

    double det = t1_col3 * det_t2_t3 + t2_col3 * det_t3_t1_m + t3_col3 * det_t1_t2;

    // If the points are enumerated in clockwise, then negate the result
    if (counter_clockwise < 0) {
      return det < 0;
    }
    return det > 0;
  }

  @Override
  public Object gclone() {
    return null;
  }

  @Override
  public void restoreFrom(Object copy) {
  }
}
