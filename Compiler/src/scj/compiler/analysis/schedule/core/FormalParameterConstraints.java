package scj.compiler.analysis.schedule.core;

import java.util.Arrays;

import scj.compiler.analysis.schedule.extraction.TaskSchedule;

/**
 * constraints relating each pair of actual parameters.
 * those constraints are assembled by the parent task before recursing analysis into the child
 * @author angererc
 *
 */
public class FormalParameterConstraints {
	
	//TODO I probably don't need a full matrix here; the diagonal is always "singleton" and the opposite order can be retrieved by inverting the relation;
	//having a full matrix makes the equals checks etc slower; however I expect that 99% of all the task methods only have very few (1, 2, or 3 or so) Task parameters and therefore
	//I'm coding this more explicitly and less efficient.
	private final TaskSchedule.Relation[][] relations;
	
	public FormalParameterConstraints() {
		this.relations = new TaskSchedule.Relation[0][0];
	}
	
	public FormalParameterConstraints(TaskSchedule<?,?> taskSchedule, int scheduleSite) {
		
		int[] actuals = taskSchedule.actualsForTaskVariable(scheduleSite);
		System.out.println(taskSchedule + " actuals " + Arrays.toString(actuals));
		
		int size = actuals.length;
		this.relations = new TaskSchedule.Relation[size][size];
		//compare parameter 0 with 1, 2, 3... then 1 with 2, 3, ... etc
		for(int i = 0; i < size-1; i++) {
			relations[i][i] = TaskSchedule.Relation.singleton;
			for(int j = i+1; j < size; j++) {
				int lhs = actuals[i];
				int rhs = actuals[j];
				
				relations[i][j] = taskSchedule.relationForTaskVariables(lhs, rhs);
				assert(relations[i][j] != null);
				relations[j][i] = taskSchedule.relationForTaskVariables(rhs, lhs);
				assert(relations[j][i] != null);
			}
		}
		relations[size-1][size-1] = TaskSchedule.Relation.singleton;
	}
	
	public int numActualParameters() {
		return relations.length;
	}
	
	public TaskSchedule.Relation relation(int lhs, int rhs) {
		return relations[lhs][rhs];
	}
	
	@Override
	public boolean equals(Object otherObj) {
		if(otherObj instanceof FormalParameterConstraints) {
			FormalParameterConstraints other = (FormalParameterConstraints)otherObj;
			if(relations.length != other.relations.length)
				return false;
			
			for(int i = 0; i < relations.length; i++) {
				for(int j = 0; j < relations.length; j++) {
					if(relations[i][j] != other.relations[i][j]) {
						return false;
					}
				}
			}
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		int hash = relations.length + (relations.length == 0 ? 2011 : 2011 * relations[0][0].hashCode());
		return hash;
	}
	
	@Override
	public String toString() {
		return Arrays.toString(relations);
	}
}
