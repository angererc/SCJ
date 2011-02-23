package scj.compiler;

import java.io.File;
import java.io.IOException;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.ref.ReferenceCleanser;

public abstract class CompilationDriver {

	public static final boolean DEBUG = true;

	private static final ClassLoader MY_CLASSLOADER = CompilationDriver.class.getClassLoader();
	
	protected final CompilerOptions compilerOptions;
	
	//wala stuff
	private AnalysisCache cache;
	private AnalysisScope scope;
	private ClassHierarchy classHierarchy;
	private Iterable<Entrypoint> entrypoints;
	
	//javassist stuff
	protected ClassPool classPool;;
	
	protected CompilationDriver(CompilerOptions opts) {
		this.compilerOptions = opts;
	}
	
	public CompilerOptions compilerOptions() {
		return this.compilerOptions;
	}
	
	public abstract void analyze() throws Exception;
	
	public void compile() throws Exception {
		this.setUpCompiler();
		this.analyze();
		
		this.emitCode();
	}
	
	public Iterable<Entrypoint> entrypoints() {
		assert entrypoints != null;
		return this.entrypoints;
	}
	
	public ClassHierarchy classHierarchy() {
		assert classHierarchy != null;
		return classHierarchy;
	}
	
	public AnalysisScope scope() {
		return this.scope;
	}
	
	public AnalysisCache cache() {
		return this.cache;
	}
	
	public IR irForMethod(IMethod method) {
		return cache.getIR(method);
	}
	
	public void setUpCompiler() throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		assert cache == null;
		cache = new AnalysisCache();
		ReferenceCleanser.registerCache(cache);
		//
		File exclusionsFile = compilerOptions.openExclusionsFile();
		scope = AnalysisScopeReader.readJavaScope(compilerOptions.standardScopeFile(), exclusionsFile, MY_CLASSLOADER);
		for(String s : compilerOptions.applicationFiles()) {
			AnalysisScope appScope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(s, exclusionsFile);
			scope.addToScope(appScope);
		}
		//
		System.out.println("Making class hierarchy");
		classHierarchy = ClassHierarchy.make(scope);
		ReferenceCleanser.registerClassHierarchy(classHierarchy);
		//
		this.entrypoints = new AllApplicationEntrypoints(scope, classHierarchy);	
	
	}	
	
	public abstract String prefix();
	public void prepareEmitCode() throws Exception { }
	public void cleanupEmitCode() { }
	public abstract boolean wantsToRewrite(IClass iclass);
	public abstract void rewrite(IClass iclass, CtClass ctclass) throws Exception;
	
	public void emitCode() throws Exception {
		String outputFolder = compilerOptions.outputFolder();
		classPool = ClassPool.getDefault();
		this.prepareEmitCode();
		int i = 0;
		for(IClass iclass : classHierarchy) {
			System.out.print(".");
			if(i++ % 60 == 0) {
				System.out.println();
			}
			if(this.wantsToRewrite(iclass)) {					
				CtClass ctclass = null;
				if(iclass.getSource() == null) {
					String classname = iclass.getName().getPackage().toString().replace('/', '.') + "." + iclass.getName().getClassName();
//					if(DEBUG)
//						System.err.println("Warning: no input source for class " + iclass + ". Trying to load through javassist: " + classname);
					try {
						ctclass = classPool.get(classname);
					} catch(NotFoundException e) {
						//e.printStackTrace();
					}
				} else {
//					if(DEBUG)
//						System.err.println("Found input source for class " + iclass);
					ctclass = classPool.makeClass(iclass.getSource());
				}
				
				if(ctclass != null) {
					this.rewrite(iclass, ctclass);
					ctclass.writeFile(outputFolder);					
				} else {
//					if(DEBUG)
//						System.err.println("Warning: wasn't able to create a CtClass for " + iclass + ". Did not compile it.");
				}
			}				
		}
		this.cleanupEmitCode();
	}
}
