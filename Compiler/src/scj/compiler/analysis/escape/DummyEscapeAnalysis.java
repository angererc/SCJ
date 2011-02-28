package scj.compiler.analysis.escape;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public class DummyEscapeAnalysis implements EscapeAnalysis {

	@Override
	public boolean escapes(InstanceKey key) {
		return true;
	}
	
	@Override
	public void analyze() {
		
	}

}
