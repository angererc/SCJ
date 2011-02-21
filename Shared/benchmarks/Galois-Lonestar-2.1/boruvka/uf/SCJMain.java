package boruvka.uf;

import galois.objects.Bag;
import galois.objects.BagBuilder;
import galois.objects.Mappables;
import galois.objects.MethodFlag;
import galois.objects.graph.GNode;
import galois.objects.graph.GraphGenerator;
import galois.objects.graph.MorphGraph;
import galois.objects.graph.ObjectGraph;
import galois.runtime.ForeachContext;
import galois.runtime.FullGaloisRuntime;
import galois.runtime.GaloisRuntime;
import galois.runtime.Iteration;
import galois.runtime.ReplayFeature;
import galois.runtime.wl.Priority;
import galois_scj.GaloisSCJComputation;
import galois_scj.GaloisSCJProcess;
import galois_scj.ReducedGaloisRuntime;
import galois_scj.UnorderedGaloisSCJComputation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import scj.Task;
import util.Launcher;
import util.SystemProperties;
import util.fn.FnIterable;
import util.fn.Lambda;
import util.fn.Lambda2;
import util.fn.Lambda2Void;
import util.fn.LambdaVoid;

public class SCJMain {
	protected static final int INFINITY = Integer.MAX_VALUE;

	private ObjectGraph<Node, Integer> graph;

	private boolean isSerial;

	private UnionFind uf;

	public static void main(String[] args) throws Exception {
		new SCJMain().run(args);
	}

	private void init(String[] args) throws IOException {
		this.graph = newGraphInstance();

		GraphGenerator.readIntegerEdgeGraph(args, this.graph, new Lambda<Integer, Node>() {
			@Override
			public Node call(Integer x) {
				return new Node();
			}
		});

		final Lambda2Void<GNode<Node>, GNode<Node>> body = new Lambda2Void<GNode<Node>, GNode<Node>>() {
			@Override
			public void call(GNode<Node> dst, GNode<Node> src) {
				Edge e = new Edge(src.getData(), dst.getData(), graph.getEdgeData(src, dst));
				src.getData().add(e);
			}
		};

		graph.map(new LambdaVoid<GNode<Node>>() {
			@Override
			public void call(GNode<Node> src) {
				src.map(body, src);
			}
		});
	}

	protected ObjectGraph<Node, Integer> newGraphInstance() {
		return new MorphGraph.ObjectGraphBuilder().create();
	}

	/**
	 * Calculate weight of MST using Prim's algorithm.
	 */
	 private long prims() {
		final Map<GNode<Node>, Integer> ordering = new HashMap<GNode<Node>, Integer>();
		Comparator<GNode<Node>> comparator = new PrimsComparator<Node>(ordering);
		final BinaryHeap<GNode<Node>> heap = new BinaryHeap<GNode<Node>>(graph.size(), comparator);
		final AtomicBoolean first = new AtomicBoolean(true);

		graph.map(new LambdaVoid<GNode<Node>>() {
			@Override
			public void call(GNode<Node> src) {
				if (first.get()) {
					ordering.put(src, 0);
					first.set(false);
				} else {
					ordering.put(src, INFINITY);
				}
				heap.add(src);
			}
		});

		// Straight out of CLRS
		final Map<GNode<Node>, GNode<Node>> parent = new HashMap<GNode<Node>, GNode<Node>>();
		final Lambda2Void<GNode<Node>, GNode<Node>> body = new Lambda2Void<GNode<Node>, GNode<Node>>() {
			@Override
			public void call(GNode<Node> dst, GNode<Node> src) {
				int w = graph.getEdgeData(src, dst);

				if (heap.contains(dst) && w < ordering.get(dst)) {
					parent.put(dst, src);

					// Update weight
					ordering.put(dst, w);
					heap.decreaseKey(dst);
				}
			}
		};

		while (!heap.isEmpty()) {
			GNode<Node> src = heap.poll();
			// Add outgoing edges to component
			src.map(body, src);
		}

		if (parent.size() != graph.size() - 1) {
			throw new Error("Gold standard did not generate a tree: " + parent.size() + " != " + (graph.size() - 1));
		}

		// Weigh tree
		long weight = 0;
		for (GNode<Node> key : parent.keySet()) {
			final Integer data = graph.getEdgeData(key, parent.get(key));
			weight += data;
		}
		return weight;
	 }

