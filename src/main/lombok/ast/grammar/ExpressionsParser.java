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

import lombok.ast.ArrayAccess;
import lombok.ast.ArrayCreation;
import lombok.ast.ArrayDimension;
import lombok.ast.ArrayInitializer;
import lombok.ast.BinaryExpression;
import lombok.ast.Cast;
import lombok.ast.ClassLiteral;
import lombok.ast.ConstructorInvocation;
import lombok.ast.Identifier;
import lombok.ast.InlineIfExpression;
import lombok.ast.InstanceOf;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.Select;
import lombok.ast.Super;
import lombok.ast.This;
import lombok.ast.UnaryExpression;
import lombok.ast.UnaryOperator;
import lombok.ast.VariableReference;

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.Cached;

public class ExpressionsParser extends BaseParser<Node> {
	final ParserGroup group;
	final ExpressionsActions actions;
	
	public ExpressionsParser(ParserGroup group) {
		this.actions = new ExpressionsActions(group.getSource());
		this.group = group;
	}
	
	/**
	 * P0
	 */
	public Rule primaryExpression() {
		return FirstOf(
				parenGrouping(),
				group.literals.anyLiteral(),
				unqualifiedThisLiteral(),
				unqualifiedSuperLiteral(),
				arrayCreationExpression(),
				unqualifiedConstructorInvocation(),
				classLiteral(),
				qualifiedThisLiteral(),
				qualifiedSuperLiteral(),
				identifierExpression());
	}
	
	Rule parenGrouping() {
		return Sequence(
				Ch('('), group.basics.optWS(),
				anyExpression(),
				Ch(')'), push(actions.addParens(pop())),
				group.basics.optWS());
	}
	
	Rule unqualifiedThisLiteral() {
		return Sequence(
				Test(String("this"), group.basics.testLexBreak()),
				actions.p(new This()),
				String("this"), actions.endPosByPos(),
				group.basics.optWS(), TestNot(Ch('(')));
	}
	
