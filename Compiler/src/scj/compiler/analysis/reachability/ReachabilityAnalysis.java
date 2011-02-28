package scj.compiler.analysis.reachability;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import scj.compiler.OptimizingCompilation;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.graph.traverse.FloydWarshall;

public class ReachabilityAnalysis {

	private final OptimizingCompilation compiler;
	private HashMap<CGNode, Set<CGNode>> reachableNodesByTaskNode;
	private HashMap<CGNode, Set<CGNode>> reachingTaskNodesByNode;

	public ReachabilityAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	public void analyze() {
		int[][] paths = new FW().allPairsShortestPaths();

		reachableNodesByTaskNode = new HashMap<CGNode, Set<CGNode>>();
		reachingTaskNodesByNode = new HashMap<CGNode, Set<CGNode>>();

		CallGraph callGraph = compiler.callGraph();

		for(CGNode taskNode : compiler.allTaskNodes()) {
			int nodeID = taskNode.getGraphNodeId();
			for(int i = 0; i < paths.length; i++) {
				int path = paths[nodeID][i];
				assert nodeID != i || path != Integer.MAX_VALUE : "a node should reach itself";
				if(path != Integer.MAX_VALUE) {
					CGNode reachedNode = callGraph.getNode(i);
					this.taskReachesNode(taskNode, reachedNode);
				}
			}
		}
	}

	private void taskReachesNode(CGNode task, CGNode node) {
		Set<CGNode> set = this.reachableNodesByTaskNode.get(task);
		if(set == null) {
			set = new HashSet<CGNode>();
			this.reachableNodesByTaskNode.put(task, set);
		}
		set.add(node);

		set = this.reachingTaskNodesByNode.get(node);
		if(set == null) {
			set = new HashSet<CGNode>();
			this.reachingTaskNodesByNode.put(node, set);
		}
		set.add(task);
	}

	public Set<CGNode> reachableNodes(CGNode taskNode) {
		Set<CGNode> result = this.reachableNodesByTaskNode.get(taskNode);
		if(result == null) {
			return Collections.emptySet();
		} else {
			return result;
		}
	}

	public Set<CGNode> reachingTasks(CGNode node) {
		Set<CGNode> result = this.reachingTaskNodesByNode.get(node);
		if(result == null) {
			return Collections.emptySet();
		} else {
			return result;
		}
	}

	private class FW extends FloydWarshall<CGNode> {
		private final CallGraph callGraph;
		public FW() {
			super(compiler.callGraph());
			this.callGraph = compiler.callGraph();
		}

		@Override
		protected int edgeCost(int from, int to) {
			IMethod toMethod = callGraph.getNode(to).getMethod();
			if(WalaConstants.isNormalOrMainTaskMethod(toMethod.getReference())) {
				return Integer.MAX_VALUE;
			} else {
				return 1;
			}
		}


	}
}
