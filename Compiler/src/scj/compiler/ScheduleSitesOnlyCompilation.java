package scj.compiler;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;

import com.ibm.wala.classLoader.IClass;

public class ScheduleSitesOnlyCompilation extends CompilationDriver {

	private final ScheduleSiteEditor scheduleSiteEditor = new ScheduleSiteEditor();
	
	protected ScheduleSitesOnlyCompilation(CompilerOptions opts) {
		super(opts);
	}

	@Override
	public String prefix() {
		return "orig";
	}
	
	@Override
	public void analyze() throws Exception {
		
	}

	@Override
	public void rewrite(IClass iclass, CtClass ctclass) throws Exception {
		CtMethod[] methods = ctclass.getDeclaredMethods();
		for (int k=0; k<methods.length; k++) {
//			if(DEBUG)
//				System.out.println("instrumenting method " + methods[k]);
			instrumentMethod(methods[k]);									
		}
	}

	@Override
	public boolean wantsToRewrite(IClass iclass) {
		return true;
//		//here we assume that schedule sites are only allowed inside Task methods!
//		for(IMethod method : iclass.getDeclaredMethods()) {
//			String methodName = method.getName().toString();
//			if(methodName.startsWith(Task.NormalTaskMethodPrefix) || methodName.startsWith(Task.MainTaskMethodPrefix))
//				return true;
//			
//		}
//		return false;
	}
	
	private void instrumentMethod(final CtMethod method) throws CannotCompileException {
		//there are GeneratedMethodAccessor2.invoke() methods out there (from the use of reflection in Task)
		//that call xschedTask methods
		//and if we rewrite them we get weird behavior...
		if(method.getLongName().startsWith("sun.reflect."))
			return;
		
		scheduleSiteEditor.setMethod(method);
		method.instrument(scheduleSiteEditor);		
	}

}
