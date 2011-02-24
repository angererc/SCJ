package scj.compiler.analysis.schedule;

//a schedule analysis that considers all tasks parallel
//used when the compiler is configured to not use a schedule analysis
public class DummyScheduleAnalysis implements ScheduleAnalysis {

	@Override
	public void analyze() {
		
	}
}
