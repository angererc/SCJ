package scj.compiler;

import scj.compiler.optimizing.OptimizingUtil;

import javassist.CtClass;

import com.ibm.wala.classLoader.IClass;

public class SequentiallyConsistentCompilation extends ScheduleSitesOnlyCompilation {

	
	
	public SequentiallyConsistentCompilation(CompilerOptions opts) {
		super(opts);		
	}
	
	@Override
	public String prefix() {
		return "sc";
	}
	
	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		
		OptimizingUtil.markAllFieldsVolatile(ctclass);
		
		if(ctclass.getName().startsWith("java.lang")) { //have to exclude the whole java.lang package; not sure why I can't just exclude the required classes; I don't find all of them i guess
			//System.err.println("OptimizingUtil: ignoring system method " + mName);
			return;
		}
		//rewrite schedule sites
		super.rewrite(iclass, ctclass);
		
		OptimizingUtil.makeAllArrayAccessesVolatile(ctclass);
		OptimizingUtil.makeAllFieldAccessesVolatile(compilationStats, ctclass);
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}
}
