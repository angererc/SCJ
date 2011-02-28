package scj.compiler.analysis.schedule;

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

	@Override
	public Set<CGNode> parallelTasksFor(CGNode _) {
		return compiler.allTaskNodes();		
	}

	@Override
	public Set<IMethod> parallelTasksFor(IMethod method) {
		return compiler.allTaskMethods();
	}
}
