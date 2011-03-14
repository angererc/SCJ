package scj.compiler;

import java.util.ArrayList;

import org.junit.Test;

import scj.compiler.analysis.schedule.FullScheduleAnalysis;
import scj.compiler.analysis.schedule.core.AnalysisResult;
import com.ibm.wala.ipa.callgraph.CGNode;


public class BenchmarkScheduleAnalysisTests {

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
	public void compileBarneshutDefault_ZeroXCFA() throws Exception {
		Compiler compiler = compilerWithArgs(galoisBenchmarkArgs("barneshut", "default:ZeroXCFA:SA"));
		OptimizingCompilation driver = (OptimizingCompilation)compiler.compilationDriver();
		
		driver.setUpCompiler();
		driver.findConcreteTaskMethods();
		driver.computeCallGraph();
		
		FullScheduleAnalysis sa = (FullScheduleAnalysis)driver.getOrCreateScheduleAnalysis();
		
		sa.computeTaskSchedules();
		sa.populateAnalysisSession();
		AnalysisResult<CGNode> result = sa.runAnalysisOnMainTaskMethods();
		
		System.out.println(result);
	}
	
	@Test
	public void compilePhiloDefault_ZeroXCFA() throws Exception {
		Compiler compiler = compilerWithArgs(ercoBenchmarkArgs("philo", "default:ZeroXCFA:SA"));
		OptimizingCompilation driver = (OptimizingCompilation)compiler.compilationDriver();
		
		driver.setUpCompiler();
		driver.findConcreteTaskMethods();
		driver.computeCallGraph();
		
		FullScheduleAnalysis sa = (FullScheduleAnalysis)driver.getOrCreateScheduleAnalysis();
		
		sa.computeTaskSchedules();
		sa.populateAnalysisSession();
		AnalysisResult<CGNode> result = sa.runAnalysisOnMainTaskMethods();

		System.out.println(result);
	}
	
	@Test
	public void compileSorDefault_ZeroXCFA() throws Exception {
		Compiler compiler = compilerWithArgs(ercoBenchmarkArgs("sor", "default:ZeroXCFA:SA"));
		OptimizingCompilation driver = (OptimizingCompilation)compiler.compilationDriver();
		
		driver.setUpCompiler();
		driver.findConcreteTaskMethods();
		driver.computeCallGraph();
		
		FullScheduleAnalysis sa = (FullScheduleAnalysis)driver.getOrCreateScheduleAnalysis();
		
		sa.computeTaskSchedules();
		sa.populateAnalysisSession();
		AnalysisResult<CGNode> result = sa.runAnalysisOnMainTaskMethods();

		System.out.println(result);
	}
	
}
