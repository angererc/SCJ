package benchmarks.lonestar.barnes_hut;
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

File: SerialBarneshut.java 
 */

import objects.graph.ArrayIndexedGraph;
import objects.graph.Node;
import util.Time;
import scj.Task;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Scanner;


public final class SCJBarneshut {
  private int nbodies; // number of bodies in system
  private int ntimesteps; // number of time steps to run
  private double dtime; // length of one time step
  private double eps; // potential softening parameter
  private double tol; // tolerance for stopping recursion, should be less than 0.57 for 3D case to bound error

  private double dthf, epssq, itolsq;
  private OctTreeLeafNodeData body[]; // the n bodies
  private double diameter, centerx, centery, centerz;
  private int curr;

  private boolean isFirstRun = true;
  private long runtime;

  private Node<OctTreeNodeData> root;
  private ArrayIndexedGraph<OctTreeNodeData> octree;
  private int step;

  @SuppressWarnings("null")
private void ReadInput(String filename) {
    double vx, vy, vz;
    Scanner in = null;
    try {
      in = new Scanner(new BufferedReader(new FileReader(filename)));
      in.useLocale(Locale.US);
    } catch (FileNotFoundException e) {
      System.err.println(e);
      System.exit(-1);
    }
    nbodies = in.nextInt();
    ntimesteps = in.nextInt();
    dtime = in.nextDouble();
    eps = in.nextDouble();
    tol = in.nextDouble();
    dthf = 0.5 * dtime;
    epssq = eps * eps;
    itolsq = 1.0 / (tol * tol);
    if (isFirstRun) {
      System.err.println("configuration: " + nbodies + " bodies, " + ntimesteps + " time steps");
      System.err.println();
    }
    body = new OctTreeLeafNodeData[nbodies];
    for (int i = 0; i < nbodies; i++) {
      body[i] = new OctTreeLeafNodeData();
    }
    for (int i = 0; i < nbodies; i++) {
    	body[i].setMass(in.nextDouble());
        body[i].setPosX(in.nextDouble());
        body[i].setPosY(in.nextDouble());
        body[i].setPosZ(in.nextDouble());
      vx = in.nextDouble();
      vy = in.nextDouble();
      vz = in.nextDouble();
      body[i].setVelocity(vx, vy, vz);
    }
  }


  private void ComputeCenterAndDiameter() {
    double minx, miny, minz;
    double maxx, maxy, maxz;
    double posx, posy, posz;
    minx = miny = minz = Double.MAX_VALUE;
    maxx = maxy = maxz = Double.MIN_VALUE;
    for (int i = 0; i < nbodies; i++) {
      posx = body[i].posx();
      posy = body[i].posy();
      posz = body[i].posz();
      if (minx > posx) {
        minx = posx;
      }
      if (miny > posy) {
        miny = posy;
      }
      if (minz > posz) {
        minz = posz;
      }
      if (maxx < posx) {
        maxx = posx;
      }
      if (maxy < posy) {
        maxy = posy;
      }
      if (maxz < posz) {
        maxz = posz;
      }
    }
    diameter = maxx - minx;
    if (diameter < (maxy - miny)) {
      diameter = (maxy - miny);
    }
    if (diameter < (maxz - minz)) {
      diameter = (maxz - minz);
    }
    centerx = (maxx + minx) * 0.5;
    centery = (maxy + miny) * 0.5;
    centerz = (maxz + minz) * 0.5;
  }


  void ComputeCenterOfMass(ArrayIndexedGraph<OctTreeNodeData> octree, Node<OctTreeNodeData> root) { // recursively summarizes info about subtrees
    double m, px = 0.0, py = 0.0, pz = 0.0;
    OctTreeNodeData n = octree.getNodeData(root);
    int j = 0;
    n.setMass(0.0);
    for (int i = 0; i < 8; i++) {
      Node<OctTreeNodeData> child = octree.getNeighbor(root, i);
      if (child != null) {
        if (i != j) {
          octree.removeNeighbor(root, i);
          octree.setNeighbor(root, j, child); // move non-null children to the front (needed later to make other code faster)
        }
        j++;
        OctTreeNodeData ch = octree.getNodeData(child);
        if (ch instanceof OctTreeLeafNodeData) {
          body[curr++] = (OctTreeLeafNodeData) ch; // sort bodies in tree order (approximation of putting nearby nodes together for locality)
        } else {
          ComputeCenterOfMass(octree, child);
        }
        m = ch.mass();
        n.setMass(n.mass() + m);
        px += ch.posx() * m;
        py += ch.posy() * m;
        pz += ch.posz() * m;
      }
    }
    m = 1.0 / n.mass();
    n.setPosX(px * m);
    n.setPosY(py * m);
    n.setPosZ(pz * m);
  }


