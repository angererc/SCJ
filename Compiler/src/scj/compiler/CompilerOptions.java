package scj.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import scj.compiler.analysis.escape.DirectedEscapeAnalysis;
import scj.compiler.analysis.escape.DummyEscapeAnalysis;
import scj.compiler.analysis.escape.EscapeAnalysis;
import scj.compiler.analysis.escape.FullEscapeAnalysis;
import scj.compiler.analysis.schedule.DummyScheduleAnalysis;
import scj.compiler.analysis.schedule.FullScheduleAnalysis;
import scj.compiler.analysis.schedule.ScheduleAnalysis;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXContainerCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.rta.BasicRTABuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.io.FileProvider;

public class CompilerOptions {
	
	private ArrayList<String> applicationFiles = new ArrayList<String>();
	private String exclusionsFile = "scj/compiler/wala/Exclusions.txt";
	private String outputFolder = "./scj_build/";
	private String standardScopeFile = "scj/compiler/wala/StandardScope.txt";
	private String prefix;
	private boolean driverPrefix = true;
	
	private CompilationDriver compilationDriver;
	
	private String[] optimizationLevel;
	private int policy = 0;
	
	public CompilerOptions(String[] args) {
		this.parseArguments(args);
		
		if(this.compilationDriver == null) {
			this.addOption("-opt=sc");
		}
	}
	
	private void parseArguments(String[] args) {
		int len = args.length;
		int i;
		for(i = 0; i < len; i++) {
			this.addOption(args[i]);
		}
	}
	
	private void addOption(String opt) {
		if(opt.equals("-opt=sc")) {
			if(this.compilationDriver != null) {
				throw new IllegalArgumentException("Only one -opt=xyz option allowed");
			}
			this.compilationDriver = new SequentiallyConsistentCompilation(this);
		} else if(opt.equals("-opt=orig")) {
			if(this.compilationDriver != null) {
				throw new IllegalArgumentException("Only one -opt=xyz option allowed");
			}
			this.compilationDriver = new ScheduleSitesOnlyCompilation(this);
		} else if(opt.startsWith("-opt=")) {
			if(this.compilationDriver != null) {
				throw new IllegalArgumentException("Only one -opt=xyz option allowed");
			}
			this.compilationDriver = new OptimizingCompilation(this);
			parseOptimizationLevel(opt.substring(5));
		} else if(opt.startsWith("-zeroXCFAPolicy=")) {
			parseCFAPolicy(opt.substring(11));
		} else if(opt.startsWith("-output=")) {
			this.outputFolder = opt.substring(8);
		} else if(opt.startsWith("-exclusions=")) {
			this.exclusionsFile = opt.substring(12);
		} else if(opt.startsWith("-standardScope=")) {
			this.standardScopeFile = opt.substring(15);
		} else if(opt.startsWith("-prefix=")) {
			this.prefix = opt.substring(8);
		} else if(opt.equals("-driverPrefix=YES")) {
			this.driverPrefix = true;
		} else if(opt.equals("-driverPrefix=NO")) {
			this.driverPrefix = false;
		} else {
			if(opt.startsWith("-")) {
				throw new IllegalArgumentException(opt);
			}
			applicationFiles.add(opt);
		}
	}
	
	private void parseOptimizationLevel(String levelString) {
		optimizationLevel = levelString.split(":");
		if(optimizationLevel.length != 3) {
			throw new IllegalArgumentException("Illegal optimization level specification: " + levelString + ". It must follow the form contextSelector:cfaBuilder:analyses");
		}
	}
	
