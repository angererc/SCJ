package scj.compiler;

import scj.compiler.analysis.schedule.ScheduleAnalysis;

import javassist.CtClass;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;

public class OptimizingCompilation extends CompilationDriver {

	private AnalysisOptions walaOptions;
	private CallGraph cg;
	private PointerAnalysis pointerAnalysis;

	private ScheduleAnalysis scheduleAnalysis;

	protected OptimizingCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public void analyze() throws Exception {
		this.runScheduleAnalysis();
		
		computeCallGraph();
	}

	@Override
	public String prefix() {
		String[] spec = compilerOptions.optimizationLevel();

		StringBuffer result = new StringBuffer();
		if (spec.length > 0) {
			result.append(spec[0]);
			for (int i=1; i<spec.length; i++) {
				result.append("_");
				result.append(spec[i]);
			}
		}
		return result.toString();
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		// TODO Auto-generated method stub

	}
	
	public void runScheduleAnalysis() {
		getOrCreateScheduleAnalysis().analyze();
	}
	
	public ScheduleAnalysis getOrCreateScheduleAnalysis() {
		if(this.scheduleAnalysis == null) {
			this.scheduleAnalysis = compilerOptions.createScheduleAnalysis(this);
		}
		return this.scheduleAnalysis;
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}

	public void computeCallGraph() {
		System.out.println("Computing call graph");
		assert(cg == null);
		//
		this.walaOptions = new AnalysisOptions(scope(), entrypoints());
		
		CallGraphBuilder builder = compilerOptions.createCallGraphBuilder(walaOptions, cache(), scope(), classHierarchy());
		try {
			cg = builder.makeCallGraph(walaOptions, null);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		pointerAnalysis = builder.getPointerAnalysis();
	}

	public CallGraph callGraph() {
		assert cg != null;
		return this.cg;
	}

	public PointerAnalysis pointerAnalysis() {
		assert cg != null;
		return pointerAnalysis;
	}

}
