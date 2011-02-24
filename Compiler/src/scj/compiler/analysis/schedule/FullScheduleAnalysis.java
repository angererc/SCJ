package scj.compiler.analysis.schedule;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSACache;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;

public class FullScheduleAnalysis implements ScheduleAnalysis {

	private OptimizingCompilation compiler;
	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;
	private CallGraph callGraph;
	private HashMap<IMethod, TaskSchedule<Integer, WalaTaskScheduleManager>> taskSchedulesByMethod;
	private AnalysisSession<CGNode, Integer, WalaTaskScheduleManager> session;
	
	public FullScheduleAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}
	
	public Set<IMethod> taskMethods() {
		return this.taskMethods;
	}

	public Set<IMethod> mainTaskMethods() {
		return this.mainTaskMethods;
	}

	public TaskSchedule<Integer, ?> taskScheduleForTaskMethod(IMethod method) {
		return this.taskSchedulesByMethod.get(method);
	}
	
	@Override
	public void analyze() {
		findTaskMethods();
		computeTaskSchedules();
		populateAnalysisSession();
		runAnalysisOnMainTaskMethods();
	}
	
	public void findTaskMethods() {
		System.out.println("Finding task methods");
		this.taskMethods = new HashSet<IMethod>();
		this.mainTaskMethods = new HashSet<IMethod>();
		ClassHierarchy classHierarchy = compiler.classHierarchy();
		Iterator<IClass> classes = classHierarchy.iterator();
		while(classes.hasNext()) {
			IClass clazz = classes.next();
			//we don't have to look in the standard library because they don't have any task methods
			if( ! clazz.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
				for(IMethod method : clazz.getDeclaredMethods()) {
					if(WalaConstants.isTaskMethod(method.getReference())) {
						taskMethods.add(method);
						if(WalaConstants.isMainTaskMethod(method.getReference())) {
							mainTaskMethods.add(method);
						}
					}
				}
			}
		}
	}

	public void computeTaskSchedules() {
		System.out.println("Computing task schedules");
		this.taskSchedulesByMethod = new HashMap<IMethod, TaskSchedule<Integer, WalaTaskScheduleManager>>();
		//
		SSACache ssaCache = compiler.cache().getSSACache();
		for(IMethod taskMethod : taskMethods) {
			TaskSchedule<Integer, WalaTaskScheduleManager> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(ssaCache, compiler.irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
		//
		for(IMethod taskMethod : mainTaskMethods) {
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
		for(IMethod mainTaskMethod : mainTaskMethods) {
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
