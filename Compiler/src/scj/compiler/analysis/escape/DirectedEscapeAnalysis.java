package scj.compiler.analysis.escape;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.rw_sets.ReadWriteSet;
import scj.compiler.analysis.rw_sets.ReadWriteSetsAnalysis;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.demandpa.util.ArrayContents;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.OrdinalSet;

public class DirectedEscapeAnalysis implements EscapeAnalysis {

	private final OptimizingCompilation compiler;
	private Map<CGNode, Set<InstanceKey>> escapingFromInstanceKeys;
	private Map<CGNode, Set<InstanceKey>> escapingToInstanceKeys;
	
	/**
	 * The two input parameters define the program to analyze: the jars of .class files and the main class to start from.
	 */
	public DirectedEscapeAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	public boolean escapesFrom(CGNode taskNode, InstanceKey key) {
		Set<InstanceKey> keys = escapingFromInstanceKeys.get(taskNode);
		if(keys == null)
			return false;
		else
			return keys.contains(key);
	}
	
	public boolean escapesTo(CGNode taskNode, InstanceKey key) {
		Set<InstanceKey> keys = escapingToInstanceKeys.get(taskNode);
		if(keys == null)
			return false;
		else
			return keys.contains(key);
	}
	
	@Override
	public boolean instanceMayEscape(CGNode thisTask, CGNode otherTask, InstanceKey instance) {
		boolean escapesFromThisTask = escapesFrom(thisTask, instance);
		boolean escapesToThisTask = escapesTo(thisTask, instance);
		boolean escapesFromOtherTask = escapesFrom(otherTask, instance);
		boolean escapesToOtherTask = escapesTo(otherTask, instance);
		return
			(escapesFromThisTask && escapesToOtherTask)
			||
			(escapesFromOtherTask && escapesToThisTask)
			||
			(escapesToThisTask && escapesToOtherTask);	
	}
	
	private void addAllReachables(PointerAnalysis pa, HeapModel heapModel, Set<InstanceKey> escapingInstanceKeys) {
		//
		// passes 2+: get fields of escaping keys, and add pointed-to keys
		//
		Set<InstanceKey> newKeys = HashSetFactory.make();
		do {
			newKeys.clear();
			for (InstanceKey key : escapingInstanceKeys) {
				IClass type = key.getConcreteType();
				if (type.isReferenceType()) {
					if (type.isArrayClass()) {
						if (((ArrayClass) type).getElementClass() != null) {
							PointerKey fk = heapModel.getPointerKeyForArrayContents(key);
							OrdinalSet<InstanceKey> fobjects = pa.getPointsToSet(fk);
							for (InstanceKey fobj : fobjects) {
								if (!escapingInstanceKeys.contains(fobj)) {
									newKeys.add(fobj);
								}
							}
						}
					} else {
						Collection<IField> fields = type.getAllInstanceFields();
						for (IField f : fields) {
							if (f.getFieldTypeReference().isReferenceType()) {
								PointerKey fk = heapModel.getPointerKeyForInstanceField(key, f);
								OrdinalSet<InstanceKey> fobjects = pa.getPointsToSet(fk);
								for (InstanceKey fobj : fobjects) {
									if (!escapingInstanceKeys.contains(fobj)) {
										newKeys.add(fobj);
									}
								}
							}
						}
					}
				}
			}
			escapingInstanceKeys.addAll(newKeys);
		} while (!newKeys.isEmpty());
	}
	
	private void analyzeTask(PointerAnalysis pa, HeapModel heapModel, ReadWriteSetsAnalysis rwSets, CGNode taskNode) {
		//analyze reads and writes
		Set<PointerKey> escapeToRoots = HashSetFactory.make();
		Set<PointerKey> escapeFromRoots = HashSetFactory.make();
		
		//(1) reads of static fields escape to the task node
		ReadWriteSet rwSet = rwSets.taskReadWriteSet(taskNode);
		
		for(Entry<InstanceKey, Set<IField>> rwEntry : rwSet.readEntries()) {
			for(IField field : rwEntry.getValue()) {
				if(field != ArrayContents.v() && field.isStatic()) {
					escapeToRoots.add(heapModel.getPointerKeyForStaticField(field));
				}
			}
		}
		
		//(2) writes to static fields escape from task node
		for(Entry<InstanceKey, Set<IField>> rwEntry : rwSet.writeEntries()) {
			for(IField field : rwEntry.getValue()) {
				if(field != ArrayContents.v() && field.isStatic()) {
					escapeFromRoots.add(heapModel.getPointerKeyForStaticField(field));
				}
			}
		}
		
		//(3) params passed to task node escape to task node
		IMethod taskMethod = taskNode.getMethod();
		IR ir = compiler.irForMethod(taskMethod);
		for(int i = 0; i < taskMethod.getNumberOfParameters(); i++) {
			//don't count task objects as escaping because you can't write to them anyways; reduces size of the sets
			//TODO hm, they have the result field, so I guess that's not really correct... unless we say that a task field must only be written once
			if(! WalaConstants.isTaskType(taskMethod.getParameterType(i))) {
				int ssaVariable = ir.getParameter(i);
				escapeToRoots.add(heapModel.getPointerKeyForLocal(taskNode, ssaVariable));
			}
		}
		
		//(4) params passed to other tasks in theory would escape the taskNode, too
		//however, since child tasks are not parallel to this task, we don't really need to do that
		//TODO think about if that's really true...
		
		//(5) get instance keys from pointers
		Set<InstanceKey> escapingFrom = HashSetFactory.make();
		Set<InstanceKey> escapingTo = HashSetFactory.make();
		
		for (PointerKey root : escapeToRoots) {
			OrdinalSet<InstanceKey> objects = pa.getPointsToSet(root);
			for (InstanceKey obj : objects) {				
				escapingTo.add(obj);
			}
		}
		
		for (PointerKey root : escapeFromRoots) {
			OrdinalSet<InstanceKey> objects = pa.getPointsToSet(root);
			for (InstanceKey obj : objects) {				
				escapingFrom.add(obj);
			}
		}
		
		//find transitive closure and store result
		addAllReachables(pa, heapModel, escapingFrom);
		addAllReachables(pa, heapModel, escapingTo);
		this.escapingFromInstanceKeys.put(taskNode, escapingFrom);
		this.escapingToInstanceKeys.put(taskNode, escapingTo);
	}
	
	/**
	 * The heart of the analysis.
	 * @throws CancelException
	 * @throws IllegalArgumentException
	 */
	@Override
	public void analyze() {
		
		//
		// extract data for analysis
		//
		CallGraph cg = compiler.callGraph();
		PointerAnalysis pa = compiler.pointerAnalysis();

		//
		// collect all places where objects can escape their creating task:
		// 1) all static fields
		// 2) arguments to task schedule sites
		//
		HeapModel heapModel = pa.getHeapModel();

		ReadWriteSetsAnalysis rwSetsAnalysis = compiler.readWriteSetsAnalysis();
		
		this.escapingFromInstanceKeys = new HashMap<CGNode, Set<InstanceKey>>();
		this.escapingToInstanceKeys = new HashMap<CGNode, Set<InstanceKey>>();
		
		//
		for(IMethod taskMethod : compiler.allConcreteTaskMethods()) {
			for(CGNode taskNode : cg.getNodes(taskMethod.getReference())) {
				this.analyzeTask(pa, heapModel, rwSetsAnalysis, taskNode);
			}
		}

	}

}