	Rule unqualifiedSuperLiteral() {
		return Sequence(
				Test(String("super"), group.basics.testLexBreak()),
				actions.p(new Super()),
				String("super"), actions.endPosByPos(),
				group.basics.optWS(), TestNot(Ch('(')));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.8.2">JLS section 15.8.2</a>
	 */
	Rule classLiteral() {
		return Sequence(
				group.types.type(),
				actions.p(new ClassLiteral()),
				push(((ClassLiteral) pop()).rawTypeReference(pop())),
				Ch('.'), actions.structure(),
				group.basics.optWS(),
				String("class"), actions.structure(), group.basics.testLexBreak(),
				actions.endPosByPos(),
				group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.8.2">JLS section 15.8.2</a>
	 */
	Rule qualifiedThisLiteral() {
		return Sequence(
				group.types.type(),
				actions.p(new This()),
				push(((This) pop()).rawQualifier(pop())),
				Ch('.'), actions.structure(),
				group.basics.optWS(),
				String("this"), actions.structure(), group.basics.testLexBreak(),
				actions.endPosByPos(),
				group.basics.optWS());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.8.2">JLS section 15.8.2</a>
	 */
	Rule qualifiedSuperLiteral() {
		return Sequence(
				group.types.type(),
				actions.p(new Super()),
				push(((Super) pop()).rawQualifier(pop())),
				Ch('.'), actions.structure(),
				group.basics.optWS(),
				String("super"), actions.structure(), group.basics.testLexBreak(),
				actions.endPosByPos(),
				group.basics.optWS());
	}
	
	Rule unqualifiedConstructorInvocation() {
		return Sequence(
				Test(String("new"), group.basics.testLexBreak()),
				actions.p(new ConstructorInvocation()),
				String("new"), actions.structure(), group.basics.optWS(),
				group.types.typeArguments().label("constructorTypeArgs"),
				actions.addTypeArgsToConstructorInvocation(peek(1), pop()),
				group.types.type(),
				swap(), push(((ConstructorInvocation) pop()).rawTypeReference(pop())),
				group.structures.methodArguments().label("constructorArguments"),
				actions.endPosByNode(),
				actions.addArgsToConstructorInvocation(peek(1), pop()),
				Optional(
						group.structures.typeBody().label("anonymousTypeBody"),
						actions.endPosByNode(),
						swap(), push(((ConstructorInvocation) pop()).rawAnonymousClassBody(pop()))));
	}

	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/arrays.html#10.3">JLS section 10.3</a>
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.10">JLS section 15.10</a>
	 */
	Rule arrayCreationExpression() {
		return Sequence(
				Test(String("new"), group.basics.testLexBreak()),
				actions.p(new ArrayCreation()),
				String("new"), actions.structure(), group.basics.optWS(),
				group.types.nonArrayType().label("type"),
				swap(), push(((ArrayCreation) pop()).rawComponentTypeReference(pop())),
				OneOrMore(
						arrayDimension(),
						actions.endPosByNode(),
						swap(), push(((ArrayCreation) pop()).rawDimensions().addToEnd(pop()))),
				Optional(
						arrayInitializer(),
						actions.endPosByNode(),
						push(((ArrayCreation) pop()).rawInitializer(pop()))));
	}
	
	Rule arrayDimension() {
		return Sequence(
				Test(Ch('[')),
				actions.p(new ArrayDimension()),
				Ch('['), actions.structure(), group.basics.optWS(),
				Optional(
						anyExpression().label("dimension"),
						Test(Ch(']')),
						swap(), push(((ArrayDimension) pop()).rawDimension(pop()))),
				Ch(']'), actions.structure(), actions.endPosByPos(), group.basics.optWS());
	}
	
	public Rule arrayInitializer() {
		return arrayInitializerInternal(FirstOf(arrayInitializer(), anyExpression()));
	}
	
	Rule arrayInitializerInternal(Rule element) {
		return Sequence(
				Test(Ch('{')),
				actions.p(new ArrayInitializer()),
				Ch('{'), actions.structure(),
				group.basics.optWS(),
				Optional(
						element.label("head"),
						swap(), push(((ArrayInitializer) pop()).rawExpressions().addToEnd(pop())),
						ZeroOrMore(
								Ch(','), group.basics.optWS(),
								element.label("tail"),
								swap(), push(((ArrayInitializer) pop()).rawExpressions().addToEnd(pop()))),
						Optional(Ch(','), group.basics.optWS())),
				Ch('}'), actions.endPosByPos(), group.basics.optWS());
	}
	
	Rule identifierExpression() {
		return Sequence(
				actions.p(new VariableReference()),
				group.basics.identifier(),
				actions.turnToIdentifier(),
				actions.endPosByNode(),
				swap(),
				push(((VariableReference) pop()).astIdentifier((Identifier) pop())),
				Optional(
						group.structures.methodArguments(),
						swap(), push(actions.createSimpleMethodInvocation(pop(), pop()))));
	}
	
	public Rule anyExpression() {
		return assignmentExpressionChaining();
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/statements.html#14.8">JLS section 14.8</a>
	 */
	public Rule statementExpression() {
		// While in theory only assignmentExpression, postfix/prefix increment/decrement, constructor, or method invocations are allowed,
		// if you type '5;', then we should return an AST that represents what you meant, even if it won't compile, so we allow any expression
		// as statement expression.
		return anyExpression();
	}
	
	public Rule allPrimaryExpressions() {
		return Sequence(level1ExpressionChaining(), EMPTY);
	}
	
	/**
	 * P1
	 */
	Rule level1ExpressionChaining() {
		return Sequence(
				primaryExpression(),
				ZeroOrMore(FirstOf(
						Sequence(
								arrayAccessOperation(),
								actions.startPosByNode(),
								push(((ArrayAccess) pop()).rawOperand(pop()))),
						Sequence(
								methodInvocationWithTypeArgsOperation(),
								actions.startPosByNode(),
								push(((MethodInvocation) pop()).rawOperand(pop()))),
						Sequence(
								select(),
								actions.startPosByNode(),
								push(((Select) pop()).rawOperand(pop()))))));
	}
	
	Rule arrayAccessOperation() {
		return Sequence(
				Test(Ch('[')),
				actions.p(new ArrayAccess()),
				Ch('['), actions.structure(), group.basics.optWS(),
				anyExpression(),
				swap(), push(((ArrayAccess) pop()).rawIndexExpression(pop())),
				Ch(']'), actions.structure(), actions.endPosByPos(),
				group.basics.optWS());
	}
	
	Rule methodInvocationWithTypeArgsOperation() {
		return Sequence(
				Test(Ch('.')),
				actions.p(new MethodInvocation()),
				Ch('.'), actions.structure(), group.basics.optWS(),
				group.types.typeArguments(),
				actions.addTypeArgsToMethodInvocation(peek(1), pop()),
				group.basics.identifier().label("name"),
				actions.turnToIdentifier(),
				swap(), push(((MethodInvocation) pop()).astName((Identifier) pop())),
				group.structures.methodArguments(),
				actions.endPosByNode(),
				actions.addArgsToMethodInvocation(peek(1), pop()));
	}
	
	Rule select() {
		return Sequence(
				group.basics.dotIdentifier().label("identifier"),
				actions.turnToIdentifier(),
				TestNot(Ch('(')),
				actions.p(new Select()),
				swap(), actions.endPosByNode(),
				swap(), push(((Select) pop()).astIdentifier((Identifier) pop())));
	}
	
	/**
	 * P2''
	 * 
	 * This is the relational new operator; not just 'new', but new with context, so: "a.new InnerClass(params)". It is grouped with P2, but for some reason has higher precedence
	 * in all java parsers, and so we give it its own little precedence group here.
	 */
	Rule dotNewExpressionChaining() {
		return Sequence(
				level1ExpressionChaining().label("head"),
				ZeroOrMore(
						Sequence(
								Ch('.'),
								group.basics.optWS(),
								String("new"),
								group.basics.testLexBreak(),
								group.basics.optWS()),
						push(new ConstructorInvocation()),
						actions.startPosByNode(),
						swap(), push(((ConstructorInvocation) pop()).rawQualifier(pop())),
						group.types.typeArguments().label("constructorTypeArgs"),
						actions.addTypeArgsToConstructorInvocation(peek(1), pop()),
						group.basics.identifier().label("innerClassName"),
						actions.turnToIdentifier(),
						group.types.typeArguments(),
						swap(), actions.addInnerTypeToConstructorInvocation(peek(2), (Identifier) pop(), pop()),
						group.structures.methodArguments(),
						actions.endPosByNode(),
						actions.addArgsToConstructorInvocation(peek(1), pop()),
						Optional(
								group.structures.typeBody().label("anonymousTypeBody"),
								actions.endPosByNode(),
								swap(), push(((ConstructorInvocation) pop()).rawAnonymousClassBody(pop())))));
	}
	
	/**
	 * P2'
	 * Technically, postfix increment operations are in P2 along with all the unary operators like ~ and !, as well as typecasts.
	 * However, because ALL of the P2 expression are right-associative, the postfix operators can be considered as a higher level of precedence.
	 */
	Rule postfixIncrementExpressionChaining() {
		return Sequence(
				dotNewExpressionChaining(),
				ZeroOrMore(
						FirstOf(
								unaryPostfix(String("++"), UnaryOperator.POSTFIX_INCREMENT),
								unaryPostfix(String("--"), UnaryOperator.POSTFIX_DECREMENT)),
						group.basics.optWS()));
	}
	
	// Call this with 'operand' on top of stack.
	Rule unaryPostfix(Rule operator, UnaryOperator op) {
		return Sequence(
				Test(operator),
				push(new UnaryExpression().astOperator(op)),
				actions.startPosByNode(),
				operator, actions.structure(),
				push(((UnaryExpression) pop()).rawOperand(pop())));

	}
	
	Rule unaryPrefix(Rule operator, UnaryOperator op) {
		return Sequence(
				Test(operator),
				actions.p(new UnaryExpression().astOperator(op)),
				operator, actions.structure(),
				group.basics.optWS(),
				level2ExpressionChaining(),
				actions.endPosByNode(),
				swap(), push(((UnaryExpression) pop()).rawOperand(pop())));
	}
	
	/**
	 * P2
	 */
	Rule level2ExpressionChaining() {
		return FirstOf(
				unaryPrefix(String("++"), UnaryOperator.PREFIX_INCREMENT),
				unaryPrefix(String("--"), UnaryOperator.PREFIX_DECREMENT),
				unaryPrefix(Ch('!'), UnaryOperator.LOGICAL_NOT),
				unaryPrefix(Ch('~'), UnaryOperator.BINARY_NOT),
				unaryPrefix(solitarySymbol('+'), UnaryOperator.UNARY_PLUS),
				unaryPrefix(solitarySymbol('-'), UnaryOperator.UNARY_MINUS),
				Sequence(
						Test(Ch('(')),
						actions.p(new Cast()),
						Ch('('), actions.structure(), group.basics.optWS(),
						group.types.type(),
						swap(), push(((Cast) pop()).rawTypeReference(pop())),
						Ch(')'), actions.structure(),
						TestNot(Sequence(
								actions.typeIsAlsoLegalAsExpression(((Cast) peek()).rawTypeReference()),
								group.basics.optWS(),
								FirstOf(solitarySymbol('+'), solitarySymbol('-')))),
						group.basics.optWS(),
						level2ExpressionChaining(),
						actions.endPosByNode(),
						swap(), push(((Cast) pop()).rawOperand(pop()))),
				postfixIncrementExpressionChaining());
	}
	
	/**
	 * P3
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.17">JLS section 15.17</a>
	 */
	Rule multiplicativeExpressionChaining() {
		return forLeftAssociativeBinaryExpression(FirstOf(Ch('*'), solitarySymbol('/'), Ch('%')), level2ExpressionChaining());
	}
	
	/**
	 * P4
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.18">JLS section 15.18</a>
	 */
	Rule additiveExpressionChaining() {
		return forLeftAssociativeBinaryExpression(FirstOf(solitarySymbol('+'), solitarySymbol('-')), multiplicativeExpressionChaining());
	}
	
	/**
	 * P5
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.19">JLS section 15.19</a>
	 */
	Rule shiftExpressionChaining() {
		return forLeftAssociativeBinaryExpression(FirstOf(String(">>>"), String("<<<"), String("<<"), String(">>")), additiveExpressionChaining());
	}
	
	/**
	 * P6
	 * 
	 * Technically 'instanceof' is on equal footing with the other operators, but practically speaking this doesn't hold;
	 * for starters, the RHS of instanceof is a Type and not an expression, and the inevitable type of an instanceof expression (boolean) is
	 * not compatible as LHS to *ANY* of the operators in this class, including instanceof itself. Therefore, pragmatically speaking, there can only
	 * be one instanceof, and it has to appear at the end of the chain.
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.20">JLS section 15.20</a>
	 */
	Rule relationalExpressionChaining() {
		return Sequence(
				forLeftAssociativeBinaryExpression(FirstOf(String("<="), String(">="), solitarySymbol('<'), solitarySymbol('>')), shiftExpressionChaining()),
				ZeroOrMore(
						Test(String("instanceof"), group.basics.testLexBreak()),
						push(new InstanceOf()),
						actions.startPosByNode(),
						push(((InstanceOf) pop()).rawObjectReference(pop())),
						String("instanceof"), actions.structure(),
						group.basics.optWS(),
						group.types.type(),
						actions.endPosByNode(),
						swap(),
						push(((InstanceOf) pop()).rawTypeReference(pop()))));
	}
	
	/**
	 * P7
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.21">JLS section 15.21</a>
	 */
	Rule equalityExpressionChaining() {
		return forLeftAssociativeBinaryExpression(FirstOf(String("==="), String("!=="), String("=="), String("!=")), relationalExpressionChaining());
	}
	
	/**
	 * P8
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.22">JLS section 15.22</a>
	 */
	Rule bitwiseAndExpressionChaining() {
		return forLeftAssociativeBinaryExpression(solitarySymbol('&'), equalityExpressionChaining());
	}
	
	/**
	 * P9
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.22">JLS section 15.22</a>
	 */
	Rule bitwiseXorExpressionChaining() {
		return forLeftAssociativeBinaryExpression(solitarySymbol('^'), bitwiseAndExpressionChaining());
	}
	
	/**
	 * P10
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.22">JLS section 15.22</a>
	 */
	Rule bitwiseOrExpressionChaining() {
		return forLeftAssociativeBinaryExpression(solitarySymbol('|'), bitwiseXorExpressionChaining());
	}
	
	/**
	 * P11
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.23">JLS section 15.23</a>
	 */
	Rule conditionalAndExpressionChaining() {
		return forLeftAssociativeBinaryExpression(String("&&"), bitwiseOrExpressionChaining());
	}
	
	/**
	 * P12'
	 * 
	 * This is not a legal operator; however, it is entirely imaginable someone presumes it does exist.
	 * It also has no other sensible meaning, so we will parse it and flag it as a syntax error in AST phase.
	 */
	Rule conditionalXorExpressionChaining() {
		return forLeftAssociativeBinaryExpression(String("^^"), conditionalAndExpressionChaining());
	}
	
	/**
	 * P12
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.24">JLS section 15.24</a>
	 */
	Rule conditionalOrExpressionChaining() {
		return forLeftAssociativeBinaryExpression(String("||"), conditionalXorExpressionChaining());
	}
	
	/**
	 * P13
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.25">JLS section 15.25</a>
	 */
	Rule inlineIfExpressionChaining() {
		return Sequence(
				conditionalOrExpressionChaining(),
				Optional(
						Test(Ch('?')),
						push(new InlineIfExpression()),
						actions.startPosByNode(),
						push(((InlineIfExpression) pop()).rawCondition(pop())),
						Ch('?'), actions.structure(),
						TestNot(FirstOf(Ch('.'), Ch(':'), Ch('?'))),
						group.basics.optWS(),
						assignmentExpressionChaining(),
						swap(), push(((InlineIfExpression) pop()).rawIfTrue(pop())),
						Ch(':'), actions.structure(),
						group.basics.optWS(),
						inlineIfExpressionChaining(),
						actions.endPosByNode(),
						swap(), push(((InlineIfExpression) pop()).rawIfFalse(pop()))));
	}
	
	/**
	 * P14
	 * 
	 * Not all of the listed operators are actually legal, but if not legal, then they are at least imaginable, so we parse them and flag them as errors in the AST phase.
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#15.26">JLS section 15.26</a>
	 */
	Rule assignmentExpressionChaining() {
		return Sequence(
				inlineIfExpressionChaining(),
				actions.printStack("A"),
				Optional(
						assignmentOperator(),
						push(new BinaryExpression().rawOperator(match())),
						actions.structure(),
						actions.startPosByNode(),
						actions.printStack("C"),
						actions.printNode(peek(1)),
						push(((BinaryExpression) pop()).rawLeft(pop())),
						group.basics.optWS(),
						assignmentExpressionChaining(),
						actions.printStack("B"),
						actions.endPosByNode(),
						swap(), push(((BinaryExpression) pop()).rawRight(pop()))));
	}
	
	Rule assignmentOperator() {
		return FirstOf(
				solitarySymbol('='),
				String("*="), String("/="), String("+="), String("-="), String("%="),
				String(">>>="), String("<<<="), String("<<="), String(">>="),
				String("&="), String("^="), String("|="),
				String("&&="), String("^^="), String("||="));
	}
	
	/**
	 * @param operator Careful; operator has to match _ONLY_ the operator, not any whitespace around it (otherwise we'd have to remove comments from it, which isn't feasible).
	 */
	@Cached
	Rule forLeftAssociativeBinaryExpression(Rule operator, Rule nextHigher) {
		return Sequence(
				nextHigher,
				ZeroOrMore(
						operator,
						push(new BinaryExpression().rawOperator(match())),
						actions.structure(),
						actions.startPosByNode(),
						push(((BinaryExpression) pop()).rawLeft(pop())),
						group.basics.optWS(),
						nextHigher,
						actions.endPosByNode(),
						swap(), push(((BinaryExpression) pop()).rawRight(pop()))));
	}
	
	Rule solitarySymbol(char c) {
		return Sequence(Ch(c), TestNot(Ch(c)));
	}
}
