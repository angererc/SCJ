package testclasses;

import scj.Task;

public class TrivialScheduling {

	private String[] names;
	
	public void scjTask_subtask1(Task<Void> now) {
		//System.out.println(names[0]);
		String s = names[0];
	}
	
//	public void scjTask_subtask2(Task<Void> now) {
//		System.out.println(names[1]);
//	}

	public void scjMainTask_main(Task<Void> now) {
		//System.out.println("main task");
		
		names = new String[2];
		names[0] = "Subtask1";
		//names[1] = "Subtask2";
		
		Task<Void> s1 = new Task<Void>();
		this.scjTask_subtask1(s1);
		
//		Task<Void> s2 = new Task<Void>();
//		this.scjTask_subtask2(s2);
//		
//		s1.hb(s2);
		
	}
	public void doStuff() {
		//System.out.println("Before main task");
		
		Task<Void> main = new Task<Void>();
		this.scjMainTask_main(main);
		
		//System.out.println("After main task");
	}
	
	public static void main(String[] args) {
		TrivialScheduling s = new TrivialScheduling();
		s.doStuff();
	}
}
