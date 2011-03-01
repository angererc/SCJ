package scj.compiler.optimizing;

import java.util.ArrayList;

import com.ibm.wala.classLoader.IBytecodeMethod;

import scj.compiler.analysis.rw_sets.ReadWriteConflictDetector;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import static javassist.bytecode.Opcode.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

public class CollectConflictingFieldAndArrayAccessesEditor extends ExprEditor  {

	private final ReadWriteConflictDetector conflicts;
	private final IBytecodeMethod bcMethod;

	ArrayList<Boolean> fieldAccessesNeedingVolatile;
	ArrayList<Boolean> arrayAccessesNeedingVolatile;

	//note: bcMethod can be null if the conflicts detector can deal with it!
	CollectConflictingFieldAndArrayAccessesEditor(ReadWriteConflictDetector conflicts, IBytecodeMethod bcMethod) {
		this.conflicts = conflicts;
		this.bcMethod = bcMethod;
		fieldAccessesNeedingVolatile = new ArrayList<Boolean>();
		arrayAccessesNeedingVolatile = new ArrayList<Boolean>();
	}

	@Override
	public boolean doit(CtClass clazz, MethodInfo minfo) throws CannotCompileException {
		CodeAttribute codeAttr = minfo.getCodeAttribute();
		if (codeAttr != null) {
			CodeIterator iterator = codeAttr.iterator();

			while (iterator.hasNext()) {

				int pos;
				try {
					pos = iterator.next();
				} catch (BadBytecode e) {
					throw new RuntimeException(e);
				}

				int c = iterator.byteAt(pos);

				if (c == AALOAD || c == BALOAD || c == CALOAD || c == DALOAD
						|| c == FALOAD || c == IALOAD || c == LALOAD
						|| c == SALOAD) {
					if(conflicts.readWriteConflict(bcMethod, pos)) {
						arrayAccessesNeedingVolatile.add(true);
					} else {
						arrayAccessesNeedingVolatile.add(false);
					}
				} else if (c == AASTORE || c == BASTORE || c == CASTORE
						|| c == DASTORE || c == FASTORE || c == IASTORE
						|| c == LASTORE || c == SASTORE) {
					if(conflicts.writeReadConflict(bcMethod, pos) || conflicts.writeWriteConflict(bcMethod, pos)) {
						arrayAccessesNeedingVolatile.add(true);
					} else {
						arrayAccessesNeedingVolatile.add(false);
					}
				}

			}
		}
		return super.doit(clazz, minfo);
	}

	@Override
	public void edit(FieldAccess f) throws CannotCompileException {
		Integer bcIndex = f.indexOfBytecode();

		if(f.isReader()) {
			assert bcMethod == null || ! conflicts.writeReadConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a read so it shouldn't have a writeXYZ conflict";
			assert bcMethod == null || ! conflicts.writeWriteConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a read so it shouldn't have a writeXYZ conflict";;

			if(conflicts.readWriteConflict(bcMethod, bcIndex)) {
				fieldAccessesNeedingVolatile.add(true);
			} else {
				fieldAccessesNeedingVolatile.add(false);
			}
		} else {
			assert f.isWriter();						
			assert bcMethod == null || ! conflicts.readReadConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a write so it shouldn't have a readXYZ conflict";
			assert bcMethod == null || ! conflicts.readWriteConflict(bcMethod, bcIndex): "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a write so it shouldn't have a readXYZ conflict";			
			if(conflicts.writeReadConflict(bcMethod, bcIndex) || conflicts.writeWriteConflict(bcMethod, bcIndex)) {
				fieldAccessesNeedingVolatile.add(true);				
			} else {
				fieldAccessesNeedingVolatile.add(false);
			}
		}					
	}

}