package scj.compiler.analysis.escape;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public interface EscapeAnalysis {

	public void analyze();
	public boolean escapes(InstanceKey key);
}
