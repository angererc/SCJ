package scj.compiler.optimizing;

import java.util.ArrayList;
import java.util.Stack;

import scj.compiler.CompilerOptions;

import javassist.expr.FieldAccess;

public class CompilationStats {
	
	private final CompilerOptions options;
	private int ir, ur, iw, uw, ia, ua;
	private int schedSites, mainSchedSites;
	private Stack<Timing> timings = new Stack<Timing>();
	private ArrayList<Timing> timingsList = new ArrayList<Timing>();
	
	private static class Timing {
		final long startTime;
		long endTime;
		final String name;
		
		public Timing(String name) {
			this.name = name;
			this.startTime = System.currentTimeMillis();
		}
		
		@Override
		public String toString() {
			return name + "\t" + (endTime - startTime);
		}
	}
	
	public CompilationStats(CompilerOptions options) {
		this.options = options;
	}

	public void recordInstrumentedRead(FieldAccess f) {
		ir++;
	}

	public void recordUninstrumentedRead(FieldAccess f) {
		ur++;
	}

	public void recordInstrumentedWrite(FieldAccess f) {
		iw++;
	}

	public void recordUninstrumentedWrite(FieldAccess f) {
		uw++;
	}
	
	public void recordUninstrumentedArrayAccess(int pos, int c,
			String methodName) {
		ua++;
	}

	public void recordInstrumentedArrayAccess(int pos, int c, String methodName) {
		ia++;
	}
	
	public void recordScheduleSite() {
		schedSites++;
	}
	
	public void recordMainScheduleSite() {
		mainSchedSites++;
	}
	
	public void startTiming(String name) {
		System.out.println("Starting " + name);
		timings.push(new Timing(name));		
	}
	
	public void stopTiming() {
		assert timings.peek().endTime == 0;
		Timing t = timings.pop();
		t.endTime = System.currentTimeMillis();
		timingsList.add(t);
	}

	public void printStats() {
		assert timings.isEmpty();
		System.out.println("");
		System.out.printf("%s\t%s\n", options.prefix(), options.configurationString());
		System.out.println("");
		System.out.printf("Main Schedule Sites\t%d\n", mainSchedSites);
		System.out.printf("Schedule Sites\t%d\n", schedSites);
		System.out.println("Memory\t0");
		System.out.println("");
		System.out.println("\tInstrumented\tUninstrumented");
		System.out.printf("reads\t%s\t%s\n", ir, ur);
		System.out.printf("writes\t%s\t%s\n", iw, uw);
		System.out.printf("array\t%s\t%s\n", ia, ua);
		System.out.println("");
		System.out.println("Phase\tDuration");
		for(int i = 0; i < timingsList.size(); i++) {
			Timing t = timingsList.get(i);
			System.out.println(t);
		}
	}
}
