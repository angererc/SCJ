package scj.compiler.optimizing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import scj.compiler.analysis.rw_sets.ReadWriteConflictDetector;
import sun.misc.Unsafe;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;

public class OptimizingUtil {

	public static void markFieldNotVolatile(CtField field) {
		int mods = field.getModifiers();
		if(Modifier.isVolatile(mods)) {
			int newMods = mods & ~Modifier.VOLATILE;				
			field.setModifiers(newMods);
		}
	}
	
	public static void markFieldVolatile(CtField field) {
		int mods = field.getModifiers();
		if(! Modifier.isVolatile(mods)) {
			int newMods = mods | Modifier.VOLATILE;				
			field.setModifiers(newMods);
		}
	}
	
	public static void markAllNonStaticFieldsNotVolatileAndStaticFieldsVolatile(CtClass ctclass) {
		//change all fields to not be volatile
		for(CtField field : ctclass.getDeclaredFields()) {
			int mods = field.getModifiers();
			if(Modifier.isStatic(mods)) {
				markFieldVolatile(field);
			} else {
				markFieldNotVolatile(field);
			}
		}
	}
	
	public static void markAllFieldsVolatile(CtClass ctclass) {
		//change all fields to volatile
		for(CtField field : ctclass.getDeclaredFields()) {
			markFieldVolatile(field);			
		}
	}
	
	private static CodeConverter arrayAccessReplacementConverter(ClassPool classPool) throws NotFoundException {
		CtClass scjRuntimeClass = classPool.get("scj.Runtime");
		
		CodeConverter converter = new CodeConverter();
		converter.replaceArrayAccess(scjRuntimeClass, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
		
		return converter;
	}
	
	public static void makeAllArrayAccessesVolatile(CtMethod method) throws NotFoundException, CannotCompileException {
		CodeConverter converter = arrayAccessReplacementConverter(method.getDeclaringClass().getClassPool());
		method.instrument(converter);
	}
	
	public static void makeAllArrayAccessesVolatile(CtClass ctclass) throws NotFoundException, CannotCompileException {
		CodeConverter converter = arrayAccessReplacementConverter(ctclass.getClassPool());
		//rewrite array accesses to call the Runtime array accessors
		for(CtMethod method : ctclass.getDeclaredMethods()) {
				method.instrument(converter);
		}
	}
	
	public static void makeAllFieldAccessesVolatile(CompilationStats stats, CtClass ctclass) throws CannotCompileException {
		for(CtMethod method : ctclass.getDeclaredMethods()) {
			makeAllFieldAccessesVolatile(stats, method);
		}
	}

	public static void makeAllFieldAccessesVolatile(CompilationStats stats, CtMethod ctMethod) throws CannotCompileException {
		//we know that bc is only used in calls to the conflict detector which is unused when rewriting all accesses; so we can just pass null
		makeConflictingFieldAccessesVolatile(
				new ReadWriteConflictDetector() {

					@Override
					public boolean readReadConflict(IMethod method, Integer bytecode) {
						return true;
					}

					@Override
					public boolean readWriteConflict(IMethod method, Integer bytecode) {
						return true;
					}

					@Override
					public boolean writeReadConflict(IMethod method, Integer bytecode) {
						return false;
					}

					@Override
					public boolean writeWriteConflict(IMethod method, Integer bytecode) {
						return false;
					}
					
				}, stats, ctMethod, null
		);
	}
	
	public static void makeConflictingFieldAccessesVolatile(ReadWriteConflictDetector conflicts, CompilationStats stats, CtMethod ctMethod, IBytecodeMethod bcMethod) throws CannotCompileException {
		//we can only map wala ssa instructions to javassist bytecodes through the bytecode index. However,
		//when we replace a field access with a call to unsafe, the bytecode indices change and subsequent javassist bytecodes don't match up with the wala
		//counterparts any more
		//therefore we first collect a list for each field access without modifying the code. The theory is that the ordering of the field accesses doesn't change
		//and then the bytecode indices aren't important any more
		//another solution would be to edit the javassist body backwards but I didn't find an easy way to do that...
		CollectVolatileFieldAccessesEditor collector = new CollectVolatileFieldAccessesEditor(conflicts, bcMethod);
		ctMethod.instrument(collector);
		
		VolatileFieldAccessesEditor editor = new VolatileFieldAccessesEditor(collector.fieldAccessNeedsVolatile, stats);		
		ctMethod.instrument(editor);
		
		assert collector.fieldAccessNeedsVolatile.size() == 0 : "not all field accesses have been seen?!?";
	}
	
	public static Class<?> runtimeClassForName(String className) {
		Class<?> runtimeClass;
		try {
			runtimeClass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return runtimeClass;
	}
	
	public static Field getField(Class<?> clazz, String fieldName) {
		Field[] fields = clazz.getDeclaredFields();
		for(int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if(f.getName().equals(fieldName))
				return f;
		}

		Class<?> superClazz = clazz.getSuperclass();
		if(superClazz != null) {
			return getField(superClazz, fieldName);
		} else {
			Exception e = new NoSuchFieldException(fieldName);
			throw new RuntimeException(e);
		}
	}
	
	public static IBytecodeMethod ctMethodToIBytecodeMethod(CtMethod ctMethod, IClass iclass) {
		String signature = ctMethod.getSignature();
		Selector selector = Selector.make(ctMethod.getName() + signature);
		IBytecodeMethod iMethod = (IBytecodeMethod)iclass.getMethod(selector);
		//System.out.println(iMethod + "; name=" + ctMethod.getName() + "; signature=" + signature + "; selector=" + selector);
		assert iMethod != null;
		return iMethod;
	}
	
	public static long unsafeFieldOffset(Field field) {
		long offset;
		Unsafe unsafe = scj.Runtime.unsafe;
		if(Modifier.isStatic(field.getModifiers())) {
			offset = unsafe.staticFieldOffset(field);
		} else {
			offset = unsafe.objectFieldOffset(field);
		}	
		return offset;
	}
	
	public static String primitiveTypeToUppercaseString(char prim) {
		switch (prim) {
        case 'Z' :
            return "Boolean";            
        case 'C' :
        	return "Char";
        case 'B' :
        	return "Byte";
        case 'S' :
        	return "Short";
        case 'I' :
        	return "Int";
        case 'J' :
        	return "Long";
        case 'F' :
        	return "Float";
        case 'D' :
        	return "Double";
        default :
            throw new RuntimeException("Unknown primitive type " + prim);
        }
	}
	
	public static String primitiveTypeToLowercaseString(char prim) {
		return primitiveTypeToUppercaseString(prim).toLowerCase();
	}
}
