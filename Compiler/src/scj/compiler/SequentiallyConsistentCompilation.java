package scj.compiler;

import scj.compiler.optimizing.CompilationStats;
import scj.compiler.optimizing.OptimizingUtil;

import javassist.CtClass;

import com.ibm.wala.classLoader.IClass;

public class SequentiallyConsistentCompilation extends ScheduleSitesOnlyCompilation {

	private CompilationStats stats;
	
	public SequentiallyConsistentCompilation(CompilerOptions opts) {
		super(opts);
	}
	
	@Override
	public String prefix() {
		return "sc";
	}
	
	@Override
	public void prepareEmitCode() throws Exception {
		stats = new CompilationStats();
	}
	
	@Override
	public void cleanupEmitCode() {
		System.out.println("");
		stats.printStats();
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		//rewrite schedule sites
		super.rewrite(iclass, ctclass);

		OptimizingUtil.markAllFieldsVolatile(ctclass);
		OptimizingUtil.makeAllArrayAccessesVolatile(ctclass);
		OptimizingUtil.makeAllFieldAccessesVolatile(stats, ctclass);
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}
}
