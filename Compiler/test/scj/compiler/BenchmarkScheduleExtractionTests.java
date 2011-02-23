package scj.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import scj.compiler.analysis.schedule.TaskSchedule;

import com.ibm.wala.classLoader.IMethod;


public class BenchmarkScheduleExtractionTests {

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
	
	@Test
	public void compileBarneshutDefault_ZeroXCFA() throws Exception {
		Compiler compiler = compilerWithArgs(galoisBenchmarkArgs("barneshut", "default:ZeroXCFA"));
		OptimizingCompilation driver = (OptimizingCompilation)compiler.compilationDriver();
		driver.setUpCompiler();		
		driver.findTaskMethods();
		driver.computeTaskSchedules();
		
		Set<IMethod> mainTaskMethods = driver.mainTaskMethods();
		Set<IMethod> taskMethods = driver.taskMethods();
		
		Set<IMethod> allTaskMethods = new HashSet<IMethod>();
		allTaskMethods.addAll(mainTaskMethods);
		allTaskMethods.addAll(taskMethods);
		
		for(IMethod method : allTaskMethods) {
			TaskSchedule<Integer, ?> schedule = driver.taskScheduleForTaskMethod(method);
			System.out.println("=====================");
			System.out.println(method.getName());
			schedule.print(System.out);			
		}
		
	}
}
