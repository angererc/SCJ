package scj;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

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
	
	// volatile array support
	private static Unsafe getUnsafe() {
        Unsafe unsafe = null;
        try {
            Class<?> uc = Unsafe.class;
            Field[] fields = uc.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals("theUnsafe")) {
                    fields[i].setAccessible(true);
                    unsafe = (Unsafe) fields[i].get(uc);
                    break;
                }
            }
        } catch (Exception ex) {
        	ex.printStackTrace();
        }
        return unsafe;
    }
	private static final Unsafe unsafe = getUnsafe();

    private static final int base = unsafe.arrayBaseOffset(Object[].class);
    private static final int scale = unsafe.arrayIndexScale(Object[].class);
    
    private static long rawIndex(int i) {
        return base + (long) i * scale;
    }
    
	public static Object arrayReadObject(Object array, int i) {
		if (i < 0 || i >= ((Object[])array).length)
            throw new IndexOutOfBoundsException("index " + i);
		
		System.out.println("arrayReadObject: " + array + " i=" + i);
		return unsafe.getObjectVolatile(array, rawIndex(i));
	}
	
	public static void arrayWriteObject(Object array, int i, Object newValue) {
		if (i < 0 || i >= ((Object[])array).length)
            throw new IndexOutOfBoundsException("index " + i);
		System.out.println("arrayWriteObject: " + array + " i=" + i + " val=" + newValue);
		unsafe.putObjectVolatile(array, rawIndex(i), newValue);
	}
	
}

