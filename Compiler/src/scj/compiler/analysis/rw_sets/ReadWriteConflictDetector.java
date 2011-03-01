package scj.compiler.analysis.rw_sets;

import com.ibm.wala.classLoader.IMethod;

public interface ReadWriteConflictDetector {

	public abstract boolean readReadConflict(IMethod method, Integer bytecode);

	public abstract boolean readWriteConflict(IMethod method, Integer bytecode);

	public abstract boolean writeReadConflict(IMethod method, Integer bytecode);

	public abstract boolean writeWriteConflict(IMethod method, Integer bytecode);

}