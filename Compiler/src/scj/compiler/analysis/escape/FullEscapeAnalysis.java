package scj.compiler.analysis.escape;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.OptimizingCompilation;
import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.OrdinalSet;

public class FullEscapeAnalysis implements EscapeAnalysis {

	private final OptimizingCompilation compiler;
	private Set<InstanceKey> escapingInstanceKeys;
	
	/**
	 * The two input parameters define the program to analyze: the jars of .class files and the main class to start from.
	 */
	public FullEscapeAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}
	
	@Override
	public boolean instanceMayEscape(CGNode thisTask, CGNode otherTask, InstanceKey instance) {
		return escapingInstanceKeys.contains(instance);
	}
	
	public Set<InstanceKey> escapingInstanceKeys() {
		return escapingInstanceKeys;
	}
	
	/**
	 * The heart of the analysis.
	 * @throws CancelException
	 * @throws IllegalArgumentException
	 */
	@Override
	public void analyze() {
		
		ClassHierarchy classHierarchy = compiler.classHierarchy();
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
		Set<PointerKey> escapeAnalysisRoots = HashSetFactory.make();
		HeapModel heapModel = pa.getHeapModel();

		// 1) static fields
		for (IClass cls : classHierarchy) {
			Collection<IField> staticFields = cls.getDeclaredStaticFields();
			for (Iterator<IField> sfs = staticFields.iterator(); sfs.hasNext();) {
				IField sf = sfs.next();
				if (sf.getFieldTypeReference().isReferenceType()) {
					escapeAnalysisRoots.add(heapModel.getPointerKeyForStaticField(sf));
				}
			}
		}

		// 2) parameters flowing into task methods.
		for(IMethod taskMethod : compiler.allConcreteTaskMethods()) {
			IR ir = compiler.irForMethod(taskMethod);
			Set<CGNode> nodes = cg.getNodes(taskMethod.getReference());
			for(CGNode node : nodes) {
				for(int i = 0; i < taskMethod.getNumberOfParameters(); i++) {
					//don't count task objects as escaping because you can't write to them anyways; reduces size of the sets
					if(! WalaConstants.isTaskType(taskMethod.getParameterType(i))) {
						int ssaVariable = ir.getParameter(i);
						escapeAnalysisRoots.add(heapModel.getPointerKeyForLocal(node, ssaVariable));
					}
				}
			}
		}
		
		// 
		// compute escaping types: all types flowing to escaping roots and
		// all types transitively reachable through their fields.
		//
		escapingInstanceKeys = HashSetFactory.make();

		//
		// pass 1: get abstract objects (instance keys) for escaping locations
		//
		for (PointerKey root : escapeAnalysisRoots) {
			OrdinalSet<InstanceKey> objects = pa.getPointsToSet(root);
			for (InstanceKey obj : objects) {				
				escapingInstanceKeys.add(obj);
			}
		}

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

}
