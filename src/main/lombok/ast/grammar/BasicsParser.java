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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.ast.Node;

import org.parboiled.BaseParser;
import org.parboiled.MatcherContext;
import org.parboiled.Rule;
import org.parboiled.annotations.SuppressSubnodes;
import org.parboiled.matchers.CustomMatcher;

/**
 * Contains the basics of java parsing: Whitespace and comment handling, as well as applying backslash-u escapes.
 */
public class BasicsParser extends BaseParser<Node> {
	final ParserGroup group;
	final BasicsActions actions;
	
	public BasicsParser(ParserGroup group) {
		this.group = group;
		this.actions = new BasicsActions(group.getSource());
	}
	
	/**
	 * Eats up any whitespace and comments at the current position.
	 */
	public Rule optWS() {
		return ZeroOrMore(FirstOf(comment(), whitespaceChar())).label("ws");
	}
	
	/**
	 * Eats up any whitespace and comments at the current position,
	 * but only matches if there is at least one comment or whitespace character to gobble up.
	 */
	public Rule mandatoryWS() {
		return OneOrMore(FirstOf(comment(), whitespaceChar())).label("ws");
	}
	
	public Rule testLexBreak() {
		return TestNot(identifierPart());
	}
	
	public Rule identifier() {
		return Sequence(
				identifierRaw(),
				actions.checkIfKeyword(match()),
				push(actions.createIdentifier(match(), matchStart(), matchEnd())),
				optWS());
	}
	
	public Rule dotIdentifier() {
		return Sequence(
				Ch('.'), optWS(),
				identifierRaw().label("identifier"),
				actions.checkIfKeyword(match()),
				push(actions.createIdentifier(match(), matchStart(), matchEnd())),
				optWS());
	}
	
	/**
	 * Technically {@code null}, {@code true} and {@code false} aren't keywords but specific literals, but, from a parser point of view they are keywords.
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.9">JLS section 3.9</a>
	 */
	static final List<String> KEYWORDS = Collections.unmodifiableList(Arrays.asList(
			"abstract", "class", "interface", "enum", "static", "final", "volatile", "transient", "strictfp", "native",
			"boolean", "byte", "short", "char", "int", "long", "float", "double", "void",
			"null", "this", "super", "true", "false",
			"continue", "break", "goto", "case", "default", "instanceof",
			"if", "do", "while", "for", "else", "synchronized", "switch", "assert", "throw", "try", "catch", "finally", "new", "return",
			"throws", "extends", "implements",
			"import", "package", "const",
			"public", "private", "protected"
	));
	
	@SuppressSubnodes
	public Rule identifierRaw() {
		return Sequence(new JavaIdentifierStartMatcher(), ZeroOrMore(new JavaIdentifierPartMatcher()));
	}
	
	@SuppressSubnodes
	public Rule identifierPart() {
		return new JavaIdentifierPartMatcher();
	}
	
	private static final class JavaIdentifierStartMatcher extends AbstractJavaIdentifierMatcher {
		public JavaIdentifierStartMatcher() {
			super("IdentifierStart");
		}
		
		@Override protected boolean acceptChar(char c) {
			return Character.isJavaIdentifierStart(c);
		}
	}
	
	private static final class JavaIdentifierPartMatcher extends AbstractJavaIdentifierMatcher {
		public JavaIdentifierPartMatcher() {
			super("IdentifierPart");
		}
		
		@Override protected boolean acceptChar(char c) {
			return Character.isJavaIdentifierPart(c);
		}
	}
	
	private static abstract class AbstractJavaIdentifierMatcher extends CustomMatcher {
		AbstractJavaIdentifierMatcher(String label) { super(label); }
		@Override public final boolean isSingleCharMatcher() { return true; }
		@Override public final boolean canMatchEmpty() { return false; }
		@Override public final char getStarterChar() { return 'a'; }
		@Override public final boolean isStarterChar(char c) { return acceptChar(c); }
		@Override public final <V> boolean match(MatcherContext<V> context) {
			char current = context.getCurrentChar();
			if (!acceptChar(current)) return false;
			context.advanceIndex(1);
			context.createNode();
			return true;
		}
		protected abstract boolean acceptChar(char c);
	}
	
	/**
	 * Any comment (block, line, or javadoc)
	 * 
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.7">JLS section 3.7</a>
	 */
	public Rule comment() {
		return Sequence(
				FirstOf(lineComment(), blockComment()),
				actions.logComment(match()));
	}
	
	@SuppressSubnodes
	Rule lineComment() {
		return Sequence(String("//"), ZeroOrMore(Sequence(TestNot(AnyOf("\r\n")), ANY)), FirstOf(String("\r\n"), Ch('\r'), Ch('\n'), Test(EOI)));
	}
	
	@SuppressSubnodes
	Rule blockComment() {
		return Sequence("/*", ZeroOrMore(Sequence(TestNot("*/"), ANY)), "*/");
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.4">JLS section 3.4</a>
	 */
	@SuppressSubnodes
	Rule whitespaceChar() {
		return FirstOf(Ch(' '), Ch('\t'), Ch('\f'), lineTerminator());
	}
	
	/**
	 * @see <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.6">JLS section 3.6</a>
	 */
	@SuppressSubnodes
	public Rule lineTerminator() {
		return FirstOf(String("\r\n").label("\\r\\n"), Ch('\r').label("\\r"), Ch('\n').label("\\n"));
	}
}