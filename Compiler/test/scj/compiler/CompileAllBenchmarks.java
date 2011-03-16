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
	
	private ArrayList<String> ercoBenchmarkArgs(String prefix, String opt) {
		ArrayList<String> args = new ArrayList<String>();	
		args.add("-prefix=" + prefix);
		if(opt != null)
			args.add("-opt=" + opt);
		args.add("bin/" + prefix);
		return args;
	}
		
	private Compiler compilerWithArgs(ArrayList<String> args) {
		String[] argsString = new String[args.size()];
		args.toArray(argsString);
		CompilerOptions options = new CompilerOptions(argsString);
		return new Compiler(options);
	}
	
	@Test
	public void compileBarneshutOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "orig")).compile();		
	}
	
	@Test
	public void compileBarneshutSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "sc")).compile();		
	}
	
	//@Test
	public void compileBarneshutOptimized() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("barneshut", "default:ZeroXCFA:none")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileBoruvkaOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("boruvka", "orig")).compile();		
	}
	
	@Test
	public void compileBoruvkaSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("boruvka", "sc")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileClusteringOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("clustering", "orig")).compile();		
	}
	
	@Test
	public void compileClusteringSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("clustering", "sc")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileDelaunayrefinementOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("delaunayrefinement", "orig")).compile();		
	}
	
	@Test
	public void compileDelaunayrefinementSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("delaunayrefinement", "sc")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileDelaunaytriangulationOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("delaunaytriangulation", "orig")).compile();		
	}
	
	@Test
	public void compileDelaunaytriangulationSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("delaunaytriangulation", "sc")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileGmetisOrig() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("gmetis", "orig")).compile();		
	}
	
	@Test
	public void compileGmetisSC() throws Exception {
		compilerWithArgs(galoisBenchmarkArgs("gmetis", "sc")).compile();		
	}

	/**
	 * 
	 */
	
	@Test
	public void compilePhiloOrig() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "orig")).compile();		
	}
	
	@Test
	public void compilePhiloSCNaive() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compilePhiloFullyOptimized() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compilePhiloESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "default:ZeroXCFA:ESC")).compile();		
	}
}
