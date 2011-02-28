package scj.compiler.analysis.rw_sets;

import java.util.HashMap;
import java.util.Map;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.demandpa.util.ArrayContents;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAInstruction.Visitor;
import com.ibm.wala.types.FieldReference;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.reachability.ReachabilityAnalysis;

public class ReadWriteSetsAnalysis {

	private final OptimizingCompilation compiler;

	private Map<CGNode, ReadWriteSet> nodeReadWriteSets;
	private Map<CGNode, ReadWriteSet> taskReadWriteSets;
	
	public ReadWriteSetsAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	public void analyze() {
		this.collectNodeReadWriteSets();
		this.collectTaskReadWriteSets();		
	}

	private IField lookupField(FieldReference fieldRef) {
		IClass clazz = compiler.classHierarchy().lookupClass(fieldRef.getDeclaringClass());
		return clazz.getField(fieldRef.getName());
	}

	private void collectNodeReadWriteSets() {
		nodeReadWriteSets = new HashMap<CGNode, ReadWriteSet>();
		
		final PointerAnalysis pa = compiler.pointerAnalysis();
		final HeapModel heap = pa.getHeapModel();
		for(final CGNode node : compiler.callGraph()) {
			IR ir = node.getIR();
			final ReadWriteSet readWriteSet = this.getOrCreateReadWriteSet(nodeReadWriteSets, node);			
			ir.visitNormalInstructions(new Visitor() {

				@Override
				public void visitArrayLoad(SSAArrayLoadInstruction instruction) {					
					PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());					
					for(InstanceKey instance : pa.getPointsToSet(pointer)) {
						readWriteSet.addFieldRead(instance, ArrayContents.v());
					}
				}

				@Override
				public void visitArrayStore(SSAArrayStoreInstruction instruction) {
					PointerKey pointer = heap.getPointerKeyForLocal(node, instruction.getArrayRef());					
					for(InstanceKey instance : pa.getPointsToSet(pointer)) {
						readWriteSet.addFieldWrite(instance, ArrayContents.v());
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
						readWriteSet.addFieldRead(instance, field);
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
						readWriteSet.addFieldWrite(instance, field);
					}
				}

			});
		}
	}

	private void collectTaskReadWriteSets() {
		taskReadWriteSets = new HashMap<CGNode, ReadWriteSet>();
		
		ReachabilityAnalysis reachability = compiler.getOrCreateReachabilityAnalysis();
		
		for(CGNode taskNode : compiler.allTaskNodes()) {
			ReadWriteSet taskReadWriteSet = this.getOrCreateReadWriteSet(taskReadWriteSets, taskNode);
			for(CGNode reachable : reachability.reachableNodes(taskNode)) {
				ReadWriteSet nodeReadWriteSet = this.nodeReadWriteSet(reachable);				
				taskReadWriteSet.addAll(nodeReadWriteSet);				
			}
		}
	}

	private ReadWriteSet getOrCreateReadWriteSet(Map<CGNode, ReadWriteSet> sets, CGNode node) {
		ReadWriteSet set = sets.get(node);
		if(set == null) {
			set = new ReadWriteSet();
			sets.put(node, set);
		}
		return set;
	}

	public ReadWriteSet nodeReadWriteSet(CGNode node) {
		ReadWriteSet set = nodeReadWriteSets.get(node);
		return set == null ? ReadWriteSet.emptySet : set;		
	}

	public ReadWriteSet taskReadWriteSet(CGNode task) {
		ReadWriteSet set = taskReadWriteSets.get(task);
		return set == null ? ReadWriteSet.emptySet : set;
	}

}
