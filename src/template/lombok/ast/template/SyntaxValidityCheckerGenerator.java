/*
 * Copyright © 2010 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.ast.template;

import static java.util.Collections.emptyList;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic.Kind;

import lombok.Data;
import lombok.Getter;
import lombok.ast.template.TemplateProcessor.FieldData;

public class SyntaxValidityCheckerGenerator {
	@Data
	private static class MethodData {
		private final boolean isStatic;
		private final String typeName;
		private final String methodName;
	}
	
	private Map<String, List<MethodData>> checkMethods = new HashMap<String, List<MethodData>>();
	private Map<String, List<FieldData>> types = new HashMap<String, List<FieldData>>();
	private final ProcessingEnvironment env;
	@Getter private boolean finished;
	
	public SyntaxValidityCheckerGenerator(ProcessingEnvironment env) {
		this.env = env;
		
	}
	
	private static <K, V> List<V> getForMultiMap(Map<K,List<V>> map, K key) {
		List<V> list = map.get(key);
		if (list == null) map.put(key, list = new ArrayList<V>());
		return list;
	}
	
	public void recordCheckMethod(ExecutableElement method) {
		if (method.getKind() != ElementKind.METHOD) throw new IllegalArgumentException("not a method");
		if (method.getModifiers().contains(Modifier.STATIC)) {
			if (method.getParameters().size() == 2) {
				String targetType = toSimpleName(method.getParameters().get(0).asType().toString());
				getForMultiMap(checkMethods, targetType).add(new MethodData(
						true,
						method.getEnclosingElement().asType().toString(),
						method.getSimpleName().toString()));
				return;
			}
			env.getMessager().printMessage(Kind.ERROR, "Method does not conform to requirements: If static, it must have 2 parameters, the second a List<SyntaxProblem>");
			return;
		}
		
		if (method.getParameters().size() == 1) {
			TypeMirror container = method.getEnclosingElement().asType();
			String targetType = toSimpleName(method.getParameters().get(0).asType().toString());
			
			getForMultiMap(checkMethods, targetType).add(new MethodData(
					false,
					container.toString(),
					method.getSimpleName().toString()));
			return;
		}
		env.getMessager().printMessage(Kind.ERROR, "Method does not conform to requirements: If non-static, it must have 1 parameter");
		return;
	}
	
	public void recordFieldDataForCheck(String className, List<FieldData> fields) {
		types.put(toSimpleName(className), fields);
	}
	
	private static String toSimpleName(String className) {
		int idx = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
		return idx == -1 ? className : className.substring(idx + 1);
	}
	
	public void finish() {
		try {
			finish0();
			finished = true;
		} catch (IOException e) {
			env.getMessager().printMessage(Kind.ERROR, String.format(
					"Can't generate sourcefile lombok.ast.syntaxChecks.SyntacticValidityVisitor: %s",
					e));
		}
	}
	
	private void finish0() throws IOException {
		JavaFileObject file = env.getFiler().createSourceFile("lombok.ast.syntaxChecks.SyntacticValidityVisitor");
		Writer out = file.openWriter();
		out.write("//Generated by SyntaxValidityCheckerGenerator. DO NOT EDIT, DO NOT CHECK IN!\n\n");
		
		out.write("package lombok.ast.syntaxChecks;\n\n");
		out.write("import lombok.ast.*;\n\n");
		out.write("/**\n");
		out.write(" * Adds a {@link lombok.ast.SyntaxProblem} to a list for each syntactic problem with a node.\n");
		out.write(" * Something like {@code a +} is not syntactically valid (It's missing the second argument to binary operator),\n");
		out.write(" * but something like {@code a + b} would be valid, <i>even if</i> both {@code a} and {@code b} end up being objects,\n");
		out.write(" * which do not support the + operator.\n");
		out.write(" */\n");
		out.write("public class SyntacticValidityVisitor extends lombok.ast.syntaxChecks.SyntacticValidityVisitorBase {\n");
		out.write("\tpublic SyntacticValidityVisitor(java.util.List<SyntaxProblem> problems, boolean recursing) {\n");
		out.write("\t\tsuper(problems, recursing);\n");
		out.write("\t}\n");
		Set<String> typesToCheck = new TreeSet<String>();
		typesToCheck.addAll(checkMethods.keySet());
		typesToCheck.addAll(types.keySet());
		for (String typeToCheck : typesToCheck) {
			List<FieldData> fieldList = types.get(typeToCheck);
			List<MethodData> methodList = checkMethods.get(typeToCheck);
			if (fieldList == null) fieldList = emptyList();
			if (methodList == null) methodList = emptyList();
			if (fieldList.isEmpty() && methodList.isEmpty()) continue;
			out.write("\t\n");
			out.write("\t@java.lang.Override public boolean visit");
			out.write(typeToCheck);
			out.write("(");
			out.write(typeToCheck);
			out.write(" node) {\n");
			boolean counterGenerated = false;
			for (FieldData field : fieldList) {
				if (field.isList()) {
					generateCheckForList(out, field, counterGenerated);
					counterGenerated = true;
					continue;
				}
				
				if (!field.isAstNode()) {
					generateCheckForBasicField(out, field);
					continue;
				}
				
				generateCheckForNodeField(out, field);
			}
			
			if (!fieldList.isEmpty()) out.write("\t\t\n");
			
			for (MethodData method : methodList) {
				out.write("\t\t");
				if (method.isStatic()) {
					out.write(method.getTypeName());
					out.write(".");
					out.write(method.getMethodName());
					out.write("(node, problems);\n");
				} else {
					out.write("this.getCheckerObject(");
					out.write(method.getTypeName());
					out.write(".class).");
					out.write(method.getMethodName());
					out.write("(node);\n");
				}
			}
			
			if (!methodList.isEmpty()) out.write("\t\t\n");
			out.write("\t\treturn !this.recursing;\n");
			out.write("\t}\n");
		}
		out.write("}\n");
		out.close();
	}
	
	private void generateCheckForList(Writer out, FieldData field, boolean counterGenerated) throws IOException {
		out.write("\t\t");
		if (!counterGenerated) out.write("int ");
		out.write("counter = 0;\n");
		
		out.write("\t\tfor (Node child : node.raw");
		out.write(field.titleCasedName());
		out.write("()) {\n");
		out.write("\t\t\tthis.checkChildValidity(node, child, \"");
		out.write(field.getName());
		out.write("[\" + counter++ + \"]\", true, ");
		out.write(field.getType().toString());
		out.write(".class);\n");
		out.write("\t\t}\n");
	}
	
	private void generateCheckForBasicField(Writer out, FieldData field) throws IOException {
		if (!field.getRawFormParser().isEmpty()) {
			out.write("\t\tif (node.getErrorReasonFor");
			out.write(field.titleCasedName());
			out.write("() != null) problems.add(new SyntaxProblem(node, node.getErrorReasonFor");
			out.write(field.titleCasedName());
			out.write("()));\n");
		} else {
			if (field.isMandatory()) {
				out.write("\t\tif (node.get");
				if (!field.getRawFormGenerator().isEmpty()) out.write("Raw");
				out.write(field.titleCasedName());
				out.write("() == null) problems.add(new SyntaxProblem(node, \"");
				out.write(field.getName());
				out.write(" is mandatory\"));\n");
			}
		}
	}
	
	private void generateCheckForNodeField(Writer out, FieldData field) throws IOException {
		out.write("\t\tthis.checkChildValidity(node, node.getRaw");
		out.write(field.titleCasedName());
		out.write("(), \"");
		out.write(field.getName());
		out.write("\", ");
		out.write(String.valueOf(field.isMandatory()));
		out.write(", ");
		out.write(field.getType().toString());
		out.write(".class);\n");
	}
}
