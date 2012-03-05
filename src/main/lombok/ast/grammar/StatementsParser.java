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

import lombok.ast.AlternateConstructorInvocation;
import lombok.ast.Assert;
import lombok.ast.Block;
import lombok.ast.Break;
import lombok.ast.Case;
import lombok.ast.Catch;
import lombok.ast.Continue;
import lombok.ast.Default;
import lombok.ast.DoWhile;
import lombok.ast.EmptyStatement;
import lombok.ast.ExpressionStatement;
import lombok.ast.For;
import lombok.ast.ForEach;
import lombok.ast.Identifier;
import lombok.ast.If;
import lombok.ast.LabelledStatement;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.Return;
import lombok.ast.SuperConstructorInvocation;
import lombok.ast.Switch;
import lombok.ast.Synchronized;
import lombok.ast.Throw;
import lombok.ast.Try;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;
import lombok.ast.While;

import org.parboiled.BaseParser;
import org.parboiled.Rule;

public class StatementsParser extends BaseParser<Node> {
	final ParserGroup group;
	final StatementsActions actions;
	
	public StatementsParser(ParserGroup group) {
		this.actions = new StatementsActions(group.getSource());
		this.group = group;
	}
	
	public Rule anyStatement() {
		return labelledStatement();
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.2">JLS section 14.2</a>
	 */
	public Rule blockStatement() {
		return Sequence(
				Test(Ch('{')),
				actions.p(new Block()),
				Ch('{'), actions.structure(), group.basics.optWS(),
				ZeroOrMore(
						anyStatement(),
						actions.endPosByNode(),
						swap(),
						push(((Block) pop()).rawContents().addToEnd(pop()))),
				Ch('}'), actions.structure(),
				actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.3">JLS section 14.3</a>
	 */
	public Rule localClassDeclaration() {
		return group.structures.classOrInterfaceDeclaration();
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.4">JLS section 14.4</a>
	 */
	public Rule variableDefinition() {
		return Sequence(
				group.structures.modifiers(),
				group.structures.variableDefinition(),
				swap(),
				actions.setModifiersOnVarDef((Modifiers) pop(), peek()));
	}
	
	public Rule localVariableDeclaration() {
		return Sequence(
				variableDefinition(),
				Test(Ch(';')),
				actions.p(new VariableDeclaration()),
				actions.startPosByNode(),
				push(((VariableDeclaration) pop()).rawDefinition(pop())),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	public Rule explicitAlternateConstructorInvocation() {
		return Sequence(
				group.types.typeArguments(),
				Test(String("this"), group.basics.testLexBreak()),
				actions.p(new AlternateConstructorInvocation()),
				swap(),
				actions.addTypeArgsToAlternateConstructorInvocation(peek(1), pop()),
				String("this"), actions.structure(), group.basics.optWS(),
				group.structures.methodArguments(),
				actions.addArgsToAlternateConstructorInvocation(peek(1), pop()),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	public Rule explicitSuperConstructorInvocation() {
		return Sequence(
				actions.p(new SuperConstructorInvocation()),
				Optional(
						group.expressions.allPrimaryExpressions(),
						swap(),
						push(((SuperConstructorInvocation) pop()).rawQualifier(pop())),
						Ch('.'), actions.structure(), group.basics.optWS()),
				group.types.typeArguments(),
				Test(String("super"), group.basics.testLexBreak()),
				actions.addTypeArgsToSuperConstructorInvocation(peek(1), pop()),
				String("super"), actions.structure(), group.basics.optWS(),
				group.structures.methodArguments(),
				actions.addArgsToSuperConstructorInvocation(peek(1), pop()),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.6">JLS section 14.6</a>
	 */
	Rule emptyStatement() {
		return Sequence(
				Ch(';'),
				actions.p(new EmptyStatement()),
				group.basics.optWS());
	}
	
	/**
	 * Labels aren't statements; instead they can prefix any statement. Something like {@code if (1 == 1) foo: a();} is legal.
	 * Multiple labels for the same statement is also legal.
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.7">JLS section 14.7</a>
	 */
	public Rule labelledStatement() {
		return Sequence(
				actions.p(new TemporaryNode.SentinelNode()),
				ZeroOrMore(
						group.basics.identifier(),
						actions.turnToIdentifier(),
						Test(Ch(':')),
						actions.p(new LabelledStatement()),
						Ch(':'), actions.structure(), group.basics.optWS(),
						push(((LabelledStatement) pop()).astLabel((Identifier) pop()))),
				nonLabelledStatement(),
				actions.buildLabelStack());
	}
	
	Rule nonLabelledStatement() {
		return FirstOf(
				blockStatement(),
				localClassDeclaration(),
				localVariableDeclaration(),
				emptyStatement(),
				expressionStatement(),
				ifStatement(),
				assertStatement(),
				switchStatement(),
				caseStatement(),
				defaultStatement(),
				whileStatement(),
				doWhileStatement(),
				basicForStatement(),
				enhancedForStatement(),
				breakStatement(),
				continueStatement(),
				returnStatement(),
				synchronizedStatement(),
				throwStatement(),
				tryStatement(),
				explicitAlternateConstructorInvocation(),
				explicitSuperConstructorInvocation());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.8">JLS section 14.8</a>
	 */
	public Rule expressionStatement() {
		return Sequence(
				group.expressions.anyExpression(),
				Test(Ch(';')),
				actions.p(new ExpressionStatement().rawExpression(pop())),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.9">JLS section 14.9</a>
	 */
	public Rule ifStatement() {
		return Sequence(
				Test(String("if"), group.basics.testLexBreak()),
				actions.p(new If()),
				String("if"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression().label("condition"),
				swap(),
				push(((If) pop()).rawCondition(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				anyStatement().label("statement"),
				actions.endPosByNode(),
				swap(),
				push(((If) pop()).rawStatement(pop())),
				Optional(
						Test(String("else"), group.basics.testLexBreak()),
						String("else"), actions.structure(), group.basics.optWS(),
						anyStatement().label("else"),
						actions.endPosByNode(),
						push(((If) pop()).rawElseStatement(pop()))));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.10">JLS section 14.10</a>
	 */
	public Rule assertStatement() {
		return Sequence(
				Test(String("assert"), group.basics.testLexBreak()),
				actions.p(new Assert()),
				String("assert"), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression(),
				actions.endPosByNode(),
				swap(),
				push(((Assert) pop()).rawAssertion(pop())),
				Optional(
						Ch(':'), actions.structure(), group.basics.optWS(),
						group.expressions.anyExpression(),
						actions.endPosByNode(),
						swap(),
						push(((Assert) pop()).rawMessage(pop()))),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.11">JLS section 14.11</a>
	 */
	public Rule switchStatement() {
		return Sequence(
				Test(String("switch"), group.basics.testLexBreak()),
				actions.p(new Switch()),
				String("switch"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression(),
				swap(),
				push(((Switch) pop()).rawCondition(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				blockStatement(),
				actions.endPosByNode(),
				swap(),
				push(((Switch) pop()).rawBody(pop())));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.11">JLS section 14.11</a>
	 */
	public Rule caseStatement() {
		return Sequence(
				Test(String("case"), group.basics.testLexBreak()),
				actions.p(new Case()),
				String("case"), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression(),
				Ch(':'), actions.structure(),
				swap(),
				push(((Case) pop()).rawCondition(pop())),
				actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.11">JLS section 14.11</a>
	 */
	public Rule defaultStatement() {
		return Sequence(
				Test(String("default"), group.basics.testLexBreak()),
				actions.p(new Default()),
				String("default"), actions.structure(), group.basics.optWS(),
				Ch(':'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.12">JLS section 14.12</a>
	 */
	public Rule whileStatement() {
		return Sequence(
				Test(String("while"), group.basics.testLexBreak()),
				actions.p(new While()),
				String("while"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression().label("condition"),
				swap(),
				push(((While) pop()).rawCondition(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				anyStatement(),
				actions.endPosByNode(),
				swap(),
				push(((While) pop()).rawStatement(pop())));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.13">JLS section 14.13</a>
	 */
	public Rule doWhileStatement() {
		return Sequence(
				Test(String("do"), group.basics.testLexBreak()),
				actions.p(new DoWhile()),
				String("do"), actions.structure(), group.basics.optWS(),
				anyStatement(),
				actions.endPosByNode(),
				swap(),
				push(((DoWhile) pop()).rawStatement(pop())),
				Test(String("while"), group.basics.testLexBreak()),
				String("while"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression().label("condition"),
				swap(),
				push(((DoWhile) pop()).rawCondition(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.14.1.1">JLS section 14.14.1.1</a>
	 */
	public Rule basicForStatement() {
		return Sequence(
				Test(String("for"), group.basics.testLexBreak()),
				actions.p(new For()),
				String("for"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				Optional(forInit()),
				Ch(';'), actions.structure(), group.basics.optWS(),
				Optional(
						group.expressions.anyExpression(),
						swap(),
						push(((For) pop()).rawCondition(pop()))),
				Ch(';'), actions.structure(), group.basics.optWS(),
				Optional(forUpdate()),
				Ch(')'), actions.structure(), group.basics.optWS(),
				anyStatement(),
				actions.endPosByNode(),
				swap(),
				push(((For) pop()).rawStatement(pop())));
	}
	
	// A 'For' object MUST be top of stack.
	Rule forInit() {
		return FirstOf(
				Sequence(
						variableDefinition(),
						swap(),
						push(((For) pop()).rawVariableDeclaration(pop()))),
				Sequence(
						statementExpressionList(),
						actions.addStatementExpressionsToForInit(peek(1), pop())));
	}
	
	Rule forUpdate() {
		return Sequence(
				statementExpressionList(),
				actions.addStatementExpressionsToForUpdate(peek(1), pop()));
	}
	
	Rule statementExpressionList() {
		return Sequence(
				actions.p(new TemporaryNode.StatementExpressionList()),
				group.expressions.anyExpression(),
				swap(),
				((TemporaryNode.StatementExpressionList) peek(1)).expressions.add(pop()),
				ZeroOrMore(
						Ch(','), actions.structure(), group.basics.optWS(),
						group.expressions.anyExpression(),
						swap(),
						((TemporaryNode.StatementExpressionList) peek(1)).expressions.add(pop())));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.14.2">JLS section 14.14.2</a>
	 * @see <a href="http://bugs.sun.com/view_bug.do?bug_id=1699917">Bug 1699917</a>
	 */
	public Rule enhancedForStatement() {
		return Sequence(
				Test(String("for"), group.basics.testLexBreak()),
				actions.p(new ForEach()),
				String("for"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.structures.modifiers(),
				push(new VariableDefinition()),
				actions.startPosByNode(),
				push(((VariableDefinition) pop()).astModifiers((Modifiers) pop())),
				group.types.type(),
				swap(),
				push(((VariableDefinition) pop()).rawTypeReference(pop())),
				group.structures.variableDefinitionPartNoAssign(),
				actions.endPosByNode(),
				swap(),
				push(((VariableDefinition) pop()).rawVariables().addToEnd(pop())),
				Ch(':'), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression(),
				swap(),
				push(((ForEach) pop()).rawIterable(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				anyStatement(),
				actions.endPosByNode(), swap(),
				push(((ForEach) pop()).rawStatement(pop())));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.15">JLS section 14.15</a>
	 */
	public Rule breakStatement() {
		return Sequence(
				Test(String("break"), group.basics.testLexBreak()),
				actions.p(new Break()),
				String("break"), actions.structure(), actions.endPosByPos(), group.basics.optWS(),
				Optional(
						group.basics.identifier(),
						actions.turnToIdentifier(),
						actions.endPosByNode(),
						push(((Break) pop()).astLabel((Identifier) pop()))),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.16">JLS section 14.16</a>
	 */
	public Rule continueStatement() {
		return Sequence(
				Test(String("continue"), group.basics.testLexBreak()),
				actions.p(new Continue()),
				String("continue"), actions.structure(), actions.endPosByPos(), group.basics.optWS(),
				Optional(
						group.basics.identifier(),
						actions.turnToIdentifier(),
						actions.endPosByNode(),
						push(((Continue) pop()).astLabel((Identifier) pop()))),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.17">JLS section 14.17</a>
	 */
	public Rule returnStatement() {
		return Sequence(
				Test(String("return"), group.basics.testLexBreak()),
				actions.p(new Return()),
				String("return"), actions.structure(), actions.endPosByPos(), group.basics.optWS(),
				Optional(
						group.expressions.anyExpression(),
						actions.endPosByNode(),
						swap(),
						push(((Return) pop()).rawValue(pop()))),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.18">JLS section 14.18</a>
	 */
	public Rule throwStatement() {
		return Sequence(
				Test(String("throw"), group.basics.testLexBreak()),
				actions.p(new Throw()),
				String("throw"), actions.structure(), actions.endPosByPos(), group.basics.optWS(),
				group.expressions.anyExpression(),
				actions.endPosByNode(),
				swap(),
				push(((Throw) pop()).rawThrowable(pop())),
				Ch(';'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.19">JLS section 14.19</a>
	 */
	public Rule synchronizedStatement() {
		return Sequence(
				Test(String("synchronized"), group.basics.testLexBreak()),
				actions.p(new Synchronized()),
				String("synchronized"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.expressions.anyExpression().label("lock"),
				swap(),
				push(((Synchronized) pop()).rawLock(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				blockStatement().label("body"),
				actions.endPosByNode(),
				swap(),
				push(((Synchronized) pop()).rawBody(pop())));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.20">JLS section 14.20</a>
	 */
	public Rule tryStatement() {
		return Sequence(
				Test(String("try"), group.basics.testLexBreak()),
				actions.p(new Try()),
				String("try"), actions.structure(), group.basics.optWS(),
				blockStatement().label("body"),
				actions.endPosByNode(),
				swap(),
				push(((Try) pop()).rawBody(pop())),
				ZeroOrMore(
						catchBlock(),
						actions.endPosByNode(),
						swap(),
						push(((Try) pop()).rawCatches().addToEnd(pop()))),
				Optional(
						Test(String("finally"), group.basics.testLexBreak()),
						String("finally"), actions.structure(), group.basics.optWS(),
						blockStatement(),
						actions.endPosByNode(),
						swap(),
						push(((Try) pop()).rawFinally(pop()))));
	}
	
	Rule catchBlock() {
		return Sequence(
				Test(String("catch"), group.basics.testLexBreak()),
				actions.p(new Catch()),
				String("catch"), actions.structure(), group.basics.optWS(),
				Ch('('), actions.structure(), group.basics.optWS(),
				group.structures.modifiers(),
				push(new VariableDefinition()),
				actions.startPosByNode(),
				push(((VariableDefinition) pop()).astModifiers((Modifiers) pop())),
				group.types.type(),
				push(((VariableDefinition) pop()).rawTypeReference(pop())),
				group.basics.identifier().label("varName"),
				actions.turnToIdentifier(),
				push(new VariableDefinitionEntry()),
				actions.startPosByNode(),
				swap(), actions.endPosByNode(), swap(),
				push(((VariableDefinitionEntry) pop()).astName((Identifier) pop())),
				swap(),
				push(((VariableDefinition) pop()).rawVariables().addToEnd(pop())),
				swap(),
				push(((Catch) pop()).rawExceptionDeclaration(pop())),
				Ch(')'), actions.structure(), group.basics.optWS(),
				blockStatement().label("body"),
				actions.endPosByNode(),
				swap(),
				push(((Catch) pop()).rawBody(pop())));
	}
}
