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

import lombok.ast.DanglingNodes;
import lombok.ast.Identifier;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.Position;

import org.parboiled.BaseActions;

class SourceActions extends BaseActions<Node> {
	protected final Source source;
	
	SourceActions(Source source) {
		this.source = source;
	}
	
	public boolean printNode(Node node) {
		System.out.printf("PARENT: %s\n--\n%s\n", node.getParent(), node);
		return true;
	}
	
	public boolean printStack(String key) {
		int idx = 0;
		System.out.printf("%s: PRINTING STACK AT %d\n", key, currentPos());
		while (true) {
			try {
				Node elem = peek(idx);
				System.out.printf("%3d: %20s - %s\n", idx++, elem.getClass().getSimpleName(), elem);
			} catch (IllegalArgumentException e) {
				break;
			}
		}
		return true;
	}
	
	boolean p(Node value) {
		return push(value.setPosition(new Position(startPos(), currentPos())));
	}
	
	boolean startPosByNode() {
		Node p = peek();
		p.setPosition(new Position(peek(1).getPosition().getStart(), p.getPosition().getEnd()));
		return true;
	}
	
	boolean endPosByNode() {
		Node p = peek(1);
		p.setPosition(new Position(p.getPosition().getStart(), peek().getPosition().getEnd()));
		return true;
	}
	
	boolean endPosByPos() {
		Node p = peek();
		p.setPosition(new Position(p.getPosition().getStart(), currentPos()));
		return true;
	}
	
	boolean structure() {
		source.registerStructure(getContext().getValueStack().peek(), matchStart(), matchEnd(), match());
		return true;
	}
	
	boolean turnToIdentifier() {
		Node v = pop();
		if (v instanceof Identifier) {
			push(v);
			return true;
		}
		
		Identifier i = new Identifier();
		i.setPosition(new Position(currentPos(), currentPos()));
		DanglingNodes.addDanglingNode(i, v);
		push(i);
		return true;
	}
	
	Modifiers createModifiersIfNeeded(Node modifiers, int pos) {
		if (modifiers instanceof Modifiers) return (Modifiers)modifiers;
		Modifiers m = new Modifiers();
		m.setPosition(new Position(pos, pos));
		DanglingNodes.addDanglingNode(m, modifiers);
		return m;
	}
	
	int startPos() {
		return getContext().getStartIndex();
	}
	
	int currentPos() {
		return getContext().getCurrentIndex();
	}
}
