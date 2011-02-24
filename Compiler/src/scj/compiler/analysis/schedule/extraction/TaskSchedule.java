package scj.compiler.analysis.schedule.extraction;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

//the abstract schedule of a task
public final class TaskSchedule<TV, SM extends TaskScheduleManager<TV>> {
	
	public enum Relation {
		singleton,
		happensBefore,
		happensAfter,
		ordered,
		unordered,
		unknown; //an unknown relation can happen after the task schedule has been created and the schedule analysis runs. It represents the case where one or both task variables are phis
		
		public Relation inverse() {
			switch(this) {
			case singleton: return singleton;
			case happensBefore: return happensAfter;
			case happensAfter: return happensBefore;
			case ordered: return ordered;
			case unordered: return unordered;
			case unknown: return unknown;
			default: assert false; return null;
			}
		}
	}
	
	private final String methodName;
	//an ordering of all nodes; a "task variable" is the integer index of this array. the first n entries are all parameters coming into this task, the others
	//are local schedule sites
	private final ArrayList<TV> nodes;
	//the last parameter task variable is numTaskParameters - 1
	private final int numFormalTaskParameters;
	private final Relation[][] relations;
	
	//the 'user data' that can be piggy backed to store additional information from a TV object to other data structures, such as the SSA instructions of where a task comes from etc
	//it also has some callbacks for the task schedule to get some information about parameters and such 
	private final SM scheduleManager;
	
	public TaskSchedule(String methodName, SM scheduleManager) {
		this.methodName = methodName;
		this.scheduleManager = scheduleManager;
		
		nodes = new ArrayList<TV>(scheduleManager.formalTaskParameterNodes());
		numFormalTaskParameters = nodes.size();
		nodes.addAll(scheduleManager.scheduleSiteNodes());			
		relations = new Relation[nodes.size()][nodes.size()];
		
		scheduleManager.initializeFullSchedule(this);
		assert matrixIsFull();
	}
	
	private boolean matrixIsFull() {
		for(int i = 0; i < relations.length; i++) {
			for(int j = 0; j < relations.length; j++) {
				if(relations[i][j] == null)
					return false;
			}
		}
		return true;
	}
	
	public int numberOfAllTaskVariables() {
		return nodes.size();	
	}
	
	public int numberOfFormalParameterTaskVariables() {
		return numFormalTaskParameters;
	}
	
	public int numberOfNonParameterTaskVariables() {
		return numberOfAllTaskVariables() - numberOfFormalParameterTaskVariables();
	}
	
	public boolean isFormalParameterTaskVariable(int var) {
		return var < numFormalTaskParameters;
	}
	
	//important: this method may return -1 if the node is a phi task node.
	//deal with it
	public int taskVariableForNode(TV node) {
		//assert(nodes.contains(node)) : "cannot map node " + node + " to task variable in " + this;
		return nodes.indexOf(node);	
	}
	
	public TV nodeForTaskVariable(int taskVariable) {
		return nodes.get(taskVariable);	
	}
	
	public Iterator<Integer> iterateNonParameterTaskVariables() {
		final int len = nodes.size();
		return new Iterator<Integer>() {
			private int nextIndex = numFormalTaskParameters;
			@Override
			public boolean hasNext() {
				return nextIndex < len;
			}

			@Override
			public Integer next() {
				//the task variable is equal to its index in the nodes array; so we just return the index
				int res = nextIndex++;
				return res;
			}

			@Override
			public void remove() {
				throw new ConcurrentModificationException();
			}
			
		};
	}
	
	public Iterator<Integer> iterateFormalParameterTaskVariables() {
		
		return new Iterator<Integer>() {
			private int nextIndex = 0;
			@Override
			public boolean hasNext() {
				return nextIndex < numFormalTaskParameters;
			}

			@Override
			public Integer next() {
				int res = nextIndex++;
				return res;
			}

			@Override
			public void remove() {
				throw new ConcurrentModificationException();
			}
			
		};
	}
	
	public Iterator<Integer> iterateAllTaskVariables() {
		final int len = nodes.size();
		return new Iterator<Integer>() {
			private int nextIndex = 0;
			@Override
			public boolean hasNext() {
				return nextIndex < len;
			}

			@Override
			public Integer next() {
				int res = nextIndex++;
				return res;
			}

			@Override
			public void remove() {
				throw new ConcurrentModificationException();
			}
			
		};
	}

	//given a task variable, find the actuals that flow into the schedule site
	//note that the array may contain -1 if the original ssa variable is a phi task node
	//deal with it
	public int[] actualsForTaskVariable(int taskVariable) {
		assert ! isFormalParameterTaskVariable(taskVariable);
		List<TV> params = scheduleManager.actualParametersForNode(nodeForTaskVariable(taskVariable));		
		int[] result = new int[params.size()];
		for(int i = 0; i < result.length; i++) {
			result[i] = taskVariableForNode(params.get(i));
		}
		return result;
	}
		
	//only use this in the task schedule manager initializeTaskSchedule() method!
	public void addRelationForTaskVariables(int lhsIndex, Relation rel, int rhsIndex) {
		assert relations[lhsIndex][rhsIndex] == null;
		relations[lhsIndex][rhsIndex] = rel;
		
		if(lhsIndex == rhsIndex)
			return;
		
		assert relations[rhsIndex][lhsIndex] == null;
		relations[rhsIndex][lhsIndex] = rel.inverse();	
	}
	
	public SM taskScheduleManager() {
		return scheduleManager;
	}
	
	//returns Relation.unknown if lhs or rhs is a phi node
	public Relation relationForNodes(TV lhs, TV rhs) {
		int lhsIndex = taskVariableForNode(lhs);
		int rhsIndex = taskVariableForNode(rhs);
		if(lhsIndex < 0 || rhsIndex < 0) {
			return Relation.unknown;
		} else {
			return relations[lhsIndex][rhsIndex];
		}
	}
	
	//retrns Relation.unknown if lhs or rhs is a phi node
	public Relation relationForTaskVariables(int lhs, int rhs) {
		if(lhs < 0 || rhs < 0)
			return Relation.unknown;
		
		return relations[lhs][rhs];		
	}
	
	public void print(PrintStream out) {
		for(int i = 0; i < relations.length; i++) {
			for(int j = 0; j < relations.length; j++) {
				out.println(i + "(" + this.nodeForTaskVariable(i) + ") " + relations[i][j] + " " + j + "(" + this.nodeForTaskVariable(j) + ")");			
			}
		}
		
	}
	
	@Override
	public String toString() {
		return "TaskSchedule for " + methodName;
	}
	
}
