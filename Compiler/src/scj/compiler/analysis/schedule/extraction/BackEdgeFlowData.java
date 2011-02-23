package scj.compiler.analysis.schedule.extraction;

import com.ibm.wala.ssa.ISSABasicBlock;

public class BackEdgeFlowData extends EdgeFlowData {
	
	BackEdgeFlowData(ISSABasicBlock from, ISSABasicBlock to) {
		super(from, to);
	}
	
	@Override
	public String toString() {
		return "Back-" + super.toString();
	}
}