	 public final void run(String[] args) throws Exception {
		 //FullGaloisRuntime.initialize(2, false, false, ReplayFeature.Type.NO, false, false);
		 ReducedGaloisRuntime.initialize(false, false);
		 
		 init(args);

		 if (Launcher.getLauncher().isFirstRun()) {
			 System.err.println("Lonestar Benchmark Suite v3.0");
			 System.err.println("Copyright (C) 2007, 2008, 2009, 2010 The University of Texas at Austin");
			 System.err.println("http://iss.ices.utexas.edu/lonestar/");
			 System.err.println();
			 System.err.printf("application: Boruvka's algorithm (SCJ version)\n");
			 System.err.println("Compute the minimal spanning tree");
			 System.err.println("http://iss.ices.utexas.edu/lonestar/boruvka.html");
			 System.err.println();
			 System.err.println("number of threads: " + GaloisRuntime.getRuntime().getMaxThreads());
			 System.err.println();
		 }

		 uf = new UnionFind();

		 Launcher.getLauncher().startTiming();
		 
		 Task<Long> runBodyTask = new Task<Long>();
		 this.scjMainTask_runBody(runBodyTask);
		 long weight = runBodyTask.result();		 
		 Launcher.getLauncher().stopTiming();
		 System.out.println("Weight: " + weight);
		 if (Launcher.getLauncher().isFirstRun()) {
			 verify(weight);
		 }
	 }

	 public void scjMainTask_runBody(Task<Long> now) throws Exception {
		 final Bag<Integer> mst = new BagBuilder<Integer>().create();
		 isSerial = false;

		 // initial nodes in the workset
		 final List<Node> nodes = new ArrayList<Node>();
		 this.graph.map(new LambdaVoid<GNode<Node>>() {
			 @Override
			 public void call(GNode<Node> n) {
				 nodes.add(n.getData());
			 }
		 });

		 Task<Void> finishTask = new Task<Void>();
		 this.scjTask_finishBody(finishTask, now, mst);

		 GaloisSCJComputation<Node> computation = new UnorderedGaloisSCJComputation<Node>(Mappables.fromList(nodes), Priority.defaultOrder());
		 for(int i = 0; i < computation.getNumTasks(); i++) {
			 GaloisSCJProcess<Node> process = new GaloisSCJProcess<Node>(computation, i){

				 @Override
				 protected void body(Node item, ForeachContext<Node> context) {
					 runOnce(item, context, mst);
				 }

			 };

			 Task<Void> runOnceTask = new Task<Void>();
			 process.scjTask_process(runOnceTask);

			 runOnceTask.hb(finishTask);
		 }

	 }

	 public void scjTask_finishBody(Task<Void> now, Task<Long> result, Bag<Integer> mst) {
		 result.setResult(FnIterable.from(mst).reduce(new Lambda2<Long, Integer, Long>() {
			 @Override
			 public Long call(Long arg0, Integer arg1) {
				 return arg1 + arg0;
			 }
		 }, 0l));
	 }

	 protected int runOnce(Node n, ForeachContext<Node> ctx, Bag<Integer> mst) {		 
		 int dummy = 1;

		 Iteration it = isSerial ? null : Iteration.getCurrentIteration();
		 try {
			 final Node a = uf.find(it, ctx, n);

			 a.access(MethodFlag.CHECK_CONFLICT);

			 Edge lightest = null;
			 Node mate = null;

			 while ((lightest = a.poll()) != null) {
				 final Edge edge = lightest;

				 Node rep1 = uf.find(it, ctx, edge.getSource());
				 Node rep2 = uf.find(it, ctx, edge.getDest());

				 if (rep1 == a && rep2 == a) {
					 continue;
				 }

				 if (rep1 == a) {
					 mate = rep2;
				 } else {
					 mate = rep1;
				 }
				 break;
			 }

			 if (mate != null) {
				 mate.access(MethodFlag.CHECK_CONFLICT);
				 uf.union(it, ctx, a, mate);
				 Node winner = uf.find(it, ctx, a);

				 // FAILSAFE POINT
				 Node loser = (winner == a) ? mate : a;

				 winner.addAll(loser, MethodFlag.NONE);
				 loser.clear(MethodFlag.NONE);

				 mst.add(lightest.getWeight(), MethodFlag.NONE);
				 ctx.add(winner, MethodFlag.NONE);
			 }
		 } finally {
			 if (!isSerial)
				 uf.release(it, ctx);
		 }

		 return dummy;
	 }

	 private void verify(long result) {
		 Boolean fullVerify = SystemProperties.getBooleanProperty("verify.full", false);
		 long expected = SystemProperties.getLongProperty("verify.result", Long.MIN_VALUE);
		 if (fullVerify || expected == Long.MIN_VALUE) {
			 expected = prims();
		 }
		 if (expected == result) {
			 System.out.println("MST ok.");
		 } else {
			 throw new IllegalStateException("Inconsistent MSTs: " + expected + " != " + result);
		 }
	 }
}
