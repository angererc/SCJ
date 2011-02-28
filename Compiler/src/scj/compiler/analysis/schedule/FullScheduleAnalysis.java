package scj.compiler.analysis.schedule;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.schedule.core.AnalysisResult;
import scj.compiler.analysis.schedule.core.AnalysisSession;
import scj.compiler.analysis.schedule.core.AnalysisTask;
import scj.compiler.analysis.schedule.core.AnalysisTaskResolver;
import scj.compiler.analysis.schedule.extraction.ScheduleExtractionDriver;
import scj.compiler.analysis.schedule.extraction.TaskSchedule;
import scj.compiler.analysis.schedule.extraction.WalaTaskScheduleManager;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSACache;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class FullScheduleAnalysis implements ScheduleAnalysis {

	private OptimizingCompilation compiler;

	private CallGraph callGraph;
	private HashMap<IMethod, TaskSchedule<Integer, WalaTaskScheduleManager>> taskSchedulesByMethod;
	private AnalysisSession<CGNode, Integer, WalaTaskScheduleManager> session;

	private AnalysisResult<CGNode> result;
	private AnalysisResult<IMethod> collapsedResult;

	public FullScheduleAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	public TaskSchedule<Integer, ?> taskScheduleForTaskMethod(IMethod method) {
		return this.taskSchedulesByMethod.get(method);
	}

	@Override
	public void analyze() {
		computeTaskSchedules();
		populateAnalysisSession();
		result = runAnalysisOnMainTaskMethods();

		session = null;
		callGraph = null;
		taskSchedulesByMethod = null;
	}

	@Override
	public boolean isOrdered(CGNode one, CGNode other) {
		return result.isOrdered(one, other);
	}

	@Override
	public boolean isParallel(CGNode one, CGNode other) {
		return result.isParallel(one, other);
	}

	public AnalysisResult<IMethod> collapsedResult() {
		if(collapsedResult == null) {
			collapsedResult = this.result.collapse(new AnalysisResult.MappingOperation<CGNode, IMethod>() {
				@Override
				public IMethod map(CGNode i) {
					return i.getMethod();
				}		
			});
		}
		return collapsedResult;
	}
	
	@Override
	public boolean isOrdered(IMethod one, IMethod other) {
		return collapsedResult.isOrdered(one, other);
	}
	
	@Override
	public boolean isParallel(IMethod one, IMethod other) {
		return collapsedResult.isParallel(one, other);
	}

	public Set<CGNode> parallelTasksFor(CGNode node) {
		return result.parallelTasksFor(node);
	}
	
	public Set<IMethod> parallelTasksFor(IMethod method) {
		assert WalaConstants.isNormalOrMainTaskMethod(method.getReference());
		return collapsedResult().parallelTasksFor(method);
	}
	
	public void computeTaskSchedules() {
		System.out.println("Computing task schedules");
		this.taskSchedulesByMethod = new HashMap<IMethod, TaskSchedule<Integer, WalaTaskScheduleManager>>();
		//
		SSACache ssaCache = compiler.cache().getSSACache();
		for(IMethod taskMethod : compiler.allTaskMethods()) {
			TaskSchedule<Integer, WalaTaskScheduleManager> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(ssaCache, compiler.irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
	}

	public void populateAnalysisSession() {
		System.out.println("Populating schedule analysis session");
		session = new AnalysisSession<CGNode, Integer, WalaTaskScheduleManager>();
		CallGraph cg = compiler.callGraph();

		for(Entry<IMethod, TaskSchedule<Integer, WalaTaskScheduleManager>> entry : this.taskSchedulesByMethod.entrySet()) {
			IMethod taskMethod = entry.getKey();
			TaskSchedule<Integer, WalaTaskScheduleManager> taskSchedule = entry.getValue();

			for(CGNode node : cg.getNodes(taskMethod.getReference())) {
				session.createTask(node, taskSchedule);
			}			
		}

	}

	public AnalysisResult<CGNode> runAnalysisOnMainTaskMethods() {
		System.out.println("Running schedule analysis on main task methods.");
		callGraph = compiler.callGraph();
		AnalysisTaskResolver<CGNode, Integer, WalaTaskScheduleManager> resolver = createResolver();

		AnalysisResult<CGNode> result = new AnalysisResult<CGNode>();
		//now solve the analysis for each main task
		for(IMethod mainTaskMethod : compiler.mainTaskMethods()) {
			for(CGNode node : callGraph.getNodes(mainTaskMethod.getReference())) {
				AnalysisTask<CGNode, Integer, WalaTaskScheduleManager> task = session.taskForID(node);

				AnalysisResult<CGNode> analysisResult = task.solveAsRoot(resolver);			
				result.mergeWith(analysisResult);
			}
		}
		return result;
	}

	public AnalysisTaskResolver<CGNode, Integer, WalaTaskScheduleManager> createResolver() {
		return new AnalysisTaskResolver<CGNode, Integer, WalaTaskScheduleManager>() {
			@Override
			public Collection<CGNode> possibleTargetTasksForSite(
					AnalysisTask<CGNode, Integer, WalaTaskScheduleManager> task,
					Integer scheduleNode) {
				WalaTaskScheduleManager manager = task.taskSchedule().taskScheduleManager();
				SSAInvokeInstruction invoke = manager.scheduleSiteForNode(scheduleNode);
				return callGraph.getPossibleTargets(task.id, invoke.getCallSite());
			}
		};
	}
}
