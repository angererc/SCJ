package scj.compiler.optimizing;

import java.lang.reflect.Field;
import java.util.ArrayList;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

public class ConflictingVolatileFieldAccessesEditor extends ExprEditor {

	private final ArrayList<Boolean> volatileFieldAccesses;
	private final CompilationStats stats;
	
	ConflictingVolatileFieldAccessesEditor(ArrayList<Boolean> volatileFieldAccesses, CompilationStats stats) {
		this.volatileFieldAccesses = volatileFieldAccesses;
		this.stats = stats;
	}
	
	@Override
	public void edit(FieldAccess f) throws CannotCompileException {	
		//System.out.println("editing field access " + f.getFieldName() + " of declaring class " + f.getClassName());
		
		boolean needsVolatile = volatileFieldAccesses.remove(0);
		//we make static fields volatile by default; no need to instrument them
		if(!f.isStatic() && needsVolatile) {
			
			String fieldsClassName = f.getClassName();
			Class<?> runtimeClass = OptimizingUtil.runtimeClassForName(fieldsClassName);
			assert runtimeClass != null;

			String fieldName = f.getFieldName();
			Field field = OptimizingUtil.getField(runtimeClass, fieldName);
			
			long offset = OptimizingUtil.unsafeFieldOffset(field);
			CtClass fieldType;
			try {
				fieldType = f.getField().getType();			
			} catch (NotFoundException e) {
				throw new RuntimeException(e);
			}
			
			// we know that the field is not static. if field type is an array it is an object
			if(f.isReader()) {
				stats.recordInstrumentedRead(f);
				
				if(fieldType.isPrimitive()) {
					assert !fieldType.isArray();
					CtPrimitiveType primType = (CtPrimitiveType)fieldType;
					String methodString = OptimizingUtil.primitiveTypeToUppercaseString(primType.getDescriptor());
					f.replace("$_ = scj.Runtime.unsafe.get" + methodString + "Volatile((Object)$0, " + offset + "l);");
				} else {
					String castPostfix = "";
					while(fieldType.isArray()) {
						castPostfix = castPostfix + "[]";
						try {
							fieldType = fieldType.getComponentType();
						} catch (NotFoundException e) {
							throw new RuntimeException(e);
						}
					}
					f.replace("$_ = (" + fieldType.getName() + castPostfix + ")scj.Runtime.unsafe.getObjectVolatile((Object)$0, " + offset + "l);");
				}
			} else {
				assert f.isWriter();
				stats.recordInstrumentedWrite(f);
				
				if(fieldType.isPrimitive()) {
					assert !fieldType.isArray();
					CtPrimitiveType primType = (CtPrimitiveType)fieldType;
					String methodString = OptimizingUtil.primitiveTypeToUppercaseString(primType.getDescriptor());
					String typeString = OptimizingUtil.primitiveTypeToLowercaseString(primType.getDescriptor());
					f.replace("scj.Runtime.unsafe.put" + methodString + "Volatile((Object)$0, " + offset + "l,(" + typeString + ")$1);");
				} else {				
					f.replace("scj.Runtime.unsafe.putObjectVolatile($0, " + offset + "l, $1);");
				}
			}
		} else {
			if(f.isReader()) {
				stats.recordUninstrumentedRead(f);
			} else {
				assert f.isWriter();
				stats.recordUninstrumentedWrite(f);
			}
		}
	}

}