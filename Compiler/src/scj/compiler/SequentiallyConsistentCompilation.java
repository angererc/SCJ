package scj.compiler;

import java.lang.reflect.Modifier;

import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

import com.ibm.wala.classLoader.IClass;

public class SequentiallyConsistentCompilation extends ScheduleSitesOnlyCompilation {

	private CtClass scjRuntimeClass;
	private CodeConverter converter;
	
	public SequentiallyConsistentCompilation(CompilerOptions opts) {
		super(opts);
	}
	
	@Override
	public String prefix() {
		return "sc";
	}
	
	@Override
	public void prepareEmitCode() throws Exception {
		scjRuntimeClass = classPool.get("scj.Runtime");
		
		converter = new CodeConverter();
		converter.replaceArrayAccess(scjRuntimeClass, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
		
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		//rewrite schedule sites
		super.rewrite(iclass, ctclass);

		//change all fields to volatile
		for(CtField field : ctclass.getDeclaredFields()) {
			int mods = field.getModifiers();
			if(! Modifier.isVolatile(mods)) {
				int newMods = mods | Modifier.VOLATILE;				
				field.setModifiers(newMods);
			}
			
		}
		
		//rewrite array accesses to call the Runtime array accessors
		for(CtMethod method : ctclass.getDeclaredMethods()) {
				method.instrument(converter);
		}
		
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
	}
}
