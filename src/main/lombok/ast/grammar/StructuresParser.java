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

import lombok.ast.AnnotationDeclaration;
import lombok.ast.AnnotationMethodDeclaration;
import lombok.ast.ClassDeclaration;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.EmptyDeclaration;
import lombok.ast.EnumConstant;
import lombok.ast.EnumDeclaration;
import lombok.ast.EnumTypeBody;
import lombok.ast.ExecutableDeclaration;
import lombok.ast.Identifier;
import lombok.ast.InterfaceDeclaration;
import lombok.ast.KeywordModifier;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.NormalTypeBody;
import lombok.ast.TypeBody;
import lombok.ast.TypeDeclaration;

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
				modifiers(),
				group.types.type(),
				Optional(Sequence(String("..."), group.basics.optWS())).label("varargs"),
				group.basics.identifier().label("name"),
				ZeroOrMore(Sequence(Ch('[').label("open"), group.basics.optWS(), Ch(']').label("closed"), group.basics.optWS()).label("dim")).label("dims"),
				set(actions.createMethodParameter(value("modifiers"), value("type"), text("varargs"), value("name"), nodes("dims/dim/open"), nodes("dims/dim/closed"))));
	}
	
	public Rule instanceInitializer() {
		return Sequence(
				group.statements.blockStatement().label("initializer"),
				set(actions.createInstanceInitializer(value("initializer"))));
	}
	
	public Rule staticInitializer() {
		return Sequence(
				String("static"), group.basics.testLexBreak(), group.basics.optWS(),
				group.statements.blockStatement().label("initializer"),
				set(actions.createStaticInitializer(value("initializer"))));
	}
	
	public Rule fieldDeclaration() {
		return Sequence(
				fieldDeclarationModifiers().label("modifiers"),
				variableDefinition(), set(), set(actions.posify(value())),
				Ch(';'), group.basics.optWS(),
				set(actions.createFieldDeclaration(value(), value("modifiers"))));
	}
	
	/**
	 * Add your own modifiers!
	 */
	Rule variableDefinition() {
		return Sequence(
				group.types.type().label("type"),
				variableDefinitionPart().label("head"),
				ZeroOrMore(Sequence(
						Ch(','), group.basics.optWS(),
						variableDefinitionPart()).label("tail")),
				set(actions.createVariableDefinition(value("type"), value("head"), values("ZeroOrMore/tail"))));
	}
	
	Rule variableDefinitionPartNoAssign() {
		return Sequence(
				group.basics.identifier().label("varName"),
				ZeroOrMore(Sequence(Ch('['), group.basics.optWS(), Ch(']'), group.basics.optWS()).label("dim")).label("dims"),
				set(actions.createVariableDefinitionPart(value("varName"), texts("dims/dim"), null)));
	}
	
	Rule variableDefinitionPart() {
		return Sequence(
				group.basics.identifier().label("varName"),
				ZeroOrMore(Sequence(Ch('['), group.basics.optWS(), Ch(']'), group.basics.optWS()).label("dim")).label("dims"),
				Optional(Sequence(
						Ch('='), group.basics.optWS(),
						FirstOf(
								group.expressions.arrayInitializer(),
								group.expressions.anyExpression()))).label("initializer"),
				set(actions.createVariableDefinitionPart(value("varName"), texts("dims/dim"), value("initializer"))));
	}
	
	public Rule annotation() {
		return Sequence(
				Ch('@'), group.basics.optWS(),
				group.types.plainReferenceType().label("annotationType"),
				Optional(Sequence(
						Ch('('), group.basics.optWS(),
						Optional(FirstOf(
								annotationElements(),
								Sequence(annotationElementValue(),
										set(actions.createAnnotationFromElement(lastValue()))))),
						Ch(')'), group.basics.optWS())).label("content"),
				set(actions.createAnnotation(value("annotationType"), value("content"))));
	}
	
	Rule annotationElements() {
		return Sequence(
				annotationElement().label("head"),
				ZeroOrMore(Sequence(
						Ch(','), group.basics.optWS(),
						annotationElement()).label("tail")),
				set(actions.createAnnotationFromElements(value("head"), values("ZeroOrMore/tail"))));
	}
	
	Rule annotationElement() {
		return Sequence(
				group.basics.identifier().label("name"),
				Ch('='), group.basics.optWS(),
				annotationElementValue().label("value"),
				set(actions.createAnnotationElement(value("name"), value("value"))));
	}
	
	Rule annotationElementValue() {
		return FirstOf(
				annotation(),
				Sequence(
						Ch('{'), group.basics.optWS(),
						Optional(Sequence(
								annotationElementValue().label("head"),
								ZeroOrMore(Sequence(
										Ch(','), group.basics.optWS(),
										annotationElementValue()).label("tail")),
								Optional(Sequence(Ch(','), group.basics.optWS())))),
						Ch('}'), group.basics.optWS(),
						set(actions.createAnnotationElementValueArrayInitializer(value("Optional/Sequence/head"), values("Optional/Sequence/ZeroOrMore/tail")))),
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
				Sequence(
						ZeroOrMore(annotation().label("annotation")).label("annotations"),
						String("package"), group.basics.testLexBreak(), group.basics.optWS()),
				group.basics.identifier().label("head"),
				ZeroOrMore(group.basics.dotIdentifier().label("tail")),
				Ch(';'), group.basics.optWS(),
				set(actions.createPackageDeclaration(values("Sequence/annotations/annotation"), value("head"), values("ZeroOrMore/tail"))));
	}
	
	public Rule importDeclaration() {
		return Sequence(
				Sequence(String("import"), group.basics.testLexBreak(), group.basics.optWS()),
				Optional(Sequence(String("static"), group.basics.testLexBreak(), group.basics.optWS())).label("static"),
				group.basics.identifier().label("head"),
				ZeroOrMore(group.basics.dotIdentifier().label("tail")),
				Optional(Sequence(
						Ch('.'), group.basics.optWS(),
						Ch('*'), group.basics.optWS())).label("dotStar"),
				Ch(';'), group.basics.optWS(),
				set(actions.createImportDeclaration(text("static"), value("head"), values("ZeroOrMore/tail"), text("dotStar"))));
	}
	
	public Rule compilationUnitEoi() {
		return Sequence(compilationUnit(), EOI);
	}
	
	public Rule compilationUnit() {
		return Sequence(
				group.basics.optWS(),
				Optional(packageDeclaration()).label("package"),
				ZeroOrMore(importDeclaration().label("import")).label("imports"),
				ZeroOrMore(anyTypeDeclaration().label("type")).label("types"),
				set(actions.createCompilationUnit(value("package"), values("imports/import"), values("types/type"))));
	}
}
