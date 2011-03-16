package scj.compiler;

import javassist.CannotCompileException;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import scj.Runtime;
import scj.Task;

public class ScheduleSiteEditor extends ExprEditor {
	private CtMethod method;
	private CompilationStats stats;
	
	public ScheduleSiteEditor(CompilationStats stats) {
		this.stats = stats;
	}
	
	public void setMethod(CtMethod method) {
		this.method = method;		
	}
	
	@Override
	public void edit(MethodCall m) throws CannotCompileException {
		if(m.getMethodName().startsWith(Task.MainTaskMethodPrefix)) {
			String statement = "{ " + Runtime.ScheduleMainTaskMethod + "($0, \"" + m.getMethodName() + "\", $args); }";							
			if(Task.DEBUG)
				System.out.println("found schedule site: " + m.getMethodName() + " in " + method.getLongName() + "; replacing it with " + statement);
			m.replace(statement);
			stats.recordMainScheduleSite();
		} else if (m.getMethodName().startsWith(Task.NormalTaskMethodPrefix)) {
			String statement = "{ " + Runtime.ScheduleNormalTaskMethod + "($0, \"" + m.getMethodName() + "\", $args); }";
			if(Task.DEBUG)
				System.out.println("found schedule site: " + m.getMethodName() + " in  " + method.getLongName() + "; replacing it with " + statement);
			m.replace(statement);
			stats.recordScheduleSite();
		}
	}
	
};
