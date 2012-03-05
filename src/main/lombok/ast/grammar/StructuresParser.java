/*
 * Copyright (C) 2010-2012 The Project Lombok Authors.
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
package lombok.ast.grammar;

import lombok.ast.Annotation;
import lombok.ast.AnnotationDeclaration;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationMethodDeclaration;
import lombok.ast.ClassDeclaration;
import lombok.ast.CompilationUnit;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.EmptyDeclaration;
import lombok.ast.EnumConstant;
import lombok.ast.EnumDeclaration;
import lombok.ast.EnumTypeBody;
import lombok.ast.ExecutableDeclaration;
import lombok.ast.Identifier;
import lombok.ast.ImportDeclaration;
import lombok.ast.InstanceInitializer;
import lombok.ast.InterfaceDeclaration;
import lombok.ast.KeywordModifier;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.NormalTypeBody;
import lombok.ast.PackageDeclaration;
import lombok.ast.StaticInitializer;
import lombok.ast.TypeBody;
import lombok.ast.TypeDeclaration;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.SuppressSubnodes;

public class StructuresParser extends BaseParser<Node> {
	final ParserGroup group;
	final StructuresActions actions;
	
	public StructuresParser(ParserGroup group) {
		this.actions = new StructuresActions(group.getSource());
		this.group = group;
	}
	
	public Rule typeBody() {
		return typeBody_(typeBodyMember());
	}
	
	public Rule annotationTypeBody() {
		return typeBody_(annotationBodyMember());
	}
	
	Rule typeBody_(Rule member) {
		return Sequence(
				Test(Ch('{')),
				actions.p(new NormalTypeBody()),
				Ch('{'), actions.structure(), group.basics.optWS(),
				typeBodyDeclarations(member),
				Ch('}'), actions.structure(), actions.endPosByPos(),
				group.basics.optWS());
	}
	
	Rule typeBodyDeclarations(Rule member) {
		return ZeroOrMore(
				member,
				actions.endPosByNode(),
				swap(),
				push(((TypeBody) pop()).rawMembers().addToEnd(pop())));
	}
	
	public Rule typeBodyMember() {
		return FirstOf(
				fieldDeclaration(),
				constructorDeclaration(),
				methodDeclaration(),
				anyTypeDeclaration(),
				staticInitializer(),
				instanceInitializer());
	}
	
	Rule annotationBodyMember() {
		return FirstOf(
				annotationMethodDeclaration(),
				anyTypeDeclaration(),
				fieldDeclaration());
	}
	
	Rule emptyDeclaration() {
		return Sequence(
				Ch(';'),
				actions.p(new EmptyDeclaration()),
				group.basics.optWS());
	}
	
	public Rule anyTypeDeclaration() {
		return Sequence(
				TestNot(FirstOf(EOI, Ch('}'))),
				FirstOf(
						classOrInterfaceDeclaration(),
						enumDeclaration(),
						annotationDeclaration(),
						emptyDeclaration()));
	}
	
	public Rule classOrInterfaceDeclaration() {
		return Sequence(
				modifiers(),
				FirstOf(classDeclaration(), interfaceDeclaration()));
	}
	
	Rule classOrInterfaceSig(String keyword, Rule addonsRule) {
		return Sequence(
				actions.startPosByNode(),
				push(((TypeDeclaration) pop()).astModifiers((Modifiers) pop())),
				String(keyword), actions.structure(),
				group.basics.optWS(),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				swap(),
				push(((TypeDeclaration) pop()).astName((Identifier) pop())),
				group.types.typeVariables(),
				actions.addTypeVarsToTypeDeclaration(peek(1), pop()),
				addonsRule,
				typeBody(),
				actions.endPosByNode(),
				swap(),
				push(((TypeDeclaration) pop()).rawBody(pop())));
	}
	
	Rule classDeclaration() {
		return Sequence(
				Test(String("class"), group.basics.testLexBreak()),
				push(new ClassDeclaration()),
				classOrInterfaceSig("class",
						Optional(
								FirstOf(
										Sequence(
												extendsClause(),
												actions.addExtendsToTypeDeclaration(peek(1), pop()),
												Optional(
														implementsClause(),
														actions.addImplementsToTypeDeclaration(peek(1), pop()))),
										Sequence(
												implementsClause(),
												actions.addImplementsToTypeDeclaration(peek(1), pop()),
												Optional(
														extendsClause(),
														actions.addExtendsToTypeDeclaration(peek(1), pop())))))));
	}
	
	Rule interfaceDeclaration() {
		return Sequence(
				Test(String("interface"), group.basics.testLexBreak()),
				push(new InterfaceDeclaration()),
				classOrInterfaceSig("interface",
						Optional(
								extendsClause(),
								actions.addExtendsToTypeDeclaration(peek(1), pop()))));
	}
	
	Rule extendsClause() {
		return Sequence(
				Test(String("extends"), group.basics.testLexBreak()),
				actions.p(new TemporaryNode.ExtendsClause()),
				String("extends"), actions.structure(),
				group.basics.optWS(),
				group.types.type(),
				actions.endPosByNode(),
				((TemporaryNode.ExtendsClause) peek(1)).superTypes.add(pop()),
				ZeroOrMore(
						Ch(','), actions.structure(),
						group.basics.optWS(),
						group.types.type(),
						actions.endPosByNode(),
						((TemporaryNode.ExtendsClause) peek(1)).superTypes.add(pop())));
	}
	
	Rule implementsClause() {
		return Sequence(
				Test(String("implements"), group.basics.testLexBreak()),
				actions.p(new TemporaryNode.ImplementsClause()),
				String("implements"), actions.structure(),
				group.basics.optWS(),
				group.types.type(),
				actions.endPosByNode(),
				((TemporaryNode.ImplementsClause) peek(1)).superInterfaces.add(pop()),
				ZeroOrMore(
						Ch(','), actions.structure(),
						group.basics.optWS(),
						group.types.type(),
						actions.endPosByNode(),
						((TemporaryNode.ImplementsClause) peek(1)).superInterfaces.add(pop())));
	}
	
	Rule enumBody() {
		return Sequence(
				Test(Ch('{')),
				actions.p(new EnumTypeBody()),
				Ch('{'), actions.structure(),
				group.basics.optWS(),
				Optional(
						enumConstant(),
						swap(),
						push(((EnumTypeBody) pop()).rawConstants().addToEnd(pop())),
						ZeroOrMore(
								Ch(','), actions.structure(), group.basics.optWS(),
								enumConstant(),
								swap(),
								push(((EnumTypeBody) pop()).rawConstants().addToEnd(pop()))),
						Optional(Sequence(Ch(','), actions.structure(), group.basics.optWS()))),
				Optional(
						Ch(';'), actions.structure(), group.basics.optWS(),
						typeBodyDeclarations(typeBodyMember())),
				Ch('}'), actions.structure(),
				actions.endPosByPos(),
				group.basics.optWS());
	}
	
	Rule enumConstant() {
		return Sequence(
				actions.p(new EnumConstant()),
				ZeroOrMore(
						annotation(),
						swap(),
						push(((EnumConstant) pop()).rawAnnotations().addToEnd(pop()))),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				swap(),
				push(((EnumConstant) pop()).astName((Identifier) pop())),
				Optional(
						methodArguments(),
						actions.addMethodArgumentsToEnumConstant(peek(1), pop())),
				Optional(
						typeBody(),
						swap(),
						push(((EnumConstant) pop()).rawBody(pop()))));
	}
	
	public Rule enumDeclaration() {
		return Sequence(
				modifiers(),
				Test(String("enum"), group.basics.testLexBreak()),
				push(new EnumDeclaration()),
				actions.startPosByNode(),
				push(((TypeDeclaration) pop()).astModifiers((Modifiers) pop())),
				String("enum"), actions.structure(),
				group.basics.optWS(),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				swap(),
				push(((TypeDeclaration) pop()).astName((Identifier) pop())),
				Optional(
						implementsClause(),
						actions.addImplementsToTypeDeclaration(peek(1), pop())),
				enumBody(),
				actions.endPosByNode(),
				swap(),
				push(((TypeDeclaration) pop()).rawBody(pop())));
	}
	
	public Rule annotationDeclaration() {
		return Sequence(
				modifiers(),
				Test(Ch('@'), group.basics.optWS(), String("interface"), group.basics.testLexBreak()),
				push(new AnnotationDeclaration()),
				actions.startPosByNode(),
				push(((TypeDeclaration) pop()).astModifiers((Modifiers) pop())),
				Ch('@'), actions.structure(),
				group.basics.optWS(),
				String("interface"), actions.structure(),
				group.basics.optWS(),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				swap(),
				push(((TypeDeclaration) pop()).astName((Identifier) pop())),
				annotationTypeBody(),
				actions.endPosByNode(),
				swap(),
				push(((TypeDeclaration) pop()).rawBody(pop())));
	}
	
	public Rule methodArguments() {
		return Sequence(
				Test(Ch('(')),
				actions.p(new TemporaryNode.MethodArguments()),
				Ch('('), actions.structure(),
				group.basics.optWS(),
				Optional(
						group.expressions.anyExpression(),
						((TemporaryNode.MethodArguments) peek(1)).arguments.add(pop()),
						ZeroOrMore(
								Ch(','), actions.structure(),
								group.basics.optWS(),
								group.expressions.anyExpression(),
								((TemporaryNode.MethodArguments) peek(1)).arguments.add(pop()))),
				Ch(')'), actions.structure(), actions.endPosByPos(),
				group.basics.optWS());
	}
	
	Rule executableDeclaration(Rule coreSigRule, Rule extendedDimsRule) {
		return Sequence(
				modifiers(),
				swap(),
				push(((ExecutableDeclaration) pop()).astModifiers((Modifiers) pop())),
				group.types.typeVariables(),
				actions.addTypeVarsToExecutableDeclaration(peek(1), pop()),
				coreSigRule,
				methodParameters(),
				actions.addParamsToExecutableDeclaration(peek(1), pop()),
				extendedDimsRule,
				Optional(
						Sequence(String("throws"), group.basics.testLexBreak()), actions.structure(),
						group.basics.optWS(),
						group.types.type(),
						swap(),
						push(((ExecutableDeclaration) pop()).rawThrownTypeReferences().addToEnd(pop())),
						ZeroOrMore(
								Ch(','), actions.structure(), group.basics.optWS(),
								group.types.type(),
								swap(),
								push(((ExecutableDeclaration) pop()).rawThrownTypeReferences().addToEnd(pop())))),
				FirstOf(
						Sequence(
								Ch(';'), actions.structure(), group.basics.optWS()),
						Sequence(
								group.statements.blockStatement(),
								swap(),
								push(((ExecutableDeclaration) pop()).rawBody(pop())))));
	}
	
	public Rule constructorDeclaration() {
		return Sequence(
				actions.p(new ConstructorDeclaration()),
				executableDeclaration(
						Sequence(
								group.basics.identifier().label("typeName"),
								actions.turnToIdentifier(),
								swap(),
								push(((ConstructorDeclaration) pop()).astTypeName((Identifier) pop()))),
						EMPTY));
	}
	
	public Rule methodDeclaration() {
		return Sequence(
				actions.p(new MethodDeclaration()),
				executableDeclaration(
						Sequence(
								group.types.type(),
								swap(),
								push(((MethodDeclaration) pop()).rawReturnTypeReference(pop())),
								group.basics.identifier().label("methodName"),
								actions.turnToIdentifier(),
								swap(),
								push(((MethodDeclaration) pop()).astMethodName((Identifier) pop()))),
						ZeroOrMore(
								Ch('['),
								group.basics.optWS(),
								Ch(']'),
								group.basics.optWS(),
								actions.incrementMethodReturnTypeDimensions())));
	}
	
	public Rule annotationMethodDeclaration() {
		return Sequence(
				actions.p(new AnnotationMethodDeclaration()),
				modifiers(),
				swap(),
				push(((AnnotationMethodDeclaration) pop()).astModifiers((Modifiers) pop())),
				group.types.type(),
				swap(),
				push(((AnnotationMethodDeclaration) pop()).rawReturnTypeReference(pop())),
				group.basics.identifier().label("methodName"),
				actions.turnToIdentifier(),
				swap(),
				push(((AnnotationMethodDeclaration) pop()).astMethodName((Identifier) pop())),
				Ch('('), actions.structure(), group.basics.optWS(),
				Ch(')'), actions.structure(), group.basics.optWS(),
				Optional(
						Sequence(String("default"), group.basics.testLexBreak()), actions.structure(),
						group.basics.optWS(),
						annotationElementValue(),
						swap(),
						push(((AnnotationMethodDeclaration) pop()).rawDefaultValue(pop()))),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	Rule methodParameters() {
		return Sequence(
				Test(Ch('(')),
				actions.p(new TemporaryNode.MethodParameters()),
				Ch('('), actions.structure(),
				group.basics.optWS(),
				Optional(
						methodParameter(),
						((TemporaryNode.MethodParameters) peek(1)).parameters.add(pop()),
						swap(),
						ZeroOrMore(
								Ch(','), actions.structure(), group.basics.optWS(),
								methodParameter(),
								((TemporaryNode.MethodParameters) peek(1)).parameters.add(pop()))),
				Ch(')'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	Rule methodParameter() {
		return Sequence(
				actions.p(new VariableDefinition()),
				swap(),
				push(((VariableDefinition) pop()).astModifiers((Modifiers) pop())),
				group.types.type(),
				swap(),
				push(((VariableDefinition) pop()).rawTypeReference(pop())),
				Optional(
						String("..."), actions.structure(),
						group.basics.optWS(),
						push(((VariableDefinition) pop()).astVarargs(true))),
				group.basics.identifier().label("name"),
				actions.turnToIdentifier(),
				push(new VariableDefinitionEntry()),
				actions.startPosByNode(),
				swap(),
				actions.endPosByNode(),
				push(((VariableDefinitionEntry) pop()).astName((Identifier) pop())),
				ZeroOrMore(
						Ch('['), actions.structure(),
						group.basics.optWS(),
						Ch(']'), actions.structure(),
						actions.endPosByPos(),
						actions.incrementVarDefEntryDimensions()),
				actions.endPosByNode(),
				swap(),
				push(((VariableDefinition) pop()).rawVariables().addToEnd(pop())));
	}
	
	public Rule instanceInitializer() {
		return Sequence(
				group.statements.blockStatement().label("initializer"),
				actions.p(new InstanceInitializer()),
				swap(), actions.endPosByNode(), swap(),
				push(((InstanceInitializer) pop()).rawBody(pop())));
	}
	
	public Rule staticInitializer() {
		return Sequence(
				Test(String("static"), group.basics.testLexBreak()),
				actions.p(new StaticInitializer()),
				String("static"), actions.structure(),
				group.basics.optWS(),
				group.statements.blockStatement().label("initializer"),
				actions.endPosByNode(), swap(),
				push(((StaticInitializer) pop()).rawBody(pop())));
	}
	
	public Rule fieldDeclaration() {
		return variableDeclaration();
	}
	
	Rule variableDeclaration() {
		return Sequence(
				modifiers(),
				variableDefinition(),
				swap(),
				actions.setModifiersOnVarDef((Modifiers) pop(), peek()),
				actions.p(new VariableDeclaration()),
				swap(), actions.endPosByPos(), swap(),
				push(((VariableDeclaration) pop()).rawDefinition(pop())),
				Ch(';'), actions.structure(),
				actions.endPosByPos(),
				group.basics.optWS());
	}
	
	/**
	 * Add your own modifiers!
	 */
	Rule variableDefinition() {
		return Sequence(
				actions.p(new VariableDefinition()),
				group.types.type(),
				swap(),
				push(((VariableDefinition) pop()).rawTypeReference(pop())),
				variableDefinitionPart(),
				actions.endPosByNode(),
				swap(),
				push(((VariableDefinition) pop()).rawVariables().addToEnd(pop())),
				ZeroOrMore(
						Ch(','), actions.structure(), group.basics.optWS(),
						variableDefinitionPart(),
						actions.endPosByNode(),
						swap(),
						push(((VariableDefinition) pop()).rawVariables().addToEnd(pop()))));
	}
	
	Rule variableDefinitionPartNoAssign() {
		return Sequence(
				actions.p(new VariableDefinitionEntry()),
				group.basics.identifier().label("varName"),
				actions.turnToIdentifier(),
				actions.endPosByNode(),
				swap(),
				push(((VariableDefinitionEntry) pop()).astName((Identifier) pop())),
				ZeroOrMore(
						Ch('['), actions.structure(), group.basics.optWS(),
						Ch(']'), actions.structure(), actions.endPosByPos(), group.basics.optWS(),
						actions.incrementVarDefEntryDimensions()));
	}
	
	Rule variableDefinitionPart() {
		return Sequence(
				variableDefinitionPartNoAssign(),
				Optional(
						Ch('='), actions.structure(), group.basics.optWS(),
						FirstOf(
								group.expressions.arrayInitializer(),
								group.expressions.anyExpression()).label("initializer"),
						actions.endPosByNode(),
						swap(),
						push(((VariableDefinitionEntry) pop()).rawInitializer(pop()))));
	}
	
	public Rule annotation() {
		return Sequence(
				Test('@'),
				actions.p(new Annotation()),
				Ch('@'), actions.structure(), group.basics.optWS(),
				group.types.plainReferenceType().label("annotationType"),
				actions.endPosByNode(), swap(),
				push(((Annotation) pop()).rawAnnotationTypeReference(pop())),
				Optional(
						Ch('('), group.basics.optWS(),
						Optional(
								FirstOf(
										annotationElements(),
										Sequence(
												annotationElementValue(),
												push(new AnnotationElement()),
												actions.startPosByNode(),
												swap(), actions.endPosByNode(),
												swap(), push(((AnnotationElement) pop()).rawValue(pop())),
												swap(), push(((Annotation) pop()).rawElements().addToEnd(pop()))))),
						Ch(')'), actions.endPosByPos(),
						group.basics.optWS()));
	}
	
	Rule annotationElements() {
		return Sequence(
				annotationElement(),
				swap(),
				push(((Annotation) pop()).rawElements().addToEnd(pop())),
				ZeroOrMore(
						Ch(','), actions.structure(), group.basics.optWS(),
						annotationElement(),
						swap(),
						push(((Annotation) pop()).rawElements().addToEnd(pop()))));
	}
	
	Rule annotationElement() {
		return Sequence(
				actions.p(new AnnotationElement()),
				group.basics.identifier().label("name"),
				actions.turnToIdentifier(),
				swap(),
				push(((AnnotationElement) pop()).astName((Identifier) pop())),
				Ch('='), actions.structure(), group.basics.optWS(),
				annotationElementValue(),
				actions.endPosByNode(), swap(),
				push(((AnnotationElement) pop()).rawValue(pop())));
	}
	
	Rule annotationElementValue() {
		return FirstOf(
				annotation(),
				group.expressions.arrayInitializerInternal(FirstOf(annotation(), group.expressions.inlineIfExpressionChaining())),
				group.expressions.inlineIfExpressionChaining());
	}
	
	@SuppressSubnodes
	Rule anyKeyword() {
		return Sequence(
				FirstOf("final", "strictfp", "abstract", "transient", "volatile",
						"public", "protected", "private", "synchronized", "static", "native"),
				group.basics.testLexBreak());
	}
	
	public Rule keywordModifier() {
		return Sequence(
				anyKeyword(),
				actions.p(new KeywordModifier().astName(match())),
				group.basics.optWS());
	}
	
	public Rule modifier() {
		return FirstOf(annotation(), keywordModifier());
	}
	
	public Rule modifiers() {
		return Sequence(
				TestNot(Ch('}')),
				actions.p(new Modifiers()),
				ZeroOrMore(addModifierToModifiers()));
	}
	
	Rule addModifierToModifiers() {
		return FirstOf(addAnnotationToModifiers(), addKeywordModifierToModifiers());
	}
	
	Rule addKeywordModifierToModifiers() {
		return Sequence(
				keywordModifier(),
				swap(),
				push(((Modifiers) pop()).rawKeywords().addToEnd(pop())));
	}
	
	Rule addAnnotationToModifiers() {
		return Sequence(
				annotation(),
				swap(),
				push(((Modifiers) pop()).rawAnnotations().addToEnd(pop())));
	}
	
	public Rule packageDeclaration() {
		return Sequence(
				actions.p(new PackageDeclaration()),
				ZeroOrMore(
						annotation(),
						swap(),
						push(((PackageDeclaration) pop()).rawAnnotations().addToEnd(pop()))),
				String("package"), actions.structure(), group.basics.testLexBreak(), group.basics.optWS(),
				group.basics.identifier(),
				swap(),
				push(((PackageDeclaration) pop()).rawParts().addToEnd(pop())),
				ZeroOrMore(
						group.basics.dotIdentifier(),
						swap(),
						push(((PackageDeclaration) pop()).rawParts().addToEnd(pop()))),
				Ch(';'), actions.endPosByPos(), group.basics.optWS());
	}
	
	public Rule importDeclaration() {
		return Sequence(
				Test(String("import"), group.basics.testLexBreak()),
				actions.p(new ImportDeclaration()),
				String("import"), actions.structure(), group.basics.optWS(),
				Optional(
						String("static"), group.basics.testLexBreak(), group.basics.optWS(),
						push(((ImportDeclaration) pop()).astStaticImport(true))),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				swap(),
				push(((ImportDeclaration) pop()).rawParts().addToEnd(pop())),
				ZeroOrMore(
						group.basics.dotIdentifier(),
						swap(),
						push(((ImportDeclaration) pop()).rawParts().addToEnd(pop()))),
				Optional(
						Test(Ch('.'), group.basics.optWS(), Ch('*')),
						Ch('.'), actions.structure(),
						group.basics.optWS(),
						Ch('*'), actions.structure(),
						group.basics.optWS(),
						push(((ImportDeclaration) pop()).astStarImport(true))),
				Ch(';'),
				actions.endPosByPos(), group.basics.optWS());
	}
	
	public Rule compilationUnitEoi() {
		return Sequence(compilationUnit(), EOI);
	}
	
	public Rule compilationUnit() {
		return Sequence(
				actions.p(new CompilationUnit()),
				group.basics.optWS(),
				Optional(
						packageDeclaration(), actions.endPosByNode(), swap(),
						push(((CompilationUnit) pop()).rawPackageDeclaration(pop()))),
				ZeroOrMore(
						importDeclaration(), actions.endPosByNode(), swap(),
						push(((CompilationUnit) pop()).rawImportDeclarations().addToEnd(pop()))),
				ZeroOrMore(
						anyTypeDeclaration(), actions.endPosByNode(), swap(),
						push(((CompilationUnit) pop()).rawTypeDeclarations().addToEnd(pop()))));
	}
}
