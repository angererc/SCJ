package scj.util;

import java.util.concurrent.Callable;

import scj.Task;

public class UtilityTasks {

	public static UtilityTasks get() {
		return new UtilityTasks();
	}
	
	public void scjTask_join(Task<Void> now) {
	}
	
	public <T> void scjTask_call(Task<Void> now, Callable<T> body) throws Exception {
		body.call();
	}
	
	public <T> void scjMainTask_call(Task<Void> now, Callable<T> body) throws Exception {
		body.call();
	}
}
