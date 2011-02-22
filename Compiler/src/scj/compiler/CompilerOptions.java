package scj.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.io.FileProvider;

public class CompilerOptions {
	
	private ArrayList<String> applicationFiles = new ArrayList<String>();
	private String exclusionsFile;
	private String outputFolder = "./scj_build/";
	private String standardScopeFile = "scj/compiler/wala/StandardScope.txt";
	
	private CompilationDriver compilationDriver;
	
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
			
		} else if(opt.startsWith("-output=")) {
			this.outputFolder = opt.substring(8);
		} else if(opt.startsWith("-exclusions=")) {
			this.exclusionsFile = opt.substring(12);
		} else if(opt.startsWith("-standardScope=")) {
			this.standardScopeFile = opt.substring(15);
		} else {
			if(opt.startsWith("-")) {
				throw new IllegalArgumentException(opt);
			}
			applicationFiles.add(opt);
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
	
	public File openOutputFolder() {
		return openFile(outputFolder);
	}
	public File openExclusionsFile() {		
		return openFile(this.exclusionsFile);
	}

	public CompilationDriver getCompilationDriver() {
		return compilationDriver;
	}
	
	public PropagationCallGraphBuilder createCallGraphBuilder(AnalysisOptions options, AnalysisCache cache, AnalysisScope scope, ClassHierarchy classHierarchy) {
		ContextSelector def = new DefaultContextSelector(options);
	    ContextSelector nCFAContextSelector = new nCFAContextSelector(1, def);
	    
		return Util.makeZeroCFABuilder(options, cache, classHierarchy, scope, nCFAContextSelector, null);
	}
}
