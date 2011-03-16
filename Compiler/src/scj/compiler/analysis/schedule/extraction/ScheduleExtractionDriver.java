package scj.compiler.analysis.schedule.extraction;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACache;


public class ScheduleExtractionDriver {
	
	private ScheduleExtractionDriver() {
	}
	
	public static NormalNodeFlowData computeNodeFlowData(IR ir) {
		NormalNodeFlowData flowData = TaskScheduleSolver.solve(ir);
		return flowData;
	}

	public static TaskSchedule<Integer, WalaTaskScheduleManager> extractTaskSchedule(NormalNodeFlowData flowData, SSACache ssaCache, IR ir) {
		//System.out.println("Extracting schedule from method " + ir.getMethod());
		WalaTaskScheduleManager manager = WalaTaskScheduleManager.make(ssaCache, ir, flowData);
		TaskSchedule<Integer, WalaTaskScheduleManager> taskSchedule = new TaskSchedule<Integer, WalaTaskScheduleManager>(ir.getMethod().getName().toString(), manager);
		//System.out.println("Done extracting schedule from method " + ir.getMethod());
		return taskSchedule;
	}
	
	public static TaskSchedule<Integer, WalaTaskScheduleManager> extractTaskSchedule(SSACache ssaCache, IR ir) {
		return extractTaskSchedule(computeNodeFlowData(ir), ssaCache, ir);
	}
}
