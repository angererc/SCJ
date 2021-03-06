package scj.compiler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import scj.compiler.analysis.escape.EscapeAnalysis;
import scj.compiler.analysis.reachability.ReachabilityAnalysis;
import scj.compiler.analysis.rw_sets.BytecodeReadWriteSetsAnalysis;
import scj.compiler.analysis.rw_sets.ParallelReadWriteSetsAnalysis;
import scj.compiler.analysis.rw_sets.ReadWriteConflictDetector;
import scj.compiler.analysis.rw_sets.ReadWriteSet;
import scj.compiler.analysis.rw_sets.ReadWriteSetsAnalysis;
import scj.compiler.analysis.schedule.ScheduleAnalysis;
import scj.compiler.optimizing.OptimizingUtil;
import scj.compiler.wala.util.TaskForestCallGraph;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

public class OptimizingCompilation extends ScheduleSitesOnlyCompilation implements ReadWriteConflictDetector {

	private AnalysisOptions walaOptions;
	private CallGraph callGraph;
	private TaskForestCallGraph taskForestCallGraph;
	private Set<CGNode> taskForestCallGraphNodes; //lazily computed
	private Set<IMethod> taskForestMethods; //lazy
	private PointerAnalysis pointerAnalysis;

	private ScheduleAnalysis scheduleAnalysis;
	private EscapeAnalysis escapeAnalysis;
	private ReachabilityAnalysis reachabilityAnalysis;
	private ReadWriteSetsAnalysis rwSetsAnalysis;
	private ParallelReadWriteSetsAnalysis parRWSetsAnalysis;
	private BytecodeReadWriteSetsAnalysis bytecodeRWSetsAnalysis;

	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;
	private Set<CGNode> taskNodes; //lazily constructed
	
