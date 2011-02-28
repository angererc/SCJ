package scj.compiler.analysis.conflicting_bytecodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;

public class ParReadWriteConflicts {
	private Map<IMethod, Set<Integer>> parReadConflicts;
	private Map<IMethod, Set<Integer>> parWriteConflicts;
	
	void addParReadConflict(IMethod method, int bytecode) {
		Set<Integer> set = parReadConflicts.get(method);
		if(set == null) {
			set = new HashSet<Integer>();
			parReadConflicts.put(method, set);
		}
		set.add(bytecode);
	}
	
	void addParWriteConflict(IMethod method, int bytecode) {
		Set<Integer> set = parWriteConflicts.get(method);
		if(set == null) {
			set = new HashSet<Integer>();
			parWriteConflicts.put(method, set);
		}
		set.add(bytecode);
	}
	
	public boolean hasParallelReadConflict(IMethod method, int bytecode) {
		Set<Integer> set = parReadConflicts.get(method);
		return set == null ? false : set.contains(bytecode);
	}
	
	public boolean hasParallelWriteConflict(IMethod method, int bytecode) {
		Set<Integer> set = parWriteConflicts.get(method);
		return set == null ? false : set.contains(bytecode);
	}
}
