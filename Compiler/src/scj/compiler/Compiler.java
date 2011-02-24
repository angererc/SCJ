package scj.compiler;

public class Compiler {

	public final CompilerOptions options;
	
	public Compiler(CompilerOptions opts) {
		this.options = opts;
	}
	
	public CompilationDriver compilationDriver() {
		return options.compilationDriver();
	}
	
	public void compile() throws Exception {
		CompilationDriver driver = compilationDriver();
		driver.compile();
	}
	
	/*
	 * Flags:
	 * -opt=sc : generate sequentially consistent code

	 * -opt=orig : no optimizations, only rewrites schedule sites
	 * -output=path : folder where the generated files will be placed
	 * -exclusions=file : file with exclusions (wala)
	 * -standardScope=file : wala standard scope file
	 * -prefix=prefix : add prefix to the output folder. Default is ""
	 * -driverPrefix=YES|NO : do (not) add a prefix depending on the chosen compilation driver. Default is YES
	 * -zeroXCFAPolicy=ALLOCATIONS|SMUSH_STRINGS|SMUSH_THROWABLES|SMUSH_PRIMITIVE_HOLDERS|SMUSH_MANY|CONSTANT_SPECIFIC flags for the policy used for the ZeroX or ZeroXContainer CFA builders
	 * 
	 * -opt=ContextSensitivity:CFABuilderType:Analyses  a tuple separated by : that specifies all the optimization details
	 * ContextSensitivity = default
	 * CFABuilderType = one of RTA, ZeroXCFA, ZeroXContainerCFA or nCFA where n is an integer.
	 * Analyses = one of ESC, ESC_SA, SA
	 * usage:
	 * scj.Compiler [options]* applicationFile1 applicationFile2 ...
	 */
	public static void main(String[] args) throws Exception {		
		Compiler c = new Compiler(new CompilerOptions(args));
		c.compile();
	}
}