	protected OptimizingCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public void analyze() throws Exception {
		this.compilationStats.startTiming("Analysis");
		findConcreteTaskMethods();
		computeCallGraph();
		
		runReachabilityAnalysis();
		runReadWriteSetsAnalysis();
		runScheduleAnalysis();
		runEscapeAnalysis();
		runParallelReadWriteSetsAnalysis();
		runBytecodeReadWriteSetsAnalysis();
		this.compilationStats.stopTiming();
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
	public void prepareEmitCode() throws Exception {		
		super.prepareEmitCode();
		//make sure those are computed
		this.taskForestCallGraphNodes();
		this.taskForestMethods();
	}
	
	/* (non-Javadoc)
	 * @see scj.compiler.ReadWriteConflictDetector#readReadConflict(com.ibm.wala.classLoader.IMethod, java.lang.Integer)
	 */
	public boolean readReadConflict(IMethod method, Integer bytecode) {
		Set<CGNode> methodNodes = callGraph.getNodes(method.getReference());
		for(CGNode methodNode : methodNodes) {
			assert methodNode.getMethod().isNative()
			|| methodNode.getMethod().isSynthetic()
			|| bytecodeRWSetsAnalysis.containsReadWriteSet(methodNode, bytecode) : "Bytecode R/W sets analysis doesn't contain set for bytecode " + bytecode + " in node " + methodNode;
			ReadWriteSet rwSet = bytecodeRWSetsAnalysis.readWriteSet(methodNode, bytecode);
			ReadWriteSet parRWSet = parRWSetsAnalysis.nodeParallelReadWriteSet(methodNode);
			if(rwSet.readReadConflict(parRWSet))
				return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see scj.compiler.ReadWriteConflictDetector#readWriteConflict(com.ibm.wala.classLoader.IMethod, java.lang.Integer)
	 */
	public boolean readWriteConflict(IMethod method, Integer bytecode) {
		Set<CGNode> methodNodes = callGraph.getNodes(method.getReference());
		for(CGNode methodNode : methodNodes) {
			assert methodNode.getMethod().isNative()
			|| methodNode.getMethod().isSynthetic()
			|| bytecodeRWSetsAnalysis.containsReadWriteSet(methodNode, bytecode) : "Bytecode R/W sets analysis doesn't contain set for bytecode " + bytecode + " in node " + methodNode;
			ReadWriteSet rwSet = bytecodeRWSetsAnalysis.readWriteSet(methodNode, bytecode);
			ReadWriteSet parRWSet = parRWSetsAnalysis.nodeParallelReadWriteSet(methodNode);
			if(rwSet.readWriteConflict(parRWSet))
				return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see scj.compiler.ReadWriteConflictDetector#writeReadConflict(com.ibm.wala.classLoader.IMethod, java.lang.Integer)
	 */
	public boolean writeReadConflict(IMethod method, Integer bytecode) {
		Set<CGNode> methodNodes = callGraph.getNodes(method.getReference());
		for(CGNode methodNode : methodNodes) {
			assert methodNode.getMethod().isNative()
			|| methodNode.getMethod().isSynthetic() 
			|| bytecodeRWSetsAnalysis.containsReadWriteSet(methodNode, bytecode) : "Bytecode R/W sets analysis doesn't contain set for bytecode " + bytecode + " in node " + methodNode;
			ReadWriteSet rwSet = bytecodeRWSetsAnalysis.readWriteSet(methodNode, bytecode);
			ReadWriteSet parRWSet = parRWSetsAnalysis.nodeParallelReadWriteSet(methodNode);
			if(rwSet.writeReadConflict(parRWSet))
				return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see scj.compiler.ReadWriteConflictDetector#writeWriteConflict(com.ibm.wala.classLoader.IMethod, java.lang.Integer)
	 */
	public boolean writeWriteConflict(IMethod method, Integer bytecode) {
		Set<CGNode> methodNodes = callGraph.getNodes(method.getReference());
		for(CGNode methodNode : methodNodes) {
			assert methodNode.getMethod().isNative()
			|| methodNode.getMethod().isSynthetic()
			|| bytecodeRWSetsAnalysis.containsReadWriteSet(methodNode, bytecode) : "Bytecode R/W sets analysis doesn't contain set for bytecode " + bytecode + " in node " + methodNode;
			ReadWriteSet rwSet = bytecodeRWSetsAnalysis.readWriteSet(methodNode, bytecode);
			ReadWriteSet parRWSet = parRWSetsAnalysis.nodeParallelReadWriteSet(methodNode);
			if(rwSet.writeWriteConflict(parRWSet))
				return true;
		}
		return false;
	}
	
	private void rewriteBehavior(CtBehavior ctBehavior, IClass iclass) throws Exception {
		String mName = ctBehavior.getLongName();
		
		if(mName.startsWith("java.lang")) { //have to exclude the whole java.lang package; not sure why I can't just exclude the required classes; I don't find all of them i guess
			//System.err.println("OptimizingUtil: ignoring system method " + mName);
			return;
		}
		
		final IBytecodeMethod bcMethod = OptimizingUtil.ctBehaviorToIBytecodeMethod(ctBehavior, iclass);
		
		if(taskForestMethods.contains(bcMethod)) {
			//reachable method				
			OptimizingUtil.makeConflictingFieldAndArrayAccessesVolatile(this, compilationStats, ctBehavior, bcMethod);				
		} else {				
			//method should be unreachable
			int mods = ctBehavior.getModifiers();
			if(Modifier.isNative(mods) || Modifier.isAbstract(mods)) {
				return;
			}
			
			if(! mName.startsWith("java")) {
				System.err.println("OptimizingCompilation: instrumenting non-standard library method: " + mName + "; num nodes in CG: " + callGraph.getNodes(bcMethod.getReference()).size());
				
			//ctBehavior.insertBefore("System.err.println(\"Warning: Method " + mName + " was called even though it was considered unreachable by the analysis\");");
				OptimizingUtil.makeAllArrayAccessesVolatile(ctBehavior);
				OptimizingUtil.makeAllFieldAccessesVolatile(compilationStats, ctBehavior);
			}
		}
	}
	
	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {		
		OptimizingUtil.markAllNonStaticFieldsNotVolatileAndStaticFieldsVolatile(ctclass);
		
		for(CtConstructor ctConstructor : ctclass.getDeclaredConstructors()) {
			this.rewriteBehavior(ctConstructor, iclass);
		}
		for(CtMethod ctMethod : ctclass.getDeclaredMethods()) {
			this.rewriteBehavior(ctMethod, iclass);
		}
		
		//do it in the end because rewriting changes the bytecode indices and that screws up our analysis data
		super.rewrite(iclass, ctclass);		
	}

	public void runScheduleAnalysis() {
		this.compilationStats.startTiming("Schedule Analysis");
		getOrCreateScheduleAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public void runEscapeAnalysis() {
		this.compilationStats.startTiming("Escape Analysis");
		getOrCreateEscapeAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public void runReachabilityAnalysis() {
		this.compilationStats.startTiming("Reachability Analysis");		
		getOrCreateReachabilityAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public void runReadWriteSetsAnalysis() {
		this.compilationStats.startTiming("Read Write Sets Analysis");
		getOrCreateReadWriteSetsAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public void runParallelReadWriteSetsAnalysis() {
		this.compilationStats.startTiming("Parallel Read Write Sets Analysis");		
		getOrCreateParallelReadWriteSetsAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public void runBytecodeReadWriteSetsAnalysis() {
		this.compilationStats.startTiming("Bytecode Read Write Sets Analysis");
		getOrCreateBytecodeReadWriteSetsAnalysis().analyze();
		this.compilationStats.stopTiming();
	}

	public ScheduleAnalysis getOrCreateScheduleAnalysis() {
		if(this.scheduleAnalysis == null) {
			this.scheduleAnalysis = compilerOptions.createScheduleAnalysis(this);
		}
		return this.scheduleAnalysis;
	}

	public EscapeAnalysis getOrCreateEscapeAnalysis() {
		if(this.escapeAnalysis == null) {
			this.escapeAnalysis = compilerOptions.createEscapeAnalysis(this);
		}
		return this.escapeAnalysis;
	}

	public ReachabilityAnalysis getOrCreateReachabilityAnalysis() {
		if(this.reachabilityAnalysis == null) {
			reachabilityAnalysis = new ReachabilityAnalysis(this);
		}
		return this.reachabilityAnalysis;
	}

	public ReadWriteSetsAnalysis getOrCreateReadWriteSetsAnalysis() {
		if(this.rwSetsAnalysis == null) {
			rwSetsAnalysis = new ReadWriteSetsAnalysis(this);
		}
		return this.rwSetsAnalysis;
	}

	public ParallelReadWriteSetsAnalysis getOrCreateParallelReadWriteSetsAnalysis() {
		if(this.parRWSetsAnalysis == null) {
			this.parRWSetsAnalysis = new ParallelReadWriteSetsAnalysis(this);
		}
		return this.parRWSetsAnalysis;
	}

	public BytecodeReadWriteSetsAnalysis getOrCreateBytecodeReadWriteSetsAnalysis() {
		if(this.bytecodeRWSetsAnalysis == null) {
			this.bytecodeRWSetsAnalysis = new BytecodeReadWriteSetsAnalysis(this);
		}
		return this.bytecodeRWSetsAnalysis;
	}

	public ScheduleAnalysis scheduleAnalysis() {		
		return this.scheduleAnalysis;
	}

	public EscapeAnalysis escapeAnalysis() {		
		return this.escapeAnalysis;
	}

	public ReachabilityAnalysis reachabilityAnalysis() {
		return this.reachabilityAnalysis;
	}

	public ReadWriteSetsAnalysis readWriteSetsAnalysis() {
		return this.rwSetsAnalysis;
	}

	public ParallelReadWriteSetsAnalysis parallelReadWriteSetsAnalysis() {
		return this.parRWSetsAnalysis;
	}

	public BytecodeReadWriteSetsAnalysis conflictingBytecodeReadWriteSetsAnalysis() {
		return this.bytecodeRWSetsAnalysis;
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}

	public void computeCallGraph() {
		this.compilationStats.startTiming("Call Graph Computation");
		assert(callGraph == null);
		//
		this.walaOptions = new AnalysisOptions(scope(), entrypoints());

		CallGraphBuilder builder = compilerOptions.createCallGraphBuilder(walaOptions, cache(), scope(), classHierarchy());
		try {
			callGraph = builder.makeCallGraph(walaOptions, null);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		pointerAnalysis = builder.getPointerAnalysis();

		taskForestCallGraph = TaskForestCallGraph.make(callGraph, this.allTaskNodes());
		this.compilationStats.stopTiming();
	}

	public CallGraph callGraph() {
		assert callGraph != null;
		return this.callGraph;
	}

	public TaskForestCallGraph taskForestCallGraph() {
		return this.taskForestCallGraph;
	}
		
	public Set<CGNode> taskForestCallGraphNodes() {
		if(taskForestCallGraphNodes != null)
			return taskForestCallGraphNodes;
		
		taskForestCallGraphNodes = new HashSet<CGNode>();
		for(CGNode node : taskForestCallGraph) {
			taskForestCallGraphNodes.add(node);
		}
		return taskForestCallGraphNodes;
	}
	
	public Set<IMethod> taskForestMethods() {
		if(this.taskForestMethods != null)
			return this.taskForestMethods;
		
		taskForestMethods = new HashSet<IMethod>();
		for(CGNode node : this.taskForestCallGraphNodes()) {
			taskForestMethods.add(node.getMethod());
		}
		return taskForestMethods;
	}

	
	public PointerAnalysis pointerAnalysis() {
		assert callGraph != null;
		return pointerAnalysis;
	}

	public Set<IMethod> allConcreteTaskMethods() {
		return this.taskMethods;
	}

	public Set<CGNode> allTaskNodes() {
		if(this.taskNodes != null) {
			return this.taskNodes;
		}
		this.taskNodes = new HashSet<CGNode>();
		CallGraph callGraph = this.callGraph();
		for(IMethod taskMethod : this.allConcreteTaskMethods()) {
			taskNodes.addAll(callGraph.getNodes(taskMethod.getReference()));
		}
		taskNodes.add(callGraph.getFakeRootNode());
		return taskNodes;
	}

	public Set<IMethod> mainTaskMethods() {
		return this.mainTaskMethods;
	}

	public void findConcreteTaskMethods() {
		System.out.println("Finding task methods");
		this.taskMethods = new HashSet<IMethod>();
		this.mainTaskMethods = new HashSet<IMethod>();
		ClassHierarchy classHierarchy = classHierarchy();
		Iterator<IClass> classes = classHierarchy.iterator();
		while(classes.hasNext()) {			
			IClass clazz = classes.next();
			//System.out.println("\tfound class: " + clazz.getName());
			//we don't have to look in the standard library because they don't have any task methods
			if( ! clazz.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
				for(IMethod method : clazz.getDeclaredMethods()) {
					if(WalaConstants.isNormalOrMainTaskMethod(method.getReference()) && ! method.isAbstract()) {
						taskMethods.add(method);
						if(WalaConstants.isMainTaskMethod(method.getReference())) {
							mainTaskMethods.add(method);
						}
					}
				}
			}
		}

	}

}
