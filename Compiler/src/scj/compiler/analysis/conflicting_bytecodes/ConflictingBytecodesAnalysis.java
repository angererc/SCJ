package scj.compiler.analysis.conflicting_bytecodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.demandpa.util.ArrayContents;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAInstruction.Visitor;
import com.ibm.wala.types.FieldReference;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.rw_sets.ParallelReadWriteSetsAnalysis;
import scj.compiler.analysis.rw_sets.ReadWriteSet;

public class ConflictingBytecodesAnalysis {

	private final OptimizingCompilation compiler;
	private Map<IMethod, Set<Integer>> parReadConflicts;
	private Map<IMethod, Set<Integer>> parWriteConflicts;

	public ConflictingBytecodesAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	private IField lookupField(FieldReference fieldRef) {
		IClass clazz = compiler.classHierarchy().lookupClass(fieldRef.getDeclaringClass());
		return clazz.getField(fieldRef.getName());
	}

	public void analyze() {
		parReadConflicts = new HashMap<IMethod, Set<Integer>>();
		parWriteConflicts = new HashMap<IMethod, Set<Integer>>();

		final ParallelReadWriteSetsAnalysis parRWSetsAnalysis = compiler.getOrCreateParallelReadWriteSetsAnalysis();
		final PointerAnalysis pa = compiler.pointerAnalysis();
		final HeapModel heap = pa.getHeapModel();

		for(final CGNode node : compiler.callGraph()) {
			IR ir = node.getIR();
			final IBytecodeMethod bcMethod = (IBytecodeMethod)ir.getMethod();

			final ReadWriteSet parRWSet = parRWSetsAnalysis.nodeParallelReadWriteSet(node);

			SSAInstruction[] instructions = ir.getInstructions();
			for(int i = 0; i < instructions.length; i++) {

				final int bcIndex;
				try {
					bcIndex = bcMethod.getBytecodeIndex(i);
				} catch (InvalidClassFileException e) {
					throw new RuntimeException(e);
				}

				SSAInstruction instruction = instructions[i];
				instruction.visit(new Visitor() {

					@Override
					public void visitArrayLoad(SSAArrayLoadInstruction instruction) {	
						PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());				
						for(InstanceKey instance : pa.getPointsToSet(pointer)) {						
							if(parRWSet.fieldReads(instance).contains(ArrayContents.v())) {							
								addParReadConflict(bcMethod, bcIndex);
							}
						}
					}

					@Override
					public void visitArrayStore(SSAArrayStoreInstruction instruction) {
						PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());					
						for(InstanceKey instance : pa.getPointsToSet(pointer)) {
							if(parRWSet.fieldWrites(instance).contains(ArrayContents.v())) {
								addParWriteConflict(bcMethod, bcIndex);
							}
						}
					}

					@Override
					public void visitGet(SSAGetInstruction instruction) {
						IField field = lookupField(instruction.getDeclaredField());
						PointerKey pointer;
						if(instruction.isStatic()) {
							pointer = heap.getPointerKeyForStaticField(field);
						} else {
							pointer = heap.getPointerKeyForLocal(node, instruction.getRef());
						}

						for(InstanceKey instance : pa.getPointsToSet(pointer)) {
							if(parRWSet.fieldReads(instance).contains(field)) {
								addParReadConflict(bcMethod, bcIndex);
							}
						}
					}

					@Override
					public void visitPut(SSAPutInstruction instruction) {
						IField field = lookupField(instruction.getDeclaredField());
						PointerKey pointer;
						if(instruction.isStatic()) {
							pointer = heap.getPointerKeyForStaticField(field);
						} else {
							pointer = heap.getPointerKeyForLocal(node, instruction.getRef());
						}

						for(InstanceKey instance : pa.getPointsToSet(pointer)) {
							if(parRWSet.fieldWrites(instance).contains(field)) {
								addParWriteConflict(bcMethod, bcIndex);
							}
						}
					}

				});			
			}
		}
	}

	private void addParReadConflict(IMethod method, int bytecode) {
		Set<Integer> set = parReadConflicts.get(method);
		if(set == null) {
			set = new HashSet<Integer>();
			parReadConflicts.put(method, set);
		}
		set.add(bytecode);
	}

	private void addParWriteConflict(IMethod method, int bytecode) {
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
