package scj.compiler.optimizing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import scj.compiler.CompilationStats;
import scj.compiler.analysis.rw_sets.ReadWriteConflictDetector;
import sun.misc.Unsafe;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
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
	
	private static CodeConverter conflictingArrayAccessReplacementConverter(CompilationStats stats, ClassPool classPool, ArrayList<Boolean> arrayAccessesNeedingVolatile) throws NotFoundException {
		CtClass scjRuntimeClass = classPool.get("scj.Runtime");
		
		ConflictingVolatileArraysCodeConverter converter = new ConflictingVolatileArraysCodeConverter();
		converter.replaceVolatileArrayAccess(stats, arrayAccessesNeedingVolatile, scjRuntimeClass, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
		
		return converter;
	}
	
	public static void makeAllArrayAccessesVolatile(CtBehavior behavior) throws NotFoundException, CannotCompileException {
		CodeConverter converter = arrayAccessReplacementConverter(behavior.getDeclaringClass().getClassPool());
		behavior.instrument(converter);
	}
	
	public static void makeAllArrayAccessesVolatile(CtClass ctclass) throws NotFoundException, CannotCompileException {
		CodeConverter converter = arrayAccessReplacementConverter(ctclass.getClassPool());
		//rewrite array accesses to call the Runtime array accessors
		for(CtMethod method : ctclass.getDeclaredMethods()) {
				method.instrument(converter);
		}
	}
	
	public static void makeAllFieldAccessesVolatile(CompilationStats stats, CtClass ctclass) throws CannotCompileException, NotFoundException {
		for(CtMethod method : ctclass.getDeclaredMethods()) {
			makeAllFieldAccessesVolatile(stats, method);
		}
	}

	public static void makeAllFieldAccessesVolatile(CompilationStats stats, CtBehavior ctBehavior) throws CannotCompileException, NotFoundException {
		//we know that bc is only used in calls to the conflict detector which is unused when rewriting all accesses; so we can just pass null
		makeConflictingFieldAndArrayAccessesVolatile(
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
						return true;
					}

					@Override
					public boolean writeWriteConflict(IMethod method, Integer bytecode) {
						return true;
					}
					
				}, stats, ctBehavior, null
		);
	}
	
	public static void makeConflictingFieldAndArrayAccessesVolatile(ReadWriteConflictDetector conflicts, CompilationStats stats, CtBehavior ctBehavior, IBytecodeMethod bcMethod) throws CannotCompileException, NotFoundException {
		//we can only map wala ssa instructions to javassist bytecodes through the bytecode index. However,
		//when we replace a field access with a call to unsafe, the bytecode indices change and subsequent javassist bytecodes don't match up with the wala
		//counterparts any more
		//therefore we first collect a list for each field access without modifying the code. The theory is that the ordering of the field accesses doesn't change
		//and then the bytecode indices aren't important any more
		//another solution would be to edit the javassist body backwards but I didn't find an easy way to do that...
		
		CollectConflictingFieldAndArrayAccessesEditor collector = new CollectConflictingFieldAndArrayAccessesEditor(conflicts, bcMethod);
		ctBehavior.instrument(collector);
		
		ConflictingVolatileFieldAccessesEditor editor = new ConflictingVolatileFieldAccessesEditor(collector.fieldAccessesNeedingVolatile, stats);		
		ctBehavior.instrument(editor);
		
		CodeConverter converter = conflictingArrayAccessReplacementConverter(stats, ctBehavior.getDeclaringClass().getClassPool(), collector.arrayAccessesNeedingVolatile);
		ctBehavior.instrument(converter);
		
		assert collector.fieldAccessesNeedingVolatile.size() == 0 : "not all field accesses have been seen?!?";
		assert collector.arrayAccessesNeedingVolatile.size() == 0 : "not all array accesses have been seen?!?";
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
	
	public static IBytecodeMethod ctBehaviorToIBytecodeMethod(CtBehavior ctBehavior, IClass iclass) {
		String signature = ctBehavior.getSignature();
		String name = ((ctBehavior instanceof CtConstructor) ? "<init>" : ctBehavior.getName());
		Selector selector = Selector.make(name + signature);
		IBytecodeMethod iMethod = (IBytecodeMethod)iclass.getMethod(selector);
		
		assert iMethod != null : "didn't find method with selector " + selector + " in class " + iclass + ": " + iclass.getDeclaredMethods();
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
