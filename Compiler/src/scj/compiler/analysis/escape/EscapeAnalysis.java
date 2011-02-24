package scj.compiler.analysis.escape;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public interface EscapeAnalysis {

	public void run();
	public boolean escapes(InstanceKey key);
}
