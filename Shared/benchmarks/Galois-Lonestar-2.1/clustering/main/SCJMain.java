package clustering.main;

import galois.runtime.Features;
import galois.runtime.ForeachContext;
import galois.runtime.GaloisRuntime;
import galois.runtime.ReplayFeature;
import galois.runtime.wl.ChunkedFIFO;
import galois.runtime.wl.Priority;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.ReducedGaloisRuntime;
import galois_scj.UnorderedGaloisSCJComputation;

import java.util.Arrays;
import java.util.List;

import scj.Task;
import util.Launcher;
public class SCJMain {

	public static void main(String[] args) throws Exception {
		//FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		ReducedGaloisRuntime.initialize(false, false);
		Features.initialize(GaloisRuntime.getRuntime().getMaxThreads(), ReplayFeature.Type.RECORD);

		int count = args.length > 0 ? Integer.valueOf(args[0]) : 100000;
		if (Launcher.getLauncher().isFirstRun()) {
			System.err.println();
			System.err.println("Lonestar Benchmark Suite v3.0");
			System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
			System.err.println("http://iss.ices.utexas.edu/lonestar/");
			System.err.println();
			System.err.printf("application: Unordered Agglomerative Clustering (%s version)\n", GaloisRuntime.getRuntime()
					.useSerial() ? "serial" : "Galois");
			System.err.println("Unordered Implementation of the well-known data-mining algorithm");
			System.err.println("Agglomerative Clustering");
			System.err.println("http://iss.ices.utexas.edu/lonestar/agglomerativeclustering.html");
			System.err.println();
			System.err.println("number of threads: " + GaloisRuntime.getRuntime().getMaxThreads());
			System.out.println("configuration: " + count + " points");
			System.err.println();
		}
		LeafNode[] lights = randomGenerate(count);
		
		new SCJMain().run(lights);
	}

	static LeafNode[] randomGenerate(int count) {
		RandomGenerator ranGen = new RandomGenerator(12);
		LeafNode lights[] = new LeafNode[count];
		float dirX = 0;
		float dirY = 0;
		float dirZ = 1;
		AbstractNode.setGlobalMultitime();
		AbstractNode.setGlobalNumReps();
		//generating random lights
		for (int i = 0; i < lights.length; i++) {
			float x = (float) ranGen.nextDouble();
			float y = (float) ranGen.nextDouble();
			float z = (float) ranGen.nextDouble();
			lights[i] = new LeafNode(x, y, z, dirX, dirY, dirZ);
		}
		return lights;
	}

	private void run(LeafNode[] inLights) throws Exception {
		this.scjMainTask_clustering(new Task<Void>(), inLights);
	}
	
	public void scjMainTask_clustering(Task<Void> now, LeafNode[] inLights) throws Exception {
		Launcher launcher = Launcher.getLauncher();
		//used to choose which light is the representative light
		final RandomGenerator repRanGen = new RandomGenerator(4523489623489L);
		int tempSize = (1 << NodeWrapper.CONE_RECURSE_DEPTH) + 1;
		final float floatArr[] = new float[3 * tempSize];
		//temporary arrays used during cluster construction
		final ClusterNode clusterArr[] = new ClusterNode[tempSize];
		int numLights = inLights.length;
		NodeWrapper[] initialWorklist = new NodeWrapper[numLights];
		for (int i = 0; i < numLights; i++) {
			NodeWrapper clusterWrapper = new NodeWrapper(inLights[i]);
			initialWorklist[i] = clusterWrapper;
		}
		launcher.startTiming();
		final KdTree kdTree = KdTree.createTree(initialWorklist);
		// O(1) operation, there is no copy of data but just the creation of an arraylist backed by 'initialWorklist'
		final List<NodeWrapper> wrappers = Arrays.asList(initialWorklist);
		
		Task<Void> finishTask = new Task<Void>();
		this.scjTask_finish(finishTask, kdTree, inLights);
		
		GaloisSCJComputation<NodeWrapper> computation = new UnorderedGaloisSCJComputation<NodeWrapper>(wrappers, Priority.first(ChunkedFIFO.class));
		for(int i = 0; i < computation.getNumTasks(); i++) {
			GaloisSCJProcess<NodeWrapper> process = new GaloisSCJProcess<NodeWrapper>(computation, i) {

				@Override
				protected void body(NodeWrapper cluster, ForeachContext<NodeWrapper> ctx) {
					NodeWrapper current = cluster;
					while (current != null && kdTree.contains(current)) {
						NodeWrapper match = kdTree.findBestMatch(current);
						if (match == null) {
							break;
						}
						final NodeWrapper matchMatch = kdTree.findBestMatch(match);
						if (current == matchMatch) {
							if (kdTree.remove(match)) {
								NodeWrapper newCluster = new NodeWrapper(current, match, floatArr, clusterArr, repRanGen);
								ctx.add(newCluster);
								kdTree.add(newCluster);
								kdTree.remove(current);
							}
							break;
						} else {
							if (current == cluster) {
								ctx.add(current);
							}
							current = match;
						}
					}
				}
				
			};
			
			Task<Void> processTask = new Task<Void>();
			process.scjTask_process(processTask);
			
			processTask.hb(finishTask);
		}
		
		
	}
	
	public void scjTask_finish(Task<Void> now, KdTree kdTree, LeafNode[] inLights) {
		Launcher launcher = Launcher.getLauncher();
		launcher.stopTiming();
		if (launcher.isFirstRun()) {
			System.err.print("verifying... ");
			NodeWrapper retval = kdTree.getAny(0.5);
			AbstractNode serialRoot = new SerialMain().clustering(inLights);
			if (!serialRoot.equals(retval.light)) {
				throw new IllegalStateException("verification failed!");
			}
			System.err.println("okay.");
		}
	}
}