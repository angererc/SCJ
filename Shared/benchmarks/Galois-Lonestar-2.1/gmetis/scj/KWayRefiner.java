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

File: KWayRefiner.java 

 */



package gmetis.scj;

import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.IntGraph;
import galois.runtime.ForeachContext;
import galois.runtime.wl.Priority;
import galois.runtime.wl.RandomPermutation;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.UnorderedGaloisSCJComputation;
import gmetis.main.Balancer;
import gmetis.main.MetisGraph;
import gmetis.main.MetisNode;
import gmetis.main.Utility;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import scj.Task;
import util.fn.Lambda2Void;
import util.fn.LambdaVoid;

/**
 * Refiner for KMetis
 */
public class KWayRefiner {

	public void scjTask_refineKWay(Task<Void> now, Task<Void> later, MetisGraph metisGraph, MetisGraph orgGraph, float[] tpwgts, Float ubfactor, final Integer nparts) throws Exception {
		metisGraph.computeKWayPartitionParams(nparts);
		int nlevels = 0;
		MetisGraph metisGraphTemp = metisGraph;
		while (!metisGraphTemp.equals(orgGraph)) {
			metisGraphTemp = metisGraphTemp.getFinerGraph();
			nlevels++;
		}
		
		RandomKwayEdgeRefiner rkRefiner = new RandomKwayEdgeRefiner(tpwgts, nparts, ubfactor, 10, 1);
		
		Task<Void> whileLoopTask = new Task<Void>();
		Task<Integer> refineEnd = new Task<Integer>();
		
		this.scjTask_whileLoop(whileLoopTask, refineEnd, rkRefiner, metisGraph, orgGraph, 0, nlevels, tpwgts, ubfactor, nparts);
		whileLoopTask.hb(refineEnd);
		
		this.scjTask_refineKWayEnd(refineEnd, later, rkRefiner, metisGraphTemp, nlevels, tpwgts, ubfactor, nparts);
		refineEnd.hb(later);
	}
	
	public void scjTask_refineKWayEnd(Task<Integer> now, Task<Void> later, RandomKwayEdgeRefiner rkRefiner, MetisGraph metisGraph, Integer nlevels, float[] tpwgts, Float ubfactor, final Integer nparts) throws Exception {
		int i = now.result();
		if (2 * i >= nlevels && !metisGraph.isBalanced(tpwgts, (float) 1.04 * ubfactor)) {
			metisGraph.computeKWayBalanceBoundary();
			Balancer.greedyKWayEdgeBalance(metisGraph, nparts, tpwgts, ubfactor, 8);
			metisGraph.computeKWayBoundary();
		}
		
		Task<Void> refineEnd2 = new Task<Void>();
		this.scjTask_refineKWayEnd2(refineEnd2, later, rkRefiner, metisGraph, tpwgts, ubfactor, nparts);
		refineEnd2.hb(later);
		
		Task<Void> refine = new Task<Void>();
		rkRefiner.scjTask_refine(refine, refineEnd2, metisGraph);
		refine.hb(refineEnd2);
		
	}
	
	public void scjTask_refineKWayEnd2(Task<Void> now, Task<Void> later, RandomKwayEdgeRefiner rkRefiner, MetisGraph metisGraph, float[] tpwgts, Float ubfactor, final Integer nparts) throws Exception {
		if (!metisGraph.isBalanced(tpwgts, ubfactor)) {
			metisGraph.computeKWayBalanceBoundary();
			Balancer.greedyKWayEdgeBalance(metisGraph, nparts, tpwgts, ubfactor, 8);
			
			Task<Void> refine = new Task<Void>();
			rkRefiner.scjTask_refine(refine, later, metisGraph);			
		}
	}
	
	public void scjTask_whileLoop(Task<Void> now, Task<Integer> later, RandomKwayEdgeRefiner rkRefiner, MetisGraph metisGraph, MetisGraph orgGraph, Integer i, Integer nlevels, float[] tpwgts, Float ubfactor, Integer nparts) throws Exception {
		if(metisGraph.equals(orgGraph)) {
			later.setResult(i);
			return;
		}
		
		if (2 * i >= nlevels && !metisGraph.isBalanced(tpwgts, (float) 1.04 * ubfactor)) {
			metisGraph.computeKWayBalanceBoundary();
			Balancer.greedyKWayEdgeBalance(metisGraph, nparts, tpwgts, ubfactor, 8);
			metisGraph.computeKWayBoundary();
		}
		
		final MetisGraph finer = metisGraph.getFinerGraph();
		
		Task<Void> refine = new Task<Void>();
		Task<Void> whileLoopCenter = new Task<Void>();
		Task<Void> whileLoopEnd = new Task<Void>();
		
		rkRefiner.scjTask_refine(refine, whileLoopCenter, metisGraph);
		refine.hb(whileLoopCenter);
		
		this.scjTask_whileLoopCenter(whileLoopCenter, whileLoopEnd, rkRefiner, metisGraph, finer, nparts);
		whileLoopCenter.hb(whileLoopEnd);
		
		this.scjTask_whileLoopEnd(whileLoopEnd, later, rkRefiner, metisGraph, finer, orgGraph, i, nlevels, tpwgts, ubfactor, nparts);
		whileLoopEnd.hb(later);
	}
	
