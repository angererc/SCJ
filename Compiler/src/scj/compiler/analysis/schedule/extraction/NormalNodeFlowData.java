package scj.compiler.analysis.schedule.extraction;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import scj.compiler.wala.util.SimpleGraph;

import com.ibm.wala.ssa.ISSABasicBlock;

public class NormalNodeFlowData extends FlowData {

	private static boolean DEBUG = false;

	//null for fresh sets; in meet operations, we will call mergeWithForward edge etc multiple times which will instantiate those
	//in node analysis operations we call duplicate which instantiates those
	//in the initial setup code we call initEmpty()	
	private Set<LoopContext> loopContexts;
	private Set<LoopContext> currentContexts;

	//a phi node in a certain context can point to multiple task variables from different contexts
	protected HashMap<PhiVariable, Set<TaskVariable>> phiMappings;
	private SimpleGraph<TaskVariable> partialSchedule; 

	protected NormalNodeVisitor visitor;

	//just to improve debugging
	final ISSABasicBlock basicBlock;

	NormalNodeFlowData(ISSABasicBlock basicBlock) {
		this.basicBlock = basicBlock;
	}

	public String getStatsString() {
		StringBuilder stats = new StringBuilder();
		if(loopContexts != null) {
			stats.append("#loop contexts: " + loopContexts.size() + ";");
		}
		if(currentContexts != null) {
			 stats.append("# current contexts: " + currentContexts.size() + ";");
		}
		if(phiMappings != null) {
			stats.append("# phi mappings " + phiMappings.size() + ";");
		}
		if(partialSchedule != null) {
			stats.append("# nodes " + partialSchedule.getNumberOfNodes() + ";");
		}
		return stats.toString();
	}
	
	public SimpleGraph<TaskVariable> partialSchedule() {
		return partialSchedule;
	}

	public NormalNodeVisitor createNodeVisitor(TaskScheduleSolver solver) {
		return new NormalNodeVisitor(solver, this);
	}

	public NormalNodeVisitor nodeVisitor(TaskScheduleSolver solver) {
		if(visitor == null)
			visitor = this.createNodeVisitor(solver);

		return visitor;
	}

	boolean isTask(int ssaVariable) {
		Iterator<TaskVariable> nodes = this.partialSchedule.iterator();
		while(nodes.hasNext()) {
			TaskVariable node = nodes.next();
			if(node.ssaVariable == ssaVariable)
				return true;
		}

		return false;
	}

	Set<LoopContext> loopContexts() {
		return loopContexts;
	}

	protected void addAllCurrentLoopContexts(Set<LoopContext> ctxts) {
		this.loopContexts.addAll(ctxts);
		this.currentContexts.addAll(ctxts);
	}
	
	void initEmpty() {
		assert this.isInitial();
		this.loopContexts = new HashSet<LoopContext>();
		this.currentContexts = new HashSet<LoopContext>();
		this.partialSchedule = new SimpleGraph<TaskVariable>();	
	}

	NormalNodeFlowData duplicate(ISSABasicBlock forBasicBlock) {
		NormalNodeFlowData data = new NormalNodeFlowData(forBasicBlock);
		data.copyState(this);
		return data;
	}

	//never returns null
	Set<TaskVariable> taskVariableForSSAVariable(LoopContext ctxt, int ssaVariable) {

		if(phiMappings != null) {
			Set<TaskVariable> tasks = phiMappings.get(new PhiVariable(ctxt, ssaVariable));
			if(tasks != null)
				return tasks;
		}

		//ssaVariable is not a phi node, then it must be a scheduled task
		TaskVariable scheduledTask = new TaskVariable(ctxt, ssaVariable);
		if(partialSchedule.containsNode(scheduledTask)) {
			return Collections.singleton(scheduledTask);
		} else {
			return Collections.emptySet();
		}
	}

	void addCurrentLoopContext(LoopContext lc) {
		if(DEBUG)
			System.out.println("NormalNodeFlowData: node of block " + basicBlock.getGraphNodeId() + " adding loop context: " + lc);
		this.loopContexts.add(lc);
		this.currentContexts.add(lc);
	}

	void addFormalTaskParameter(int ssaVariable) {
		partialSchedule.addNode(new TaskVariable(LoopContext.emptyLoopContext(), ssaVariable));
	}

	void addTaskScheduleSite(TaskVariable variable) {
		if(DEBUG)
			System.out.println("NormalNodeFlowData: node of block " + basicBlock.getGraphNodeId() + " adding task variable: " + variable);
		partialSchedule.addNode(variable);
	}

