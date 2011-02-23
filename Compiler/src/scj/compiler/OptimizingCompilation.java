package scj.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.analysis.schedule.TaskSchedule;
import scj.compiler.analysis.schedule.extraction.ScheduleExtractionDriver;
import scj.compiler.wala.util.WalaConstants;

import javassist.CtClass;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

public class OptimizingCompilation extends CompilationDriver {

	private AnalysisOptions walaOptions;
	private CallGraph cg;
	private PointerAnalysis pointerAnalysis;

	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;
	private HashMap<IMethod, TaskSchedule<Integer, ?>> taskSchedulesByMethod;

	protected OptimizingCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public void analyze() throws Exception {		
		findTaskMethods();
		computeTaskSchedules();
		computeCallGraph();
	}

	@Override
	public String prefix() {
		String[] spec = compilerOptions.optimizationLevel();

		StringBuffer result = new StringBuffer();
		if (spec.length > 0) {
			result.append(spec[0]);
			for (int i=1; i<spec.length; i++) {
				result.append("_");
				result.append(spec[i]);
			}
		}
		return result.toString();
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
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

	public void computeCallGraph() {
		System.out.println("Computing call graph");
		assert(cg == null);
		//
		this.walaOptions = new AnalysisOptions(scope(), entrypoints());
		
		CallGraphBuilder builder = compilerOptions.createCallGraphBuilder(walaOptions, cache(), scope(), classHierarchy());
		try {
			cg = builder.makeCallGraph(walaOptions, null);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		pointerAnalysis = builder.getPointerAnalysis();
	}

	public CallGraph callGraph() {
		assert cg != null;
		return this.cg;
	}

	public PointerAnalysis pointerAnalysis() {
		assert cg != null;
		return pointerAnalysis;
	}


	public void findTaskMethods() {
		System.out.println("Finding task methods");
		this.taskMethods = new HashSet<IMethod>();
		this.mainTaskMethods = new HashSet<IMethod>();
		ClassHierarchy classHierarchy = this.classHierarchy();
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
		for(IMethod taskMethod : taskMethods) {
			TaskSchedule<Integer, ?> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(cache().getSSACache(), irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
		//
		for(IMethod taskMethod : mainTaskMethods) {
			TaskSchedule<Integer, ?> taskSchedule = ScheduleExtractionDriver.extractTaskSchedule(cache().getSSACache(), irForMethod(taskMethod));
			this.taskSchedulesByMethod.put(taskMethod, taskSchedule);	
		}
	}
}
