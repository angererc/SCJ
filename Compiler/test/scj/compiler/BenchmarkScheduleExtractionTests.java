package scj.compiler;

import static org.junit.Assert.*;

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
	
	private static class RelTester {
		TaskSchedule<Integer, ?> schedule;
		RelTester(TaskSchedule<Integer, ?> schedule) {
			this.schedule = schedule;
		}
		
		void assertHB(int a, int b) {
			assertTrue(schedule.relationForNodes(a, b) == TaskSchedule.Relation.happensBefore);
			assertTrue(schedule.relationForNodes(b, a) == TaskSchedule.Relation.happensAfter);
		}
		
		void assertSingleton(int a) {
			assertTrue(schedule.relationForNodes(a, a) == TaskSchedule.Relation.singleton);
		}
		
		void assertOrdered(int a) {
			assertTrue(schedule.relationForNodes(a, a) == TaskSchedule.Relation.ordered);
		}
		
		void assertUnordered(int a) {
			assertTrue(schedule.relationForNodes(a, a) == TaskSchedule.Relation.unordered);
		}
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
			RelTester t = new RelTester(schedule);
			
			System.out.println("=====================");
			System.out.println(method.getName());
			schedule.print(System.out);	
			
			String mName = method.getName().toString();
			if(mName.equals("scjTask_ComputeTimeStep")) {
				//ssa variables
				int now = 2;
				int later = 4;
				int join = 35;
				int compForce = 57;
				int advance = 63;
				//now hb all others (now hb later by transitivity) 
				t.assertHB(now, later);
				t.assertHB(now, join);
				t.assertHB(now, compForce);
				t.assertHB(now, advance);
				
				//all but compForce are singletons
				t.assertSingleton(now);
				t.assertSingleton(later);
				t.assertSingleton(join);
				t.assertSingleton(advance);
				t.assertUnordered(compForce);
				
				//the real schedule we expect
				t.assertHB(compForce, join);
				t.assertHB(join, advance);
				t.assertHB(advance, later);
				
			} else if (mName.equals("scjTask_process")) {
				//only now
				t.assertSingleton(2);
				
			} else if (mName.equals("scjMainTask_Main")) {
				int now = 2;
				int later = 4;
				int iteration = 13;
				
				t.assertOrdered(iteration);
				t.assertSingleton(later);
				
				t.assertHB(iteration, later);
				t.assertHB(now, iteration);
				t.assertHB(now, later);
			} else if (mName.equals("scjTask_Advance")) {
				//only now
				t.assertSingleton(2);
			} else {
				assertTrue("unknown method: " + method.getName(), false);
			}
		}
		
	}
}
