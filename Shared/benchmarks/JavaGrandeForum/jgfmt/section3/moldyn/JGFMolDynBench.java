/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/

package jgfmt.section3.moldyn;

import scj.Task;
import jgfmt.jgfutil.JGFInstrumentor;
import jgfmt.jgfutil.JGFSection3;

public class JGFMolDynBench implements JGFSection3 {

	public static int nthreads;
	public final mdBase base;
	public boolean failed; // NDM

	public JGFMolDynBench(int nthreads) {
		this.nthreads = nthreads;
		if (nthreads != -1) 
			this.base = new md();
		else {
			//we use -1 as a flag to start the activation based implementation, but we use nthreads as array dimensions.
			this.nthreads = 2;
			this.base = new mdSCJ();
		}
	}

	// int size;

	public void JGFsetsize(int size) {
		base.size = size;
	}

	public void JGFinitialise() {

		base.initialise();

	}

	public void JGFapplication() {
		if (! (base instanceof mdSCJ)) { 
			//base.runiters(); //TODO commented out to avoid spurious call in schedule analysis
		} else {
			mdSCJ scjBase = (mdSCJ)base;
			scjBase.scjMainTask_start(new Task<Void>());			
		}		

	}

	public void JGFvalidate() {
		double result = base.getresult();
		
		double refval[] = { 1731.4306625334357, 7397.392307839352 };
		double dev = Math.abs(result - refval[base.size]);
		if (dev > 1.0e-10) {
			failed = true; // NDM
			System.out.println("Validation failed");
			System.out.println("Kinetic Energy = " + result + "  " + dev + "  "
					+ base.size);
		}
	}

	public void JGFtidyup() {

		// one = null;
		System.gc();
	}

	public void JGFrun(int size) {

		JGFInstrumentor.addTimer("Section3:MolDyn:Total", "Solutions", size);
		JGFInstrumentor.addTimer("Section3:MolDyn:Run", "Interactions", size);

		JGFsetsize(size);

		JGFInstrumentor.startTimer("Section3:MolDyn:Total");

		JGFinitialise();
		JGFapplication();
		JGFvalidate();
		JGFtidyup();

		JGFInstrumentor.stopTimer("Section3:MolDyn:Total");

		JGFInstrumentor.addOpsToTimer(
				"Section3:MolDyn:Run",
				(double) base.getinteractions());
		JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Total", 1);

		JGFInstrumentor.printTimer("Section3:MolDyn:Run");
		JGFInstrumentor.printTimer("Section3:MolDyn:Total");
	}

}
