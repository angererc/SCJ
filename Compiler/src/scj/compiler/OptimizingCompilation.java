package scj.compiler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.analysis.conflicting_bytecodes.ConflictingBytecodesAnalysis;
import scj.compiler.analysis.escape.EscapeAnalysis;
import scj.compiler.analysis.reachability.ReachabilityAnalysis;
import scj.compiler.analysis.rw_sets.ParallelReadWriteSetsAnalysis;
import scj.compiler.analysis.rw_sets.ReadWriteSetsAnalysis;
import scj.compiler.analysis.schedule.ScheduleAnalysis;
import scj.compiler.wala.util.TaskForestCallGraph;
import scj.compiler.wala.util.WalaConstants;
import sun.misc.Unsafe;

import javassist.CannotCompileException;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

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
import com.ibm.wala.types.Selector;

public class OptimizingCompilation extends CompilationDriver {

	private AnalysisOptions walaOptions;
	private CallGraph callGraph;
	private TaskForestCallGraph taskForestCallGraph;
	private PointerAnalysis pointerAnalysis;

	private ScheduleAnalysis scheduleAnalysis;
	private EscapeAnalysis escapeAnalysis;
	private ReachabilityAnalysis reachabilityAnalysis;
	private ReadWriteSetsAnalysis rwSetsAnalysis;
	private ParallelReadWriteSetsAnalysis parRWSetsAnalysis;
	private ConflictingBytecodesAnalysis conflictingBytecodesAnalysis;
	
	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;
	private Set<CGNode> taskNodes; //lazily constructed
	
	private CtClass scjRuntimeClass;
	private CodeConverter converter;

	protected OptimizingCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public void analyze() throws Exception {
		findTaskMethods();
		computeCallGraph();

		runScheduleAnalysis();
		runEscapeAnalysis();
		runReachabilityAnalysis();
		runReadWriteSetsAnalysis();
		runParallelReadWriteSetsAnalysis();
		runConflictingBytecodesAnalysis();
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
		scjRuntimeClass = classPool.get("scj.Runtime");

		converter = new CodeConverter();
		converter.replaceArrayAccess(scjRuntimeClass, new CodeConverter.DefaultArrayAccessReplacementMethodNames());

	}

	private Field getField(Class<?> clazz, String fieldName) {
		Field[] fields = clazz.getDeclaredFields();
		for(int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if(f.getName().equals(fieldName))
				return f;
		}
		
		Class<?> superClazz = clazz.getSuperclass();
		if(superClazz != null) {
			return getField(superClazz, fieldName);
		} else {
			Exception e = new NoSuchFieldException(fieldName);
			throw new RuntimeException(e);
		}
	}
	
	private long fieldIndex(Field field) {
		long index;
		Unsafe unsafe = scj.Runtime.unsafe;
		if(Modifier.isStatic(field.getModifiers())) {
			index = unsafe.staticFieldOffset(field);
		} else {
			index = unsafe.objectFieldOffset(field);
		}	
		return index;
	}
	
