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

import lombok.ast.Identifier;
import lombok.ast.Node;
import lombok.ast.TypeReference;
import lombok.ast.TypeReferencePart;
import lombok.ast.TypeVariable;
import lombok.ast.WildcardKind;

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.SuppressSubnodes;

public class TypesParser extends BaseParser<Node> {
	final ParserGroup group;
	final TypesActions actions;
	
	public TypesParser(ParserGroup group) {
		actions = new TypesActions(group.getSource());
		this.group = group;
	}
	
	public Rule nonArrayType() {
		return FirstOf(primitiveType(), referenceType());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#4.2">JLS section 4.2</a>
	 */
	public Rule type() {
		return Sequence(
				nonArrayType(),
				ZeroOrMore(
						Test(Ch('['), group.basics.optWS(), Ch(']')),
						Ch('['), actions.structure(),
						group.basics.optWS(),
						Ch(']'), actions.structure(),
						actions.incrementArrayDimensions()));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#4.2">JLS section 4.2</a>
	 */
	public Rule primitiveType() {
		return Sequence(
				rawPrimitiveType(),
				actions.p(new Identifier().astValue(match())),
				actions.p(new TypeReferencePart().astIdentifier((Identifier) pop())),
				actions.p(new TypeReference().rawParts().addToEnd(pop())),
				group.basics.optWS());
	}
	
	@SuppressSubnodes
	Rule rawPrimitiveType() {
		return Sequence(
				FirstOf("boolean", "int", "long", "double", "float", "short", "char", "byte", "void"),
				group.basics.testLexBreak());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#4.3">JLS section 4.3</a>
	 */
	public Rule referenceType() {
		return refTypeInternal(referenceTypePart());
	}
	
	public Rule plainReferenceType() {
		return refTypeInternal(plainReferenceTypePart());
	}
	
	Rule refTypeInternal(Rule partRule) {
		return Sequence(
				partRule,
				actions.p(new TypeReference()),
				swap(), actions.endPosByNode(),
				swap(), push(((TypeReference) pop()).rawParts().addToEnd(pop())),
				ZeroOrMore(
						Ch('.'), actions.structure(), group.basics.optWS(),
						partRule,
						actions.endPosByNode(),
						swap(),
						push(((TypeReference) pop()).rawParts().addToEnd(pop()))));
	}
	
	Rule referenceTypePart() {
		return Sequence(
				plainReferenceTypePart(),
				typeArguments(),
				actions.addTypeArgsToTypeRefPart(peek(1), pop()));
	}
	
	Rule plainReferenceTypePart() {
		return Sequence(
				group.basics.identifier(),
				actions.turnToIdentifier(),
				actions.p(new TypeReferencePart()),
				swap(), actions.endPosByNode(),
				swap(), push(((TypeReferencePart) pop()).astIdentifier((Identifier) pop())));
	}
	
	public Rule typeVariables() {
		return Sequence(
				actions.p(new TemporaryNode.OrphanedTypeVariables()),
				Optional(
						Ch('<'), actions.structure(), group.basics.optWS(),
						Optional(
								typeVariable(),
								((TemporaryNode.OrphanedTypeVariables) peek(1)).variables.add(pop()),
								ZeroOrMore(
										Ch(','), actions.structure(),
										group.basics.optWS(),
										typeVariable(),
										((TemporaryNode.OrphanedTypeVariables) peek(1)).variables.add(pop()))),
						Ch('>'), actions.structure(), actions.endPosByPos(), group.basics.optWS()));
	}
	
	Rule typeVariable() {
		return Sequence(
				group.basics.identifier(),
				actions.turnToIdentifier(),
				actions.p(new TypeVariable()),
				swap(), actions.endPosByNode(),
				swap(), push(((TypeVariable) pop()).astName((Identifier) pop())),
				Optional(
						Test(String("extends"), group.basics.testLexBreak()),
						String("extends"), actions.structure(),
						//TODO It's not legal java but it's a common mistake; we should
						// read 'super' too, create a list for it, so that the downstream checker
						// can generate much nicer errors for it.
						
						group.basics.optWS(),
						type(),
						actions.endPosByNode(),
						swap(), push(((TypeVariable) pop()).rawExtending().addToEnd(pop())),
						ZeroOrMore(
								Ch('&'), actions.structure(), group.basics.optWS(),
								type(),
								actions.endPosByNode(),
								swap(), push(((TypeVariable) pop()).rawExtending().addToEnd(pop())))));
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#4.5">JLS section 4.5</a>
	 */
	public Rule typeArguments() {
		return Sequence(
				actions.p(new TemporaryNode.TypeArguments()),
				Optional(
						Ch('<'), actions.structure(), group.basics.optWS(),
						Optional(
								typeArgument(),
								((TemporaryNode.TypeArguments) peek(1)).arguments.add(pop()),
								ZeroOrMore(
										Ch(','), actions.structure(),
										group.basics.optWS(),
										typeArgument(),
										((TemporaryNode.TypeArguments) peek(1)).arguments.add(pop()))),
						Ch('>'), actions.structure(), actions.endPosByPos(), group.basics.optWS()));
	}
	
	public Rule typeArgument() {
		return FirstOf(
				type(),
				Sequence(
						Test(Ch('?')),
						actions.p(new TypeReference().astWildcard(WildcardKind.UNBOUND)),
						Ch('?'), actions.structure(),
						actions.endPosByPos(),
						group.basics.optWS(),
						Optional(
								FirstOf(
										Sequence(
												Test(String("extends"), group.basics.testLexBreak()),
												push(((TypeReference) pop()).astWildcard(WildcardKind.EXTENDS)),
												String("extends"), actions.structure()),
										Sequence(
												Test(String("super"), group.basics.testLexBreak()),
												push(((TypeReference) pop()).astWildcard(WildcardKind.SUPER)),
												String("extends"), actions.structure())),
								group.basics.optWS(),
								type(),
								actions.mergeTypeRefs())));
	}
}
