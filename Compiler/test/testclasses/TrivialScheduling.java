package testclasses;

import scj.Task;

public class TrivialScheduling {

	private boolean[] bools;
	private byte[] bytes;
	private char[] chars;
	private double[] doubles;
	private float[] floats;
	private int[] ints;
	private long[] longs;
	private Object[] objects;
	private short[] shorts;
	
	public void scjTask_subtask1(Task<Void> now) {
		System.out.println(bools[0] + ", " + bools[1]);
		System.out.println(bytes[0] + ", " + bytes[1]);
		System.out.println(chars[0] + ", " + chars[1]);
		System.out.println(doubles[0] + ", " + doubles[1]);
		System.out.println(floats[0] + ", " + floats[1]);
		System.out.println(ints[0] + ", " + ints[1]);
		System.out.println(longs[0] + ", " + longs[1]);
		System.out.println(objects[0] + ", " + objects[1]);
		System.out.println(shorts[0] + ", " + shorts[1]);
		
	}
	
//	public void scjTask_subtask2(Task<Void> now) {
//		System.out.println(names[1]);
//	}

	public void scjMainTask_main(Task<Void> now) {
		//System.out.println("main task");
		
		bools = new boolean[2];
		bools[0] = false;
		bools[1] = true;
		
		bytes = new byte[2];
		bytes[0] = 0;
		bytes[1] = 1;
		
		chars = new char[2];
		chars[0] = '0';
		chars[1] = '1';
		
		doubles = new double[2];
		doubles[0] = 0;
		doubles[1] = 1;
		
		floats = new float[2];
		floats[0] = 0;
		floats[1] = 1;
		
		ints = new int[2];
		ints[0] = 0;
		ints[1] = 1;
		
		longs = new long[2];
		longs[0] = 0;
		longs[1] = 1;
		
		objects = new Object[2];
		objects[0] = "0";
		objects[1] = "1";
		
		shorts = new short[2];
		shorts[0] = 0;
		shorts[1] = 1;
		
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