	private File openFile(String filename) {
		if(filename == null)
			return null;

		File file;
		try {
			file = FileProvider.getFile(filename);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return file;
	}
	
	public String standardScopeFile() {
		return this.standardScopeFile;
	}
	
	public ArrayList<String> applicationFiles() {
		return this.applicationFiles;
	}
	
	public String outputFolder() {
		String result = this.prefix == null ? outputFolder : outputFolder + prefix + "/";
		if(this.driverPrefix) {
			result = result + compilationDriver.prefix() + "/";
		}
		return result;
	}
	
	public File openExclusionsFile() {		
		return openFile(this.exclusionsFile);
	}

	public CompilationDriver compilationDriver() {
		return compilationDriver;
	}
	
	public String[] optimizationLevel() {
		return this.optimizationLevel;
	}
	
	public ContextSelector createContextSelector(AnalysisOptions options) {
		String contextType = optimizationLevel[0];
		if(contextType.equals("default")) {
			return null;
		} else {
			throw new IllegalArgumentException("Illegal context sensitivity: " + contextType);
		}
	}
	
	public SSAContextInterpreter createContextInterpreter() {
		//for now we always return null;; not sure if the context interpreter is something a user may want to change...
		return null;
	}
	  
	private void parseCFAPolicy(String policyString) {
		String[] parts = policyString.split("|");
		policy = ZeroXInstanceKeys.NONE;
		for(String part : parts) {
			if(part.equals("ALLOCATIONS")) {
				/**
				   * An ALLOCATIONS - based policy distinguishes instances by allocation site. Otherwise, the policy distinguishes instances by
				   * type.
				   */
				policy = policy | ZeroXInstanceKeys.ALLOCATIONS;
			} else if(part.equals("SMUSH_STRINGS")) {
				/**
				   * A policy variant where String and StringBuffers are NOT disambiguated according to allocation site.
				   */
				policy = policy | ZeroXInstanceKeys.SMUSH_STRINGS;
			} else if (part.equals("SMUSH_THROWABLES")) {
				/**
				   * A policy variant where {@link Throwable} instances are NOT disambiguated according to allocation site.
				   * 
				   */
				policy = policy | ZeroXInstanceKeys.SMUSH_THROWABLES;
			} else if (part.equals("SMUSH_PRIMITIVE_HOLDERS")) {
				/**
				   * A policy variant where if a type T has only primitive instance fields, then instances of type T are NOT disambiguated by
				   * allocation site.
				   */
				policy = policy | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS;
			} else if (part.equals("SMUSH_MANY")) {
				/**
				   * This variant counts the N, number of allocation sites of a particular type T in each method. If N > SMUSH_LIMIT, then these N
				   * allocation sites are NOT distinguished ... instead there is a single abstract allocation site for <N,T>
				   * 
				   * Probably the best choice in many cases.
				   */
				policy = policy | ZeroXInstanceKeys.SMUSH_MANY;
			} else if (part.equals("CONSTANT_SPECIFIC")) {
				/**
				   * Should we use constant-specific keys?
				   */
				policy = policy | ZeroXInstanceKeys.CONSTANT_SPECIFIC;
			} else {
				throw new IllegalArgumentException("Illegal CFA policy: " + part);
			}
		}
	}
	
	public ScheduleAnalysis createScheduleAnalysis(OptimizingCompilation compiler) {
		String analysesType = this.optimizationLevel[2];
		if(analysesType.contains("SA")){
			return new FullScheduleAnalysis(compiler);
		} else {
			return new DummyScheduleAnalysis(compiler);
		}
	}
	
	public EscapeAnalysis createEscapeAnalysis(OptimizingCompilation compiler) {
		String analysesType = this.optimizationLevel[2];
		if(analysesType.contains("D-ESC")) {
			return new DirectedEscapeAnalysis(compiler);
		}else if(analysesType.contains("ESC")){
			return new FullEscapeAnalysis(compiler);
		} else {
			return new DummyEscapeAnalysis();
		}
	}
	
	public CallGraphBuilder createCallGraphBuilder(AnalysisOptions options, AnalysisCache cache, AnalysisScope scope, ClassHierarchy classHierarchy) {
		String builderType = this.optimizationLevel[1];
		if(builderType.equals("RTA")) {
			Util.addDefaultSelectors(options, classHierarchy);
		    Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), classHierarchy);

		    return new BasicRTABuilder(classHierarchy, options, cache, this.createContextSelector(options), this.createContextInterpreter());
		} if(builderType.equals("ZeroXCFA")) {
			Util.addDefaultSelectors(options, classHierarchy);
		    Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), classHierarchy);
			return new ZeroXCFABuilder(classHierarchy, options, cache, this.createContextSelector(options), this.createContextInterpreter(), policy);
		} else if(builderType.equals("ZeroXContainerCFA")) {
			Util.addDefaultSelectors(options, classHierarchy);
		    Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), classHierarchy);
			options.setUseConstantSpecificKeys(true);
			return new ZeroXContainerCFABuilder(classHierarchy, options, cache, this.createContextSelector(options), this.createContextInterpreter(), policy);
		} else {
			if(! builderType.endsWith("CFA")) {
				throw new IllegalArgumentException("Illegal call graph builder type in optimization level: " + builderType);
			}
			
			int n = Integer.parseInt(builderType.substring(0, builderType.length() - 4));
			System.out.println("CompilerOptions: using nCFA with n=" + n);
			
			Util.addDefaultSelectors(options, classHierarchy);
		    Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), classHierarchy);
			return new nCFABuilder(n, classHierarchy, options, cache, this.createContextSelector(options), this.createContextInterpreter());
		}
	}
}
