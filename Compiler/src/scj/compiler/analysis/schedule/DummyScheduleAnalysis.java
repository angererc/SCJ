package scj.compiler.analysis.schedule;

import java.util.HashSet;
import java.util.Set;

import scj.compiler.OptimizingCompilation;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;

//a schedule analysis that considers all tasks parallel
//used when the compiler is configured to not use a schedule analysis
public class DummyScheduleAnalysis implements ScheduleAnalysis {

	private OptimizingCompilation compiler;
	
	public DummyScheduleAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}
	
	@Override
	public void analyze() {
		
	}

	@Override
	public boolean isOrdered(CGNode one, CGNode other) {
		return false;
	}

	@Override
	public boolean isParallel(CGNode one, CGNode other) {
		return true;
	}

	@Override
	public boolean isOrdered(IMethod one, IMethod other) {
		return false;
	}

	@Override
	public boolean isParallel(IMethod one, IMethod other) {
		return true;
	}

	private Set<CGNode> allNodes;
	@Override
	public Set<CGNode> parallelTaskFor(CGNode _) {
		if(allNodes == null) {
			allNodes = new HashSet<CGNode>();
			for(CGNode node : compiler.callGraph()) {
				allNodes.add(node);
			}
		}
		return allNodes;
	}

	@Override
	public Set<IMethod> parallelTasksFor(IMethod method) {
		return compiler.allTaskMethods();
	}
}
