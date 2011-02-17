package scj;

public final class Runtime {
	
	public static final String ScheduleMainTaskMethod = Runtime.class.getCanonicalName() + ".scheduleMainTask";
	public static final String ScheduleNormalTaskMethod = Runtime.class.getCanonicalName() + ".scheduleNormalTask";

	public static void scheduleMainTask(Object receiver, String taskName, Object[] args) {
		assert taskName.startsWith(Task.MainTaskMethodPrefix);
		//System.out.println("schedule main task called: " + receiver + "." + taskName + "(" + args + ")");
		Task<?> task = (Task<?>)args[0];
		task.scheduleAsMainTask(receiver, taskName, args);
	}
	
	public static void scheduleNormalTask(Object receiver, String taskName, Object[] args) {
		assert taskName.startsWith(Task.NormalTaskMethodPrefix);
		//System.out.println("schedule normal task called: " + receiver + "." + taskName + "(" + args + ")");
		Task<?> task = (Task<?>)args[0];
		task.scheduleAsNormalTask(receiver, taskName, args);
	}
}

