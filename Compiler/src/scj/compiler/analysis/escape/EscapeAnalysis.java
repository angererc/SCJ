package scj.compiler.analysis.escape;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public interface EscapeAnalysis {

	public void analyze();
	public boolean instanceMayEscape(CGNode thisTask, CGNode otherTask, InstanceKey instance);
}
