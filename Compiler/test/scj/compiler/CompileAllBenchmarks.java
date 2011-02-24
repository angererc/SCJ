package scj.compiler;

import java.util.ArrayList;

import org.junit.Test;


public class CompileAllBenchmarks {

	private ArrayList<String> galoisBenchmarkArgs(String prefix, String opt) {
		ArrayList<String> args = new ArrayList<String>();	
		args.add("-prefix=" + prefix);
		if(opt != null)
			args.add("-opt=" + opt);
		args.add("bin/" + prefix);
		args.add("bin/galois");
		args.add("bin/util");
		args.add("bin/galois_scj");
		return args;
	}
		
	private Compiler compilerWithArgs(ArrayList<String> args) {
		String[] argsString = new String[args.size()];
		args.toArray(argsString);
		CompilerOptions options = new CompilerOptions(argsString);
		return new Compiler(options);
	}
	
	//@Test
	public void compileBarneshutOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "orig")).compile();		
	}
	
	//@Test
	public void compileBarneshutSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "sc")).compile();		
	}
	
	@Test
	public void compileBarneshutOptimized() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "default:ZeroXCFA:none")).compile();		
	}
	
}