	public void scjTask_whileLoopEnd(Task<Void> now, Task<Integer> later, RandomKwayEdgeRefiner rkRefiner, MetisGraph metisGraph, MetisGraph finer, MetisGraph orgGraph, Integer iParam, Integer nlevels, float[] tpwgts, Float ubfactor, Integer nparts) throws Exception {
		finer.initPartWeight();
		for (int j = 0; j < nparts; j++) {
			finer.setPartWeight(j, metisGraph.getPartWeight(j));
		}
		finer.setMinCut(metisGraph.getMinCut());
		
		int i = iParam;
		Task<Void> nextIteration = new Task<Void>();
		this.scjTask_whileLoop(nextIteration, later, rkRefiner, metisGraph.getFinerGraph(), orgGraph, i++, nlevels, tpwgts, ubfactor, nparts);
	}
	
	public void scjTask_whileLoopCenter(Task<Void> now, Task<Void> later, RandomKwayEdgeRefiner rkRefiner, MetisGraph metisGraph, final MetisGraph finer, final Integer nparts) throws Exception {

		final IntGraph<MetisNode> graph = finer.getGraph();
		graph.map(new LambdaVoid<GNode<MetisNode>>() {
			public void call(GNode<MetisNode> node) {
				MetisNode nodeData = node.getData();
				nodeData.setPartition(nodeData.getMapTo().getData().getPartition());
			}
		});
		
		GaloisSCJComputation<GNode<MetisNode>> computation = new UnorderedGaloisSCJComputation<GNode<MetisNode>>(Utility.getAllNodes(graph), Priority.first(RandomPermutation.class));
		for(int i = 0; i < computation.getNumTasks(); i++) {
			GaloisSCJProcess<GNode<MetisNode>> process = new GaloisSCJProcess<GNode<MetisNode>>(computation, i) {

				@Override
				protected void body(GNode<MetisNode> node,
						ForeachContext<GNode<MetisNode>> context) {
					MetisNode nodeData = node.getData(MethodFlag.NONE, MethodFlag.NONE);
					int numEdges = graph.outNeighborsSize(node, MethodFlag.NONE);
					nodeData.partIndex = new int[numEdges];
					nodeData.partEd = new int[numEdges];
					nodeData.setIdegree(nodeData.getAdjWgtSum());
					if (nodeData.getMapTo().getData(MethodFlag.NONE, MethodFlag.NONE).getEdegree() > 0) {
						int[] map = new int[nparts];
						ProjectNeighborInKWayPartitionClosure closure = new ProjectNeighborInKWayPartitionClosure(graph, map,
								nodeData);
						node.map(closure, node, MethodFlag.NONE);
						nodeData.setEdegree(closure.ed);
						nodeData.setIdegree(nodeData.getIdegree() - nodeData.getEdegree());
						if (nodeData.getEdegree() - nodeData.getIdegree() >= 0)
							finer.setBoundaryNode(node);
						nodeData.setNDegrees(closure.ndegrees);
					}
				}
				
			};
			
			Task<Void> procTask = new Task<Void>();
			process.scjTask_process(procTask);
			
			procTask.hb(later);
			
		}
	}

	public void projectKWayPartition(MetisGraph metisGraph, int nparts) throws ExecutionException {
		final MetisGraph finer = metisGraph.getFinerGraph();
		final IntGraph<MetisNode> coarseGraph = metisGraph.getGraph();
		final IntGraph<MetisNode> graph = finer.getGraph();
		graph.map(new LambdaVoid<GNode<MetisNode>>() {
			public void call(GNode<MetisNode> node) {
				MetisNode nodeData = node.getData();
				nodeData.setPartition(nodeData.getMapTo().getData().getPartition());
			}
		});

		computeKWayPartInfo(nparts, finer, coarseGraph, graph);

		finer.initPartWeight();
		for (int i = 0; i < nparts; i++) {
			finer.setPartWeight(i, metisGraph.getPartWeight(i));
		}
		finer.setMinCut(metisGraph.getMinCut());
	}

	private static void computeKWayPartInfo(final int nparts, final MetisGraph finer,
			final IntGraph<MetisNode> coarseGraph, final IntGraph<MetisNode> graph) throws ExecutionException {

		
	}

	static class ProjectNeighborInKWayPartitionClosure implements Lambda2Void<GNode<MetisNode>, GNode<MetisNode>> {
		MetisNode nodeData;
		int ed;
		int[] map;
		int ndegrees;
		IntGraph<MetisNode> graph;

		public ProjectNeighborInKWayPartitionClosure(IntGraph<MetisNode> graph, int[] map, MetisNode nodeData) {
			this.graph = graph;
			this.map = map;
			this.nodeData = nodeData;
			Arrays.fill(map, -1);
		}

		public void call(GNode<MetisNode> neighbor, GNode<MetisNode> node) {
			MetisNode neighborData = neighbor.getData(MethodFlag.NONE);
			if (nodeData.getPartition() != neighborData.getPartition()) {
				int edgeWeight = (int) graph.getEdgeData(node, neighbor, MethodFlag.NONE);
				ed += edgeWeight;
				int index = map[neighborData.getPartition()];
				if (index == -1) {
					map[neighborData.getPartition()] = ndegrees;
					nodeData.partIndex[ndegrees] = neighborData.getPartition();
					nodeData.partEd[ndegrees] += edgeWeight;
					ndegrees++;
				} else {
					nodeData.partEd[index] += edgeWeight;
				}
			}
		}
	}
}
