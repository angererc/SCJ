package scj.compiler.analysis.schedule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.schedule.extraction.ScheduleExtractionDriver;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSACache;
import com.ibm.wala.types.ClassLoaderReference;

public class FullScheduleAnalysis implements ScheduleAnalysis {

	private OptimizingCompilation compiler;
	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;
	private HashMap<IMethod, TaskSchedule<Integer, ?>> taskSchedulesByMethod;
	
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
		this.taskSchedulesByMethod = new HashMap<IMethod, TaskSchedule<Integer, ?>>();
		//
		SSACache ssaCache = compiler.cache().getSSACache();
		for(IMethod taskMethod : taskMethods) {
			TaskSchedule<Integer, ?> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(ssaCache, compiler.irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
		//
		for(IMethod taskMethod : mainTaskMethods) {
			TaskSchedule<Integer, ?> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(ssaCache, compiler.irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
	}
}