	void addHappensBeforeEdge(TaskVariable src, TaskVariable trgt) {
		if(DEBUG)
			System.out.println("NormalNodeFlowData: node of block " + basicBlock.getGraphNodeId() + " adding hb edge: " + src + "->" + trgt);		
		this.partialSchedule.addEdge(src, trgt);
	}

	boolean isInitial() {
		assert  (loopContexts != null && partialSchedule != null) 
		|| (loopContexts == null && phiMappings == null && partialSchedule == null);
		return loopContexts == null;
	}

	void killHappensBeforeRelationshipsContaining(TaskVariable task) {
		this.partialSchedule.removeAllIncidentEdges(task);		
	}

	void killOldLoopContexts(TaskScheduleSolver solver) {
		HashSet<LoopContext> toKill = new HashSet<LoopContext>();
		for(LoopContext lc : currentContexts) {
			if( ! lc.isCurrentAtBlock(basicBlock, solver)) {
				toKill.add(lc);
			}
		}
		currentContexts.removeAll(toKill);
	}

	protected void mergeState(NormalNodeFlowData other) {
		assert ! other.isInitial();		

		this.loopContexts.addAll(other.loopContexts);
		this.currentContexts.addAll(other.currentContexts);
		this.partialSchedule.addAllNodesAndEdges(other.partialSchedule);

		if (other.phiMappings != null) {
			for(Entry<PhiVariable, Set<TaskVariable>> entry : other.phiMappings.entrySet()) {
				this.addAllPhiVariables(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	boolean stateEquals(FlowData otherData) {
		assert otherData instanceof NormalNodeFlowData;
		NormalNodeFlowData other = (NormalNodeFlowData)otherData;
		assert ! other.isInitial();

		return ! isInitial() 
		&& other.loopContexts.equals(loopContexts) 
		&& other.currentContexts.equals(currentContexts)
		&& other.partialSchedule.stateEquals(partialSchedule) 
		&& (phiMappings == null ? 
				other.phiMappings == null 
				: (other.phiMappings != null && other.phiMappings.equals(phiMappings)));
	}

	protected void addAllPhiVariables(PhiVariable phi, Collection<TaskVariable> toAdd) {
		if (phiMappings == null)
			phiMappings = new HashMap<PhiVariable, Set<TaskVariable>>();

		Set<TaskVariable> tasks = phiMappings.get(phi);
		if(tasks == null) {
			tasks = new HashSet<TaskVariable>();
			phiMappings.put(phi, tasks);			
		}
		tasks.addAll(toAdd);
	}

	@Override
	public void copyState(FlowData v) {
		assert(v instanceof NormalNodeFlowData);
		assert v != null;
		NormalNodeFlowData other = (NormalNodeFlowData)v;
		assert ! other.isInitial();
		//when duplicating, the basic blocks can be different
		//assert this.isInitial() || other.basicBlock.equals(basicBlock);

		this.loopContexts = new HashSet<LoopContext>(other.loopContexts);
		this.currentContexts = new HashSet<LoopContext>(other.currentContexts);
		this.partialSchedule = new SimpleGraph<TaskVariable>();
		this.partialSchedule.addAllNodesAndEdges(other.partialSchedule);

		if (other.phiMappings != null) {
			this.phiMappings = new HashMap<PhiVariable, Set<TaskVariable>>();
			for(Entry<PhiVariable, Set<TaskVariable>> entry : other.phiMappings.entrySet()) {
				this.addAllPhiVariables(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	public String toString() {
		return "Node Flow Data " + basicBlock.getGraphNodeId();
	}

	public void print(PrintStream out) {
		out.println("Loop Contexts: " + loopContexts);
		out.println("Current Loop Contexts: " + this.currentContexts);
		out.println("Scheduled Tasks: " + this.partialSchedule.nodesToString());
		out.println("Phi Mappings: " + phiMappings);
		out.println("Partial schedule: " + this.partialSchedule);
	}

	//return only those loop contexts that are valid at the current basic block
	//that is, no loop contexts with edges that point to a block that is not reachable from our block
	//if we don't do that, we create spurious tasks at schedule sites that are then considered parallel
	//however, we cannot simply kill all loop nodes because there are still task variables that have been created
	//in those old loop contexts.
	public Set<LoopContext> currentLoopContexts() {
		return currentContexts;		
	}


}
