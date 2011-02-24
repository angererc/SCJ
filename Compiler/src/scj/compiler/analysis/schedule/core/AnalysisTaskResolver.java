package scj.compiler.analysis.schedule.core;

import java.util.Collection;

import scj.compiler.analysis.schedule.extraction.TaskScheduleManager;

public interface AnalysisTaskResolver<Instance, TV, SM extends TaskScheduleManager<TV>> {
	Collection<Instance> possibleTargetTasksForSite(AnalysisTask<Instance, TV, SM> task, TV scheduleNode); 
}
