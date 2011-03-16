package scj.compiler.optimizing;

import java.util.ArrayList;

import scj.compiler.CompilationStats;

import javassist.CodeConverter;
import javassist.CtClass;
import javassist.NotFoundException;

public class ConflictingVolatileArraysCodeConverter extends CodeConverter {
	
	public void replaceVolatileArrayAccess(CompilationStats stats, ArrayList<Boolean> volatileArrayAccesses, CtClass calledClass, ArrayAccessReplacementMethodNames names) throws NotFoundException
	{
		transformers = new TransformConflictingVolatileArrayAccess(stats, volatileArrayAccesses, transformers, calledClass.getName(), names);
	}
}
