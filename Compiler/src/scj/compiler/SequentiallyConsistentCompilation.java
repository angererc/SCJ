package scj.compiler;

import java.lang.reflect.Modifier;

import javassist.CtClass;
import javassist.CtField;

import com.ibm.wala.classLoader.IClass;

public class SequentiallyConsistentCompilation extends ScheduleSitesOnlyCompilation {

	public SequentiallyConsistentCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		//rewrite schedule sites
		super.rewrite(iclass, ctclass);
		
		for(CtField field : ctclass.getDeclaredFields()) {
			int mods = field.getModifiers();
			if(! Modifier.isVolatile(mods)) {
				int newMods = mods | Modifier.VOLATILE;				
				field.setModifiers(newMods);
			}
		}
		
		//TODO: rewrite arrays
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}
}
