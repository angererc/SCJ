package scj.compiler.analysis.reachability;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import scj.compiler.OptimizingCompilation;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.traverse.DFS;

public class ReachabilityAnalysis {

	private final OptimizingCompilation compiler;
	private HashMap<CGNode, Set<CGNode>> reachableNodesByTaskNode;
	private HashMap<CGNode, Set<CGNode>> reachingTaskNodesByNode;

	public ReachabilityAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	private Collection<CGNode> reachableNodesForTaskNode(CGNode taskNode) {
		return DFS.getReachableNodes(compiler.taskForestCallGraph(), Collections.singleton(taskNode), new Filter<CGNode>(){
			@Override
			public boolean accepts(CGNode o) {				
				return ! WalaConstants.isNormalOrMainTaskMethod(o.getMethod().getReference()); 				
			}			
		});
	}
	public void analyze() {
		System.out.println("\treachability analysis: running allPairsShortestPaths()");
		
		reachableNodesByTaskNode = new HashMap<CGNode, Set<CGNode>>();
		reachingTaskNodesByNode = new HashMap<CGNode, Set<CGNode>>();

		for(CGNode taskNode : compiler.allTaskNodes()) {
			Collection<CGNode> reachableNodes = reachableNodesForTaskNode(taskNode);
			System.out.println("\tReachability: found " + reachableNodes.size() + " nodes reachable from " + taskNode);
			
			assert reachableNodes.contains(taskNode);			
			for(CGNode reachedNode : reachableNodes) {
				this.taskReachesNode(taskNode, reachedNode);
			}
		}
	}

	private void taskReachesNode(CGNode task, CGNode node) {
		assert task == node || !WalaConstants.isNormalOrMainTaskMethod(node.getMethod().getReference());
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

}
