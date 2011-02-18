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

File: Main.java 

 */

package barneshut.main;

import galois.objects.MethodFlag;
import galois.objects.graph.ArrayIndexedTree;
import galois.objects.graph.GNode;
import galois.objects.graph.IndexedGraph;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.FIFO;
import galois.runtime.wl.Priority;
import galois.runtime.wl.Worklist;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.ReducedGaloisRuntime;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import scj.Task;
import scj.util.UtilityTasks;
import util.Launcher;

/**
 * Main class of the Barnes Hut application.
 */
public final class SCJMain {
	private static int nbodies; // number of bodies in system
	private static int ntimesteps; // number of time steps to run
	private static double dtime; // length of one time step
	private static double eps; // potential softening parameter
	private static double tol; // tolerance for stopping recursion, should be less than 0.57 for 3D case to bound error

	private static double dthf, epssq, itolsq;
	private static OctTreeLeafNodeData body[]; // the n bodies
	private static GNode<OctTreeNodeData> leaf[];
	private static double diameter, centerx, centery, centerz;
	private static int curr;

	/*
	 * Reads the input from a gzipped or clear text file.
	 */
	@SuppressWarnings("unchecked")
	private static void ReadInput(String filename, boolean print) {
		double vx, vy, vz;
		Scanner in = null;
		try {
			if (filename.endsWith(".gz")) {
				in = new Scanner(new GZIPInputStream(new FileInputStream(filename)));
			} else {
				in = new Scanner(new BufferedReader(new FileReader(filename)));
			}
			in.useLocale(Locale.US);
		} catch (FileNotFoundException e) {
			System.err.println(e);
			System.exit(-1);
		} catch (IOException e) {
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
		if (print) {
			System.err.println("configuration: " + nbodies + " bodies, " + ntimesteps + " time steps");
			System.err.println();
		}
		body = new OctTreeLeafNodeData[nbodies];
		leaf = new GNode[nbodies];
		for (int i = 0; i < nbodies; i++) {
			body[i] = new OctTreeLeafNodeData();
		}
		for (int i = 0; i < nbodies; i++) {
			body[i].mass = in.nextDouble();
			body[i].posx = in.nextDouble();
			body[i].posy = in.nextDouble();
			body[i].posz = in.nextDouble();
			vx = in.nextDouble();
			vy = in.nextDouble();
			vz = in.nextDouble();
			body[i].setVelocity(vx, vy, vz);
		}
	}

	/*
	 * Computes a bounding box around all the bodies.
	 */
	private static void ComputeCenterAndDiameter() {
		double minx, miny, minz;
		double maxx, maxy, maxz;
		double posx, posy, posz;
		minx = miny = minz = Double.MAX_VALUE;
		maxx = maxy = maxz = Double.MIN_VALUE;
		for (int i = 0; i < nbodies; i++) {
			posx = body[i].posx;
			posy = body[i].posy;
			posz = body[i].posz;
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

	/*
	 * Recursively inserts a body into the octree.
	 */
	private static void Insert(IndexedGraph<OctTreeNodeData> octree, GNode<OctTreeNodeData> root, OctTreeLeafNodeData b,
			double r) {
		double x = 0.0, y = 0.0, z = 0.0;
		OctTreeNodeData n = root.getData();
		int i = 0;
		if (n.posx < b.posx) {
			i = 1;
			x = r;
		}
		if (n.posy < b.posy) {
			i += 2;
			y = r;
		}
		if (n.posz < b.posz) {
			i += 4;
			z = r;
		}
		GNode<OctTreeNodeData> child = octree.getNeighbor(root, i);
		if (child == null) {
			GNode<OctTreeNodeData> newnode = octree.createNode(b);
			octree.add(newnode);
			octree.setNeighbor(root, newnode, i);
		} else {
			double rh = 0.5 * r;
			OctTreeNodeData ch = child.getData();
			if (!(ch.isLeaf())) {
				Insert(octree, child, b, rh);
			} else {
				GNode<OctTreeNodeData> newnode = octree.createNode(new OctTreeNodeData(n.posx - rh + x, n.posy - rh + y, n.posz
						- rh + z));
				octree.add(newnode);
				Insert(octree, newnode, b, rh);
				Insert(octree, newnode, (OctTreeLeafNodeData) ch, rh);
				octree.setNeighbor(root, newnode, i);
			}
		}
	}

	/*
	 * Traverses the tree bottom up to compute the total mass and the center of
	 * mass of all the bodies in the subtree rooted in each internal octree node.
	 */
	private static void ComputeCenterOfMass(IndexedGraph<OctTreeNodeData> octree, GNode<OctTreeNodeData> root) {
		double m, px = 0.0, py = 0.0, pz = 0.0;
		OctTreeNodeData n = root.getData();
		int j = 0;
		n.mass = 0.0;
		for (int i = 0; i < 8; i++) {
			GNode<OctTreeNodeData> child = octree.getNeighbor(root, i);
			if (child != null) {
				if (i != j) {
					octree.removeNeighbor(root, i);
					octree.setNeighbor(root, child, j); // move non-null children to the
					// front (needed later to make other code faster)
				}
				j++;
				OctTreeNodeData ch = child.getData();
				if (ch.isLeaf()) {
					leaf[curr++] = child; // sort bodies in tree order (approximation of
					// putting nearby nodes together for locality)
				} else {
					ComputeCenterOfMass(octree, child);
				}
				m = ch.mass;
				n.mass += m;
				px += ch.posx * m;
				py += ch.posy * m;
				pz += ch.posz * m;
			}
		}
		m = 1.0 / n.mass;
		n.posx = px * m;
		n.posy = py * m;
		n.posz = pz * m;
	}

	/*
	 * Calculates the force acting on a body
	 */
	private static void ComputeForce(GNode<OctTreeNodeData> leaf, IndexedGraph<OctTreeNodeData> octree,
			GNode<OctTreeNodeData> root, double size, double itolsq, int step, double dthf, double epssq) {
		double ax, ay, az;
		OctTreeLeafNodeData nd = (OctTreeLeafNodeData) leaf.getData(MethodFlag.NONE);

		ax = nd.accx;
		ay = nd.accy;
		az = nd.accz;
		nd.accx = 0.0;
		nd.accy = 0.0;
		nd.accz = 0.0;
		RecurseForce(leaf, octree, root, size * size * itolsq, epssq);
		if (step > 0) {
			nd.velx += (nd.accx - ax) * dthf;
			nd.vely += (nd.accy - ay) * dthf;
			nd.velz += (nd.accz - az) * dthf;
		}
	}

	/*
	 * Helper method to compute the force acting on a body (recursively walks the
	 * tree)
	 */
	private static void RecurseForce(GNode<OctTreeNodeData> leaf, IndexedGraph<OctTreeNodeData> octree,
			GNode<OctTreeNodeData> nn, double dsq, double epssq) {
		double drx, dry, drz, drsq, nphi, scale, idr;
		OctTreeLeafNodeData nd = (OctTreeLeafNodeData) leaf.getData(MethodFlag.NONE);
		OctTreeNodeData n = nn.getData(MethodFlag.NONE);
		drx = n.posx - nd.posx;
		dry = n.posy - nd.posy;
		drz = n.posz - nd.posz;
		drsq = drx * drx + dry * dry + drz * drz;
		if (drsq < dsq) {
			if (!(n.isLeaf())) { // n is a cell
				dsq *= 0.25;
				GNode<OctTreeNodeData> child = octree.getNeighbor(nn, 0, MethodFlag.NONE);
				if (child != null) {
					RecurseForce(leaf, octree, child, dsq, epssq);
					child = octree.getNeighbor(nn, 1, MethodFlag.NONE);
					if (child != null) {
						RecurseForce(leaf, octree, child, dsq, epssq);
						child = octree.getNeighbor(nn, 2, MethodFlag.NONE);
						if (child != null) {
							RecurseForce(leaf, octree, child, dsq, epssq);
							child = octree.getNeighbor(nn, 3, MethodFlag.NONE);
							if (child != null) {
								RecurseForce(leaf, octree, child, dsq, epssq);
								child = octree.getNeighbor(nn, 4, MethodFlag.NONE);
								if (child != null) {
									RecurseForce(leaf, octree, child, dsq, epssq);
									child = octree.getNeighbor(nn, 5, MethodFlag.NONE);
									if (child != null) {
										RecurseForce(leaf, octree, child, dsq, epssq);
										child = octree.getNeighbor(nn, 6, MethodFlag.NONE);
										if (child != null) {
											RecurseForce(leaf, octree, child, dsq, epssq);
											child = octree.getNeighbor(nn, 7, MethodFlag.NONE);
											if (child != null) {
												RecurseForce(leaf, octree, child, dsq, epssq);
											}
										}
									}
								}
							}
						}
					}
				}
			} else { // n is a body
				if (n != nd) {
					drsq += epssq;
					idr = 1 / Math.sqrt(drsq);
					nphi = n.mass * idr;
					scale = nphi * idr * idr;
					nd.accx += drx * scale;
					nd.accy += dry * scale;
					nd.accz += drz * scale;
				}
			}
		} else { // node is far enough away, don't recurse any deeper
			drsq += epssq;
			idr = 1 / Math.sqrt(drsq);
			nphi = n.mass * idr;
			scale = nphi * idr * idr;
			nd.accx += drx * scale;
			nd.accy += dry * scale;
			nd.accz += drz * scale;
		}
	}

	/*
	 * Advances a body's velocity and position by one time step
	 */
	private static void Advance(IndexedGraph<OctTreeNodeData> octree, double dthf, double dtime) {
		double dvelx, dvely, dvelz;
		double velhx, velhy, velhz;

		for (int i = 0; i < nbodies; i++) {
			OctTreeLeafNodeData nd = (OctTreeLeafNodeData) leaf[i].getData();
			body[i] = nd;
			dvelx = nd.accx * dthf;
			dvely = nd.accy * dthf;
			dvelz = nd.accz * dthf;
			velhx = nd.velx + dvelx;
			velhy = nd.vely + dvely;
			velhz = nd.velz + dvelz;
			nd.posx += velhx * dtime;
			nd.posy += velhy * dtime;
			nd.posz += velhz * dtime;
			nd.velx = velhx + dvelx;
			nd.vely = velhy + dvely;
			nd.velz = velhz + dvelz;
		}
	}

	public void scjMainTask_Main(Task<Void> now) throws Exception {
		//create a linked list of chained iterations
		Task<Void> later = new Task<Void>();
		UtilityTasks.get().scjTask_join(later);
		
		for (int step = ntimesteps - 1; step >= 0; step--) { // time-step the system
			Task<Void> iteration = new Task<Void>();
			this.scjTask_ComputeTimeStep(iteration, step, later);
			iteration.hb(later);
			later = iteration;
		} // end of time step
	}
	
	public void scjTask_ComputeTimeStep(Task<Void> now, Integer step, Task<Void> later) throws Exception {
		//one iteration
		
		ComputeCenterAndDiameter();
		ArrayIndexedTree.Builder builder = new ArrayIndexedTree.Builder();
		final IndexedGraph<OctTreeNodeData> octree = builder.branchingFactor(8).create();
		final GNode<OctTreeNodeData> root = octree.createNode(new OctTreeNodeData(centerx, centery, centerz)); // create the
		// tree's
		// root
		octree.add(root);
		double radius = diameter * 0.5;
		for (int i = 0; i < nbodies; i++) {
			Insert(octree, root, body[i], radius); // grow the tree by inserting
			// each body
		}
		curr = 0;
		ComputeCenterOfMass(octree, root); // summarize subtree info in each internal node (plus restructure tree and sort bodies for performance reasons)

		Task<Void> join = new Task<Void>();
		UtilityTasks.get().scjTask_join(join);
		
		Worklist<GNode<OctTreeNodeData>> worklist = Priority.makeUnordered(Priority.first(ChunkedFIFO.class).then(FIFO.class));		
		GaloisSCJComputation<GNode<OctTreeNodeData>> computation = new GaloisSCJComputation<GNode<OctTreeNodeData>>(worklist, GaloisRuntime.getRuntime().getMaxThreads());
		computation.initializeWorklist(Arrays.asList(leaf));
		for(int i = 0; i < computation.getNumTasks(); i++) {			
			Task<Void> computeForceTask = new Task<Void>();
			this.scjTask_ComputeForce(computeForceTask, computation, i, octree, root, step);
			
			computeForceTask.hb(join);
		}
		
		Task<Void> advance = new Task<Void>();
		this.scjTask_Advance(advance, octree, root, step);
		
		join.hb(advance);
		advance.hb(later);

	}
	
	public void scjTask_ComputeForce(Task<Void> now, GaloisSCJComputation<GNode<OctTreeNodeData>> computation, Integer taskID, 
			final IndexedGraph<OctTreeNodeData> octree, final GNode<OctTreeNodeData> root, final Integer step) throws Exception {
		
		GaloisSCJProcess<GNode<OctTreeNodeData>> process = new GaloisSCJProcess<GNode<OctTreeNodeData>>(computation, taskID) {

			@Override
			protected void body(GNode<OctTreeNodeData> item,
					ForeachContext<GNode<OctTreeNodeData>> context) {
				ComputeForce(item, octree, root, diameter, itolsq, step, dthf, epssq);
			}
			
		};
		process.doCall();
	}
	
	public void scjTask_Advance(Task<Void> now, IndexedGraph<OctTreeNodeData> octree, GNode<OctTreeNodeData> root, Integer step) {
		Advance(octree, dthf, dtime); // advance the position and velocity of each
		// body
		if (Launcher.getLauncher().isFirstRun()) {
			// print center of mass for this timestep
			res = root.getData();
			System.out.println("Timestep " + step + " Center of Mass = " + df.format(res.posx) + " " + df.format(res.posy)
					+ " " + df.format(res.posz));
		}
	}
	/**
	 * Main method to launch the binary search tree code.
	 * 
	 * @param args
	 *          array of command line parameters: only one parameter is expected,
	 *          which is the name of the input file
	 * @throws Exception 
	 */
	static DecimalFormat df;
	static OctTreeNodeData res;
	
	public static void main(String args[]) throws Exception {
		ReducedGaloisRuntime.initialize(false, false);
		//FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("Lonestar Benchmark Suite v3.0");
			System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
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
		ReadInput(args[0], true);
		df = new DecimalFormat("0.0000E00");
		res = null;

		Launcher.getLauncher().startTiming();
		
		SCJMain main = new SCJMain();
		main.scjMainTask_Main(new Task<Void>());
		
		Launcher.getLauncher().stopTiming();

		if (Launcher.getLauncher().isFirstRun()) { // verify result
			ReadInput(args[0], false);
			OctTreeNodeData s_res = null;

			for (int step = 0; step < ntimesteps; step++) {
				final int s = step;
				ComputeCenterAndDiameter();
				ArrayIndexedTree.Builder builder = new ArrayIndexedTree.Builder();
				final IndexedGraph<OctTreeNodeData> octree = builder.branchingFactor(8).create();
				final GNode<OctTreeNodeData> root = octree.createNode(new OctTreeNodeData(centerx, centery, centerz));
				octree.add(root);
				double radius = diameter * 0.5;
				for (int i = 0; i < nbodies; i++) {
					Insert(octree, root, body[i], radius);
				}
				curr = 0;
				ComputeCenterOfMass(octree, root);

				for (GNode<OctTreeNodeData> item : leaf) {
					ComputeForce(item, octree, root, diameter, itolsq, s, dthf, epssq);
				}
				Advance(octree, dthf, dtime);
				s_res = root.getData();
			}

			if ((Math.abs(res.posx - s_res.posx) / Math.abs(Math.min(res.posx, s_res.posx)) > 0.001)
					|| (Math.abs(res.posy - s_res.posy) / Math.abs(Math.min(res.posy, s_res.posy)) > 0.001)
					|| (Math.abs(res.posz - s_res.posz) / Math.abs(Math.min(res.posz, s_res.posz)) > 0.001)) {
				System.err.println("verification failed");
			} else {
				System.err.println("verification succeeded");

			}
		}
	}
}
