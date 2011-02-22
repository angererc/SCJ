package scj.compiler;

import scj.Runtime;
import scj.Task;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;

public class ScheduleSitesOnlyCompilation extends CompilationDriver {

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
		
		method.instrument(new ExprEditor() {

			@Override
			public void edit(MethodCall m) throws CannotCompileException {
				if(m.getMethodName().startsWith(Task.MainTaskMethodPrefix)) {
					String statement = "{ " + Runtime.ScheduleMainTaskMethod + "($0, \"" + m.getMethodName() + "\", $args); }";							
					if(Task.DEBUG)
						System.out.println("found schedule site: " + m.getMethodName() + " in " + method.getLongName() + "; replacing it with " + statement);
					m.replace(statement);
				} else if (m.getMethodName().startsWith(Task.NormalTaskMethodPrefix)) {
					String statement = "{ " + Runtime.ScheduleNormalTaskMethod + "($0, \"" + m.getMethodName() + "\", $args); }";
					if(Task.DEBUG)
						System.out.println("found schedule site: " + m.getMethodName() + " in  " + method.getLongName() + "; replacing it with " + statement);
					m.replace(statement);
				}
			}
			
		});
		
		
	}

}
