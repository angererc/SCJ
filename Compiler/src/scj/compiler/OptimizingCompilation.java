package scj.compiler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.analysis.escape.EscapeAnalysis;
import scj.compiler.analysis.schedule.ScheduleAnalysis;
import scj.compiler.wala.util.WalaConstants;
import sun.misc.Unsafe;

import javassist.CannotCompileException;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;

public class OptimizingCompilation extends CompilationDriver {

	private AnalysisOptions walaOptions;
	private CallGraph callGraph;
	private PointerAnalysis pointerAnalysis;

	private ScheduleAnalysis scheduleAnalysis;
	private EscapeAnalysis escapeAnalysis;

	private HashSet<IMethod> taskMethods;
	private HashSet<IMethod> mainTaskMethods;

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

	private IMethod iMethod(CtMethod ctMethod, IClass iclass) {
		String signature = ctMethod.getSignature();
		Selector selector = Selector.make(signature);
		IMethod iMethod = iclass.getMethod(selector);
		assert iMethod != null;
		return iMethod;
	}

	private Field getField(Class<?> clazz, String fieldName) throws Exception {
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
			throw new NoSuchFieldException(fieldName);
		}
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

			final Set<String> fieldsToProtect = new HashSet<String>();
			fieldsToProtect.add("posx");

			//
			ctMethod.instrument(new ExprEditor() {

				@Override
				public void edit(FieldAccess f) throws CannotCompileException {
					String fieldName = f.getFieldName();
					if(fieldsToProtect.contains(fieldName)) {
						String fieldsClassName = f.getClassName();
						Class<?> runtimeClass;
						try {
							runtimeClass = Class.forName(fieldsClassName);
						} catch (ClassNotFoundException e) {
							throw new RuntimeException(e);
						}
						assert runtimeClass != null;
						long index;
						try {
							System.out.println("editing field access " + fieldName + " in class " + runtimeClass);
							Field field = getField(runtimeClass, fieldName);
							Unsafe unsafe = scj.Runtime.unsafe;
							if(Modifier.isStatic(field.getModifiers())) {
								index = unsafe.staticFieldOffset(field);
							} else {
								index = unsafe.objectFieldOffset(field);
							}
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
						System.out.println("Field " + fieldName + " has index " + index + " in class " + runtimeClass);
						if(f.isReader()) {
							//note: maybe I have to use unsafe.staticFieldBase(field) as the object to unsafe.put and unsafe.get
							f.replace("$_ = scj.Runtime.getDoubleVolatile((Object)$0, " + index + "l);");
						} else {
							assert f.isWriter();
							//f.replace("scj.Runtime.unsafe.putIntVolatile($0, 42, $1);");
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
		getOrCreateScheduleAnalysis().analyze();
	}

	public void runEscapeAnalysis() {
		getOrCreateEscapeAnalysis().run();
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
	}

	public CallGraph callGraph() {
		assert callGraph != null;
		return this.callGraph;
	}

	public PointerAnalysis pointerAnalysis() {
		assert callGraph != null;
		return pointerAnalysis;
	}

	public Set<IMethod> allTaskMethods() {
		return this.taskMethods;
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
