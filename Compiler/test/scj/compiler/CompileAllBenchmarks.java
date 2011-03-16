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
	
	private ArrayList<String> jgfBenchmarkArgs(String prefix, String opt) {
		ArrayList<String> args = new ArrayList<String>();	
		args.add("-prefix=" + prefix);
		if(opt != null)
			args.add("-opt=" + opt);
		args.add("bin/jgfmt/section3/" + prefix);
		args.add("bin/jgfmt/jgfutil");		
		args.add("bin/jgfmt/section3/Data");
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
	 * XXX measured
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
		compilerWithArgs(ercoBenchmarkArgs("philo", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compilePhiloFullyOptimized2() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compilePhiloESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("philo", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileSorOrig() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("sor", "orig")).compile();		
	}
	
	@Test
	public void compileSorSCNaive() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("sor", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileSorFullyOptimized() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("sor", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileSorFullyOptimized2() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("sor", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileSorESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("sor", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileTSPOrig() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("tsp", "orig")).compile();		
	}
	
	@Test
	public void compileTSPSCNaive() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("tsp", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileTSPFullyOptimized() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("tsp", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileTSPFullyOptimized2() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("tsp", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileTSPESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("tsp", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileElevatorOrig() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("elevator", "orig")).compile();		
	}
	
	@Test
	public void compileElevatorSCNaive() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("elevator", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileElevatorFullyOptimized() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("elevator", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileElevatorFullyOptimized2() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("elevator", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileElevatorESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("elevator", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileHedcOrig() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("hedc", "orig")).compile();		
	}
	
	@Test
	public void compileHedcSCNaive() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("hedc", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileHedcFullyOptimized() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("hedc", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileHedcFullyOptimized2() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("hedc", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileHedcESCOnly() throws Exception {
		compilerWithArgs(ercoBenchmarkArgs("hedc", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileMoldynOrig() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("moldyn", "orig")).compile();		
	}
	
	@Test
	public void compileMoldynSCNaive() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("moldyn", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileMoldynFullyOptimized() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("moldyn", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileMoldynFullyOptimized2() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("moldyn", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileMoldynESCOnly() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("moldyn", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileMontecarloOrig() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("montecarlo", "orig")).compile();		
	}
	
	@Test
	public void compileMontecarloSCNaive() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("montecarlo", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileMontecarloFullyOptimized() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("montecarlo", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileMontecarloFullyOptimized2() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("montecarlo", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileMontecarloESCOnly() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("montecarlo", "default:ZeroXCFA:ESC")).compile();		
	}
	
	/**
	 * 
	 */
	
	@Test
	public void compileRaytracerOrig() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("raytracer", "orig")).compile();		
	}
	
	@Test
	public void compileRaytracerSCNaive() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("raytracer", "default:ZeroXCFA:none")).compile();		
	}
	
	@Test
	public void compileRaytracerFullyOptimized() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("raytracer", "default:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileRaytracerFullyOptimized2() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("raytracer", "TaskSensitive:ZeroXCFA:D-ESC_SA")).compile();		
	}
	
	@Test
	public void compileRaytracerESCOnly() throws Exception {
		compilerWithArgs(jgfBenchmarkArgs("raytracer", "default:ZeroXCFA:ESC")).compile();		
	}
}
