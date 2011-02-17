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

File: OctTreeLeafNodeData.java 
*/

import objects.graph.ArrayIndexedGraph;
import objects.graph.Node;

public class OctTreeLeafNodeData extends OctTreeNodeData { // the tree leaves are the bodies
  private double velx;
  private double vely;
  private double velz;
  private double accx;
  private double accy;
  private double accz;

  OctTreeLeafNodeData() {
    super(0.0, 0.0, 0.0);
    velx = 0.0;
    vely = 0.0;
    velz = 0.0;
    accx = 0.0;
    accy = 0.0;
    accz = 0.0;
  }

  void setVelocity(double x, double y, double z) {
    velx = x;
    vely = y;
    velz = z;
  }

  void Advance(double dthf, double dtime) { // advances a body's velocity and position by one time step
    double dvelx, dvely, dvelz;
    double velhx, velhy, velhz;
    dvelx = accx * dthf;
    dvely = accy * dthf;
    dvelz = accz * dthf;
    velhx = velx + dvelx;
    velhy = vely + dvely;
    velhz = velz + dvelz;
    setPosX(posx() + velhx * dtime);
    setPosY(posy() + velhy * dtime);
    setPosZ(posz() + velhz * dtime);
    velx = velhx + dvelx;
    vely = velhy + dvely;
    velz = velhz + dvelz;
  }

  void ComputeForce(ArrayIndexedGraph<OctTreeNodeData> octree, Node<OctTreeNodeData> root, double size, double itolsq,
      int step, double dthf, double epssq) { // computes the acceleration and velocity of a body
    double ax, ay, az;
    ax = accx;
    ay = accy;
    az = accz;
    accx = 0.0;
    accy = 0.0;
    accz = 0.0;
    RecurseForce(octree, root, size * size * itolsq, epssq);
    if (step > 0) {
      velx += (accx - ax) * dthf;
      vely += (accy - ay) * dthf;
      velz += (accz - az) * dthf;
    }
  }

  private void RecurseForce(ArrayIndexedGraph<OctTreeNodeData> octree, Node<OctTreeNodeData> nn, double dsqParam,
      double epssq) { // recursively walks the tree to compute the force on a body
    double dsq, drx, dry, drz, drsq, nphi, scale, idr;
    dsq = dsqParam;
    OctTreeNodeData n = octree.getNodeData(nn);
    drx = n.posx() - posx();
    dry = n.posy() - posy();
    drz = n.posz() - posz();
    drsq = drx * drx + dry * dry + drz * drz;
    if (drsq < dsq) {
      if (!(n instanceof OctTreeLeafNodeData)) { // n is a cell
        dsq *= 0.25;
        Node<OctTreeNodeData> child = octree.getNeighbor(nn, 0);
        if (child != null) {
          RecurseForce(octree, child, dsq, epssq);
          child = octree.getNeighbor(nn, 1);
          if (child != null) {
            RecurseForce(octree, child, dsq, epssq);
            child = octree.getNeighbor(nn, 2);
            if (child != null) {
              RecurseForce(octree, child, dsq, epssq);
              child = octree.getNeighbor(nn, 3);
              if (child != null) {
                RecurseForce(octree, child, dsq, epssq);
                child = octree.getNeighbor(nn, 4);
                if (child != null) {
                  RecurseForce(octree, child, dsq, epssq);
                  child = octree.getNeighbor(nn, 5);
                  if (child != null) {
                    RecurseForce(octree, child, dsq, epssq);
                    child = octree.getNeighbor(nn, 6);
                    if (child != null) {
                      RecurseForce(octree, child, dsq, epssq);
                      child = octree.getNeighbor(nn, 7);
                      if (child != null) {
                        RecurseForce(octree, child, dsq, epssq);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } else { // n is a body
        if (n != this) {
          drsq += epssq;
          idr = 1 / Math.sqrt(drsq);
          nphi = n.mass() * idr;
          scale = nphi * idr * idr;
          accx += drx * scale;
          accy += dry * scale;
          accz += drz * scale;
        }
      }
    } else { // node is far enough away, don't recurse any deeper
      drsq += epssq;
      idr = 1 / Math.sqrt(drsq);
      nphi = n.mass() * idr;
      scale = nphi * idr * idr;
      accx += drx * scale;
      accy += dry * scale;
      accz += drz * scale;
    }
  }

  @Override
  public int hashCode() {
	 return (int) (this.posx() * 2003 + this.posy() * 953 + this.posz()); 
  }
  
  @Override
  public boolean equals(Object o) {
    OctTreeLeafNodeData n = (OctTreeLeafNodeData) o;
    return (this.posx() == n.posx() && this.posy() == n.posy() && this.posz() == n.posz());
  }

  @Override
  public String toString() {
    String result = super.toString();
    result += "vel = (" + velx + "," + vely + "," + velz + ")";
    result += "acc = (" + accx + "," + accy + "," + accz + ")";
    return result;
  }
}
