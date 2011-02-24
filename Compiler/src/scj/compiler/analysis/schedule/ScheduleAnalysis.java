package scj.compiler.analysis.schedule;

import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;

public interface ScheduleAnalysis {

	public void analyze();
	
	public boolean isOrdered(CGNode one, CGNode other);
	public boolean isParallel(CGNode one, CGNode other);
	
	public boolean isOrdered(IMethod one, IMethod other);
	public boolean isParallel(IMethod one, IMethod other);

	public Set<CGNode> parallelTaskFor(CGNode node);
	
	public Set<IMethod> parallelTasksFor(IMethod method);
}
