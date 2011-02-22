package scj.compiler;

import org.junit.Test;

public class SimpleCompilerTests {

	@Test
	public void startupCompilerTest() throws Exception {
		String[] args = new String[] {
				"-opt=orig",
				"-prefix=barneshut",
				"bin/barneshut",
				"bin/galois",
				"bin/util",
				"bin/galois_scj"
		};
		CompilerOptions options = new CompilerOptions(args);
		Compiler compiler = new Compiler(options);
		compiler.compile();
	}
}