	private Class<?> runtimeClassForName(String className) {
		Class<?> runtimeClass;
		try {
			runtimeClass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return runtimeClass;
	}
	
	private IBytecodeMethod iMethod(CtMethod ctMethod, IClass iclass) {
		String signature = ctMethod.getSignature();
		Selector selector = Selector.make(ctMethod.getName() + signature);
		IBytecodeMethod iMethod = (IBytecodeMethod)iclass.getMethod(selector);
		//System.out.println(iMethod + "; name=" + ctMethod.getName() + "; signature=" + signature + "; selector=" + selector);
		assert iMethod != null;
		return iMethod;
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		//change all fields to not be volatile
		for(CtField field : ctclass.getDeclaredFields()) {
			int mods = field.getModifiers();
			if(Modifier.isVolatile(mods)) {
				int newMods = mods & ~Modifier.VOLATILE;				
				field.setModifiers(newMods);
			}

		}
		
		//rewrite array accesses to call the Runtime array accessors
		for(CtMethod ctMethod : ctclass.getDeclaredMethods()) {
			final IBytecodeMethod iMethod = iMethod(ctMethod, iclass);
			
			//
			ctMethod.instrument(new ExprEditor() {

				@Override
				public void edit(FieldAccess f) throws CannotCompileException {
					int bcIndex = f.indexOfBytecode();
					
					String fieldsClassName = f.getClassName();
					Class<?> runtimeClass = runtimeClassForName(fieldsClassName);
					assert runtimeClass != null;
					
					String fieldName = f.getFieldName();
					Field field = getField(runtimeClass, fieldName);					
					
					if(f.isReader()) {
						if(conflictingBytecodesAnalysis.hasParallelWriteConflict(iMethod, bcIndex)) {
							System.out.println("editing field read " + fieldName + " in class " + runtimeClass);
							long index = fieldIndex(field);						
							System.out.println("Field " + fieldName + " has index " + index + " in class " + runtimeClass);
							f.replace("$_ = scj.Runtime.getDoubleVolatile((Object)$0, " + index + "l);");
						}
					} else {
						assert f.isWriter();
						if(conflictingBytecodesAnalysis.hasParallelReadConflict(iMethod, bcIndex) || conflictingBytecodesAnalysis.hasParallelWriteConflict(iMethod, bcIndex)) {
							System.out.println("editing field write " + fieldName + " in class " + runtimeClass);
						}
					}					
				}

			});

			if(ctclass.getName().startsWith("testclasses")) {
				ctMethod.instrument(converter);
			}
		}
	}

	public void runScheduleAnalysis() {
		System.out.println("running schedule analysis");
		getOrCreateScheduleAnalysis().analyze();
	}

	public void runEscapeAnalysis() {
		System.out.println("running escape analysis");
		getOrCreateEscapeAnalysis().analyze();
	}
	
	public void runReachabilityAnalysis() {
		System.out.println("running reachability analysis");
		getOrCreateReachabilityAnalysis().analyze();
	}
	
	public void runReadWriteSetsAnalysis() {
		System.out.println("running read/write sets analysis");
		getOrCreateReadWriteSetsAnalysis().analyze();
	}
	
	public void runParallelReadWriteSetsAnalysis() {
		System.out.println("running parallel read/write sets analysis");
		getOrCreateParallelReadWriteSetsAnalysis().analyze();
	}

	public void runConflictingBytecodesAnalysis() {
		System.out.println("running conflicting bytecodes analysis");
		getOrCreateConflictingBytecodesAnalysis().analyze();
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
	
	public ConflictingBytecodesAnalysis getOrCreateConflictingBytecodesAnalysis() {
		if(this.conflictingBytecodesAnalysis == null) {
			this.conflictingBytecodesAnalysis = new ConflictingBytecodesAnalysis(this);
		}
		return this.conflictingBytecodesAnalysis;
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}

	public void computeCallGraph() {
		System.out.println("Computing call graph");
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
	}

	public CallGraph callGraph() {
		assert callGraph != null;
		return this.callGraph;
	}

	public TaskForestCallGraph taskForestCallGraph() {
		return this.taskForestCallGraph;
	}
	public PointerAnalysis pointerAnalysis() {
		assert callGraph != null;
		return pointerAnalysis;
	}

	public Set<IMethod> allTaskMethods() {
		return this.taskMethods;
	}
	
	public Set<CGNode> allTaskNodes() {
		if(this.taskNodes != null) {
			return this.taskNodes;
		}
		this.taskNodes = new HashSet<CGNode>();
		CallGraph callGraph = this.callGraph();
		for(IMethod taskMethod : this.allTaskMethods()) {
			taskNodes.addAll(callGraph.getNodes(taskMethod.getReference()));
		}
		return taskNodes;
	}

	public Set<IMethod> mainTaskMethods() {
		return this.mainTaskMethods;
	}

	public void findTaskMethods() {
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
					if(WalaConstants.isNormalOrMainTaskMethod(method.getReference())) {
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
