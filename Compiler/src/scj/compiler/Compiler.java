package scj.compiler;

import java.io.IOException;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;

public class Compiler {

	public final CompilerOptions options;
	
	public Compiler(CompilerOptions opts) {
		this.options = opts;
	}
	
	public void compile() throws ClassHierarchyException, IOException, IllegalArgumentException, CallGraphBuilderCancelException {
		CompilationDriver driver = options.getCompilationDriver();
		driver.compile();
		
	}
	
	/*
	 * Flags:
	 * -opt=sc : generate sequentially consistent code
	 * -opt=x : optimization level x
	 * -opt=orig : no optimizations, only rewrites schedule sites
	 * -output=path : folder where the generated files will be placed
	 * -exclusions=file : file with exclusions (wala)
	 * -standardScope=file : wala standard scope file
	 * 
	 * usage:
	 * scj.Compiler [options]* applicationFile1 applicationFile2 ...
	 */
	public static void main(String[] args) throws ClassHierarchyException, IOException, IllegalArgumentException, CallGraphBuilderCancelException {		
		Compiler c = new Compiler(new CompilerOptions(args));
		c.compile();
	}
}