  void Insert(ArrayIndexedGraph<OctTreeNodeData> octree, Node<OctTreeNodeData> root, OctTreeLeafNodeData b, double r) { // builds the tree
    double x = 0.0, y = 0.0, z = 0.0;
    OctTreeNodeData n = octree.getNodeData(root);
    int i = 0;
    if (n.posx() < b.posx()) {
      i = 1;
      x = r;
    }
    if (n.posy() < b.posy()) {
      i += 2;
      y = r;
    }
    if (n.posz() < b.posz()) {
      i += 4;
      z = r;
    }
    Node<OctTreeNodeData> child = octree.getNeighbor(root, i);
    if (child == null) {
      Node<OctTreeNodeData> newnode = octree.createNode(b);
      octree.addNode(newnode);
      octree.setNeighbor(root, i, newnode);
    } else {
      double rh = 0.5 * r;
      OctTreeNodeData ch = octree.getNodeData(child);
      if (!(ch instanceof OctTreeLeafNodeData)) {
        Insert(octree, child, b, rh);
      } else {
        Node<OctTreeNodeData> newnode = octree.createNode(new OctTreeNodeData(n.posx() - rh + x, n.posy() - rh + y, n.posz() - rh + z));
        octree.addNode(newnode);
        Insert(octree, newnode, b, rh);
        Insert(octree, newnode, (OctTreeLeafNodeData) ch, rh);
        octree.setNeighbor(root, i, newnode);
      }
    }
  }


  public static void main(String args[]) {
    long runtime, lasttime, mintime, run;

    runtime = 0;
    lasttime = Long.MAX_VALUE;
    mintime = Long.MAX_VALUE;
    run = 0;
    SCJBarneshut barneshut = new SCJBarneshut();
    while (((run < 3) || (Math.abs(lasttime-runtime)*64 > Math.min(lasttime, runtime))) && (run < 7)) {
      System.gc();  System.gc();  System.gc();  System.gc();  System.gc();
      runtime = barneshut.run(args);
      if (runtime < mintime) mintime = runtime;
      run++;
    }
    System.err.println("minimum runtime: " + mintime + " ms");
    System.err.println("");
  }


  public long run(String args[]) {
    if (isFirstRun) {
      System.err.println("Lonestar Benchmark Suite v2.1");
      System.err.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
      System.err.println("http://iss.ices.utexas.edu/lonestar/");
      System.err.println();
      System.err.println("application: BarnesHut (SCJ version)");
      System.err.println("Simulation of the gravitational forces in a galactic");
      System.err.println("cluster using the Barnes-Hut n-body algorithm");
      System.err.println("http://iss.ices.utexas.edu/lonestar/barneshut.html");
      System.err.println();
    }
    if (args.length != 1) {
      System.err.println("arguments: input_file_name");
      System.exit(-1);
    }
    ReadInput(args[0]);

    runtime = 0;
    this.scjMainTask_startComputation(new Task<Void>());
    
    if (isFirstRun) {
      DecimalFormat df = new DecimalFormat("0.0000E00");
      for (int i = 0; i < nbodies; i++) {
        System.out.println(df.format(body[i].posx()) + " " + df.format(body[i].posy()) + " " + df.format(body[i].posz())); // print result
      }
      System.out.println();
    }
    System.err.println("runtime: " + runtime + " ms");

    isFirstRun = false;
    return runtime;
  }
  
  public void scjMainTask_startComputation(Task<Void> now) {
	  step = -1;
	  this.scjTask_computeTimeStep(new Task<Void>());
  }
  
  public void scjTask_computeTimeStep(Task<Void> now) {
	  step++;
	  ComputeCenterAndDiameter();
      octree = new ArrayIndexedGraph<OctTreeNodeData>(8);
      root = octree.createNode(new OctTreeNodeData(centerx, centery, centerz)); // create the tree's root
      octree.addNode(root);
      double radius = diameter * 0.5;
      for (int i = 0; i < nbodies; i++) {
        Insert(octree, root, body[i], radius); // grow the tree by inserting each body
      }
      curr = 0;
      // summarize subtree info in each internal node (plus restructure tree and sort bodies for performance reasons)
      ComputeCenterOfMass(octree, root);
      
      //fork all computeForce computations; schedule before join
      Task<Void> join = new Task<Void>();
      this.scjTask_join(join);
      
      long id = Time.getNewTimeId();
      for (OctTreeLeafNodeData n : body) {
        // compute the acceleration of each body (consumes most of the total runtime)
        n.ComputeForce(octree, root, diameter, itolsq, step, dthf, epssq);
        Task<Void> computeForce = new Task<Void>();
        this.scjTask_computeForce(computeForce, n);
        
        computeForce.hb(join);
      }
      
      //fork all advance computations; schedule after join and before nextStep
      Task<Void> nextStep = null;
      if(step < ntimesteps) {
    	  nextStep = new Task<Void>();
    	  this.scjTask_computeTimeStep(nextStep);
      }
      
      runtime += Time.elapsedTime(id);
      for (int i = 0; i < nbodies; i++) {
    	Task<Void> advanceBody = new Task<Void>();
  	  	this.scjTask_advanceBody(advanceBody, i);
  	  	
  	  	join.hb(advanceBody);
  	  	if(nextStep != null) {
  	  		advanceBody.hb(nextStep);  	  		
  	  	}
      }
  }
  
  public void scjTask_join(Task<Void> now) {
  }
  
  public void scjTask_computeForce(Task<Void> now, OctTreeLeafNodeData n) {
	// compute the acceleration of each body (consumes most of the total runtime)
      n.ComputeForce(octree, root, diameter, itolsq, step, dthf, epssq);
  }
  
  public void scjTask_advanceBody(Task<Void> now, Integer i) {
	  body[i].Advance(dthf, dtime); // advance the position and velocity of each body
  }
}
