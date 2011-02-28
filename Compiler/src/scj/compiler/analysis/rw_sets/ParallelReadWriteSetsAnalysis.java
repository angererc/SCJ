package scj.compiler.analysis.rw_sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

import scj.compiler.OptimizingCompilation;
import scj.compiler.analysis.escape.EscapeAnalysis;
import scj.compiler.analysis.reachability.ReachabilityAnalysis;
import scj.compiler.analysis.schedule.ScheduleAnalysis;

public class ParallelReadWriteSetsAnalysis {

	private final OptimizingCompilation compiler;

	private Map<CGNode, ReadWriteSet> parTaskReadWriteSets;
	private Map<CGNode, ReadWriteSet> parNodeReadWriteSets;
	
	public ParallelReadWriteSetsAnalysis(OptimizingCompilation compiler) {
		this.compiler = compiler;
	}

	public void analyze() {
		//for each task, compute the read write sets of parallel tasks on escaping instances
		this.collectTaskParallelReadWriteSets();
		//then compute for each node the set of all parallel instance accesses
		this.collectNodeParallelReadWriteSets();		
	}

	private void collectTaskParallelReadWriteSets() {
		ReadWriteSetsAnalysis rwSetAnalysis = compiler.readWriteSetsAnalysis();
		EscapeAnalysis escapeAnalysis = compiler.escapeAnalysis();
		ScheduleAnalysis saAnalysis = compiler.scheduleAnalysis();
		
		parTaskReadWriteSets = new HashMap<CGNode, ReadWriteSet>();
		
		for(CGNode taskNode : compiler.allTaskNodes()) {
			ReadWriteSet result = this.getOrCreateReadWriteSet(parTaskReadWriteSets, taskNode);
			
			for(CGNode parallelTaskNode : saAnalysis.parallelTasksFor(taskNode)) {
				ReadWriteSet parTaskReadWriteSet = rwSetAnalysis.taskReadWriteSet(parallelTaskNode);
				
				//reads
				for(Entry<InstanceKey, Set<IField>> readEntry : parTaskReadWriteSet.readEntries()) {					
					if(escapeAnalysis.escapes(readEntry.getKey())) {
						result.addFieldReads(readEntry.getKey(), readEntry.getValue());
					}
				}
				
				//writes
				for(Entry<InstanceKey, Set<IField>> writeEntry : parTaskReadWriteSet.writeEntries()) {					
					if(escapeAnalysis.escapes(writeEntry.getKey())) {
						result.addFieldWrites(writeEntry.getKey(), writeEntry.getValue());
					}
				}
			}
			
		}

	}
	
	private void collectNodeParallelReadWriteSets() {
		ReachabilityAnalysis reachability = compiler.reachabilityAnalysis();
		
		parNodeReadWriteSets = new HashMap<CGNode, ReadWriteSet>();
		
		for(CGNode node : compiler.taskForestCallGraph()) {
			ReadWriteSet result = this.getOrCreateReadWriteSet(parNodeReadWriteSets, node);
			
			for(CGNode reachingTask : reachability.reachingTasks(node)) {
				result.addAll(this.taskParallelReadWriteSet(reachingTask));
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

	public ReadWriteSet nodeParallelReadWriteSet(CGNode node) {
		ReadWriteSet set = parNodeReadWriteSets.get(node);
		return set == null ? ReadWriteSet.emptySet : set;
	}
	
	public ReadWriteSet taskParallelReadWriteSet(CGNode task) {
		ReadWriteSet set = parTaskReadWriteSets.get(task);
		return set == null ? ReadWriteSet.emptySet : set;
	}

}
