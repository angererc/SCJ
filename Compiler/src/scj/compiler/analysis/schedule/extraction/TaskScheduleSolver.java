/**
 * 
 */
package scj.compiler.analysis.schedule.extraction;

import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;

import scj.compiler.wala.util.WalaConstants;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.dataflow.graph.BasicFramework;
import com.ibm.wala.dataflow.graph.DataflowSolver;
import com.ibm.wala.fixedpoint.impl.AbstractStatement;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.traverse.FloydWarshall;
import com.ibm.wala.util.intset.IBinaryNaturalRelation;

public class TaskScheduleSolver extends DataflowSolver<ISSABasicBlock, FlowData> {

	public static NormalNodeFlowData solve(IR ir) {
		try {	
			assert ir != null : "didn't have IR";
			PrunedCFG<SSAInstruction, ISSABasicBlock> prunedCFG = UnhandledExceptionsPrunedCFG.make(ir.getControlFlowGraph());
			TaskScheduleSolver solver = new TaskScheduleSolver(ir, prunedCFG);
			
			solver.solve((IProgressMonitor)null);
			BasicBlock exit = ir.getControlFlowGraph().exit();
			return (NormalNodeFlowData)solver.getIn(exit);			
		} catch (CancelException e) {			
			//
		}
		
		Assertions.UNREACHABLE();
		return null;
		
	};
	
	/**
	 * 
	 */
	final IR ir;
	final ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg;
	final IBinaryNaturalRelation backEdges;
	final int[][] cfgShortestPaths;
	private NormalNodeFlowData entry;
	
	public TaskScheduleSolver(IR ir, ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg) {
		super(new BasicFramework<ISSABasicBlock, FlowData>(cfg, new TransferFunctionProvider()));
		((TransferFunctionProvider)this.getProblem().getTransferFunctionProvider()).setSolver(this);
		this.ir = ir;
		this.cfg = cfg;
		this.backEdges = Acyclic.computeBackEdges(cfg, cfg.entry());
		//do not use the CFG here because that one is pruned (no unhandled exception edges) and then the floyd warshall might crash. not sure why
		this.cfgShortestPaths = FloydWarshall.shortestPathLengths(ir.getControlFlowGraph());
	}

	@Override
	protected FlowData makeEdgeVariable(ISSABasicBlock src, ISSABasicBlock dst) {
		assert src != null;
		assert dst != null;
		
		return backEdges.contains(src.getGraphNodeId(), dst.getGraphNodeId()) ?
				new BackEdgeFlowData(src, dst) : new EdgeFlowData(src, dst);	
	}

	@Override
	protected FlowData makeNodeVariable(ISSABasicBlock n, boolean IN) {
		assert n != null;
		
		NormalNodeFlowData result;
		int predNodeCount = cfg.getPredNodeCount(n);
		if(IN &&  predNodeCount > 1) {
			result = new JoinNodeFlowData(n, predNodeCount);
		} else {
			result = new NormalNodeFlowData(n);
		}
		
//		boolean isLoopHead = false;
//		for(IntPair rel : backEdges) {
//			if(rel.getY() == n.getGraphNodeId()) {
//				isLoopHead = true;
//				break;
//			}
//		}
				
		if (IN && n.equals(cfg.entry())) {
			entry = result;
			result.initEmpty();
			result.addCurrentLoopContext(LoopContext.emptyLoopContext());
		}
		return result;
	}

	@Override
	protected void initializeWorkList() {
		super.buildEquations(false, false);
		/*
		 * Add only the entry variable to the work list.
		 */
		
		for (Iterator<?> it = getFixedPointSystem().getStatementsThatUse(entry); it.hasNext();) {
			AbstractStatement<?, ?> s = (AbstractStatement<?, ?>) it.next();
			addToWorkList(s);
		}
	}

	@Override
	protected void initializeVariables() {
		super.initializeVariables();
		
		//make sure we know the parameters
		for(int i = 0; i < ir.getNumberOfParameters(); i++) {
			TypeReference paramType = ir.getParameterType(i);
			if(WalaConstants.isTaskType(paramType)) {
				int ssaVariable = ir.getParameter(i);
				entry.addFormalTaskParameter(ssaVariable);
			}
		}
		
	}
}