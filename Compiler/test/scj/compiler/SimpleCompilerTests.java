package scj.compiler;

import org.junit.Test;

public class SimpleCompilerTests {

	@Test
	public void startupCompilerTest() throws Exception {
		String[] args = new String[] {
				"bin/testclasses"
		};
		CompilerOptions options = new CompilerOptions(args);
		Compiler compiler = new Compiler(options);
		compiler.compile();
	}
}
