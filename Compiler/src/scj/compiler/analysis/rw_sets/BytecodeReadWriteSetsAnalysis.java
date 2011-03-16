package scj.compiler.analysis.rw_sets;

import java.util.HashMap;
import java.util.Map;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.demandpa.util.ArrayContents;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
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
import scj.compiler.analysis.rw_sets.ReadWriteSet;

public class BytecodeReadWriteSetsAnalysis {

	private final OptimizingCompilation compiler;
	
	private Map<CGNode, Map<Integer, ReadWriteSet>> rwSets;
	
	public BytecodeReadWriteSetsAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}
	
	public boolean containsReadWriteSet(CGNode node, Integer bytecode) {
		if(! rwSets.containsKey(node))
			return false;
		
		Map<Integer, ReadWriteSet> methodSets = rwSets.get(node);
		if(! methodSets.containsKey(bytecode))
			return false;
		
		return true;
	}
	
	public ReadWriteSet readWriteSet(CGNode node, Integer bytecode) {
		Map<Integer, ReadWriteSet> methodSets = rwSets.get(node);
		if(methodSets == null) {
			return ReadWriteSet.emptySet;
		}
		
		ReadWriteSet rwSet = methodSets.get(bytecode);
		if(rwSet == null) {
			return ReadWriteSet.emptySet;
		} else {
			return rwSet;
		}
	}

	private IField lookupField(FieldReference fieldRef) {
		IClass clazz = compiler.classHierarchy().lookupClass(fieldRef.getDeclaringClass());
		
		if(clazz == null) {
			System.err.println("Warning: didn't find class " + fieldRef.getDeclaringClass() + " for field " + fieldRef);
			return null;
		} else {
			return clazz.getField(fieldRef.getName());
		}
	}
	
	private Map<Integer, ReadWriteSet> getOrCreateNodeSets(CGNode node) {
		Map<Integer, ReadWriteSet> methodSets = rwSets.get(node);
		if(methodSets == null) {
			methodSets = new HashMap<Integer, ReadWriteSet>();
			rwSets.put(node, methodSets);
		}
		return methodSets;
	}
	
	private ReadWriteSet getOrCreateReadWriteSet(Map<Integer, ReadWriteSet> nodeSets, Integer bytecode) {
		ReadWriteSet rwSet = nodeSets.get(bytecode);
		if(rwSet == null) {
			rwSet = new ReadWriteSet();
			nodeSets.put(bytecode, rwSet);
		}
		return rwSet;
	}

	public void analyze() {
		rwSets = new HashMap<CGNode, Map<Integer, ReadWriteSet>>();
		
		final PointerAnalysis pa = compiler.pointerAnalysis();
		final HeapModel heap = pa.getHeapModel();

		nodesLoop: for(final CGNode node : compiler.callGraph()) {
			IR ir = node.getIR();
			if(ir == null) {
				assert node.getMethod().isNative();
				System.err.println("Warning: could not compute read/write set of native method " + node);
				continue nodesLoop;
			}
			IMethod iMethod = ir.getMethod();
			if(! (iMethod instanceof IBytecodeMethod)) {
				System.err.println("Warning: could not compute read/write set of method " + iMethod + "; not an instance of IBytecodeMethod.");
				continue nodesLoop;
			}
			
			IBytecodeMethod bcMethod = (IBytecodeMethod)ir.getMethod();
			
			if(bcMethod.getName().toString().contains("clearCachesOnClassRedefinition")) {
				System.out.println("halt");
			}

			final Map<Integer, ReadWriteSet> methodSets = this.getOrCreateNodeSets(node);
			
			SSAInstruction[] instructions = ir.getInstructions();
			instructionsLoop: for(int i = 0; i < instructions.length; i++) {

				final int bcIndex;
				try {
					bcIndex = bcMethod.getBytecodeIndex(i);
				} catch (InvalidClassFileException e) {
					throw new RuntimeException(e);
				}

				SSAInstruction instruction = instructions[i];
				if(instruction == null) 
					continue instructionsLoop;
				
				//get the read/write set for the current bytecode
				final ReadWriteSet rwSet = getOrCreateReadWriteSet(methodSets, bcIndex);
				
				instruction.visit(new Visitor() {

					@Override
					public void visitArrayLoad(SSAArrayLoadInstruction instruction) {	
						PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());	
						rwSet.addFieldReads(pa.getPointsToSet(pointer), ArrayContents.v());						
					}

					@Override
					public void visitArrayStore(SSAArrayStoreInstruction instruction) {
						PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());
						rwSet.addFieldWrites(pa.getPointsToSet(pointer), ArrayContents.v());						
					}

					@Override
					public void visitGet(SSAGetInstruction instruction) {
						IField field = lookupField(instruction.getDeclaredField());
						if(field == null)
							return;
						
						PointerKey pointer;
						if(instruction.isStatic()) {
							pointer = heap.getPointerKeyForStaticField(field);
						} else {
							pointer = heap.getPointerKeyForLocal(node, instruction.getRef());
						}
						rwSet.addFieldReads(pa.getPointsToSet(pointer), field);				
					}

					@Override
					public void visitPut(SSAPutInstruction instruction) {
						IField field = lookupField(instruction.getDeclaredField());
						if(field == null)
							return;
						
						PointerKey pointer;
						if(instruction.isStatic()) {
							pointer = heap.getPointerKeyForStaticField(field);
						} else {
							pointer = heap.getPointerKeyForLocal(node, instruction.getRef());
						}
						rwSet.addFieldWrites(pa.getPointsToSet(pointer), field);
					}

				});			
			}
		}
	}
}
