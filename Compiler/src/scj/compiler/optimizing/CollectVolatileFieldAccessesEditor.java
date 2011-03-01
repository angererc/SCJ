package scj.compiler.optimizing;

import java.util.ArrayList;

import com.ibm.wala.classLoader.IBytecodeMethod;

import scj.compiler.analysis.rw_sets.ReadWriteConflictDetector;

import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

public class CollectVolatileFieldAccessesEditor extends ExprEditor {

	private final ReadWriteConflictDetector conflicts;
	private final IBytecodeMethod bcMethod;
	
	ArrayList<Boolean> fieldAccessNeedsVolatile;
	
	//note: bcMethod can be null if the conflicts detector can deal with it!
	CollectVolatileFieldAccessesEditor(ReadWriteConflictDetector conflicts, IBytecodeMethod bcMethod) {
		this.conflicts = conflicts;
		this.bcMethod = bcMethod;
		fieldAccessNeedsVolatile = new ArrayList<Boolean>();
	}
	
	@Override
	public void edit(FieldAccess f) throws CannotCompileException {
		Integer bcIndex = f.indexOfBytecode();

		if(f.isReader()) {
			assert bcMethod == null || ! conflicts.writeReadConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a read so it shouldn't have a writeXYZ conflict";
			assert bcMethod == null || ! conflicts.writeWriteConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a read so it shouldn't have a writeXYZ conflict";;
			
			if(conflicts.readWriteConflict(bcMethod, bcIndex)) {
				fieldAccessNeedsVolatile.add(true);
			} else {
				fieldAccessNeedsVolatile.add(false);
			}
		} else {
			assert f.isWriter();						
			assert bcMethod == null || ! conflicts.readReadConflict(bcMethod, bcIndex) : "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a write so it shouldn't have a readXYZ conflict";
			assert bcMethod == null || ! conflicts.readWriteConflict(bcMethod, bcIndex): "access of field " + f.getClassName() + " " + f.getFieldName() + " in " + bcMethod + " (bc #" + bcIndex + ") is a write so it shouldn't have a readXYZ conflict";			
			if(conflicts.writeReadConflict(bcMethod, bcIndex) || conflicts.writeWriteConflict(bcMethod, bcIndex)) {
				fieldAccessNeedsVolatile.add(true);				
			} else {
				fieldAccessNeedsVolatile.add(false);
			}
		}					
	}

}