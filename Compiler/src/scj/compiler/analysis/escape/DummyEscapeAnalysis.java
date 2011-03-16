package scj.compiler.analysis.escape;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public class DummyEscapeAnalysis implements EscapeAnalysis {
	
	@Override
	public void analyze() {
		
	}
	
	@Override
	public boolean instanceMayEscape(CGNode thisTask, CGNode otherTask, InstanceKey instance) {
		return true;
	}

}
