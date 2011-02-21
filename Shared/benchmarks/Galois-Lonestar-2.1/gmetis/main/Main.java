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

File: AbstractMain.java 

 */

package gmetis.main;

import galois.objects.graph.IntGraph;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.ReplayFeature;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import util.Launcher;

public class Main {	
	public static void main(String[] args) throws NumberFormatException, ExecutionException {
		Main main = new Main();
		main.run(args);
	}
 
	public void run(String args[]) throws NumberFormatException, ExecutionException {
		FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		if (args.length < 2) {
			System.err.println("Arguments: <input file> <num of partitions>");
			System.exit(-1);
		}

		if (Launcher.getLauncher().isFirstRun()) {
			String version = GaloisRuntime.getRuntime().useSerial() ? "serial" : "Galois";
			
			System.err.println();
			System.err.println("Lonestar Benchmark Suite v3.0");
			System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
			System.err.println("http://iss.ices.utexas.edu/lonestar/");
			System.err.println();
			System.err.printf("application: GMetis (%s version)\n", version);
			System.err.println("Partion a graph into K parts and minimize the graph cut");
			System.err.println("and maintain partition balance");
                  System.err.println("http://iss.ices.utexas.edu/lonestar/gmetis.html");
			System.err.println();
		}
		MetisGraph metisGraph = Utility.readGraph(args[0]);

		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println("Configuration");
			System.err.println("-------------");
			System.err.println("Input: " + args[0] + " num of partitions: " + args[1]);
			System.err.println("Graph size: " + metisGraph.getGraph().size() + " nodes and " + metisGraph.getNumEdges()
					+ " edges");
			System.err.println();
		}

		System.gc();
		System.gc();
		System.gc();
		System.gc();
		System.gc();

		long time = System.nanoTime();
		Launcher.getLauncher().startTiming();
		partition(metisGraph, Integer.valueOf(args[1]));
		Launcher.getLauncher().stopTiming();
		time = (System.nanoTime() - time) / 1000000;
		System.err.println("mincut: " + metisGraph.getMinCut());
		System.err.println("runtime: " + time + " ms");
		System.err.println();
		if (Launcher.getLauncher().isFirstRun()) {
			verify(metisGraph);
		}
	}

	/**
	 * KMetis Algorithm 
	 */
	public void partition(MetisGraph metisGraph, int nparts) throws ExecutionException {
		IntGraph<MetisNode> graph = metisGraph.getGraph();

		int coarsenTo = (int) Math.max(graph.size() / (40 * Math.log(nparts)), 20 * (nparts));
		int maxVertexWeight = (int) (1.5 * ((graph.size()) / (double) coarsenTo));
		Coarsener coarsener = new Coarsener(false, coarsenTo, maxVertexWeight);
		long time = System.nanoTime();
		MetisGraph mcg = coarsener.coarsen(metisGraph);
		time = (System.nanoTime() - time) / 1000000;
		System.err.println("coarsening time: " + time + " ms");

		MetisGraph.nparts = 2;
		float[] totalPartitionWeights = new float[nparts];
		Arrays.fill(totalPartitionWeights, 1 / (float) nparts);
		time = System.nanoTime();
		maxVertexWeight = (int) (1.5 * ((mcg.getGraph().size()) / Coarsener.COARSEN_FRACTION));
		PMetis pmetis = new PMetis(20, maxVertexWeight);
		pmetis.mlevelRecursiveBisection(mcg, nparts, totalPartitionWeights, 0, 0);
		time = (System.nanoTime() - time) / 1000000;
		System.err.println("initial partition time: " + time + " ms");
		MetisGraph.nparts = nparts;
		time = System.nanoTime();
		Arrays.fill(totalPartitionWeights, 1 / (float) nparts);
		KWayRefiner refiner = new KWayRefiner();
		refiner.refineKWay(mcg, metisGraph, totalPartitionWeights, (float) 1.03, nparts);
		time = (System.nanoTime() - time) / 1000000;
		System.err.println("refine time: " + time + " ms");
	}

	public void verify(MetisGraph metisGraph) {
		if (!metisGraph.verify()) {
			throw new IllegalStateException("KMetis failed.");
		}
		System.err.println("KMetis okay");
		System.err.println();
	}
}
