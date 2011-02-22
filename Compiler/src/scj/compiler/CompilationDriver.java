package scj.compiler;

import java.io.File;
import java.io.IOException;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.ref.ReferenceCleanser;

public abstract class CompilationDriver {

	public static final boolean DEBUG = true;

	private static final ClassLoader MY_CLASSLOADER = CompilationDriver.class.getClassLoader();
	
	protected final CompilerOptions compilerOptions;
	
	private AnalysisCache cache;
	private AnalysisScope scope;
	private ClassHierarchy classHierarchy;
	private Iterable<Entrypoint> entrypoints;
	private AnalysisOptions options;
	private CallGraph cg;
	private PointerAnalysis pointerAnalysis;
	
	protected CompilationDriver(CompilerOptions opts) {
		this.compilerOptions = opts;
	}
	
	public CompilerOptions compilerOptions() {
		return this.compilerOptions;
	}
	
	public void compile() throws ClassHierarchyException, IOException, IllegalArgumentException, CallGraphBuilderCancelException {
		this.setUpWala();
	}
	
	public Iterable<Entrypoint> entrypoints() {
		assert entrypoints != null;
		return this.entrypoints;
	}
	
	public CallGraph callGraph() {
		assert cg != null;
		return this.cg;
	}
	
	public PointerAnalysis pointerAnalysis() {
		assert pointerAnalysis != null;
		return pointerAnalysis;
	}
	
	public ClassHierarchy classHierarchy() {
		assert classHierarchy != null;
		return classHierarchy;
	}
	
	public IR irForMethod(IMethod method) {
		return cache.getIR(method);
	}
	
	public void setUpWala() throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
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
		classHierarchy = ClassHierarchy.make(scope);
		ReferenceCleanser.registerClassHierarchy(classHierarchy);
		//
		this.entrypoints = new AllApplicationEntrypoints(scope, classHierarchy);
		this.options = new AnalysisOptions(scope, entrypoints);
	
		//
		CallGraphBuilder builder = compilerOptions.createCallGraphBuilder(options, cache, scope, classHierarchy);
		cg = builder.makeCallGraph(options, null);
		pointerAnalysis = builder.getPointerAnalysis();
	}	

}
