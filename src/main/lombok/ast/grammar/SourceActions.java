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
package lombok.ast.grammar;

import lombok.ast.Node;
import lombok.ast.Position;

import org.parboiled.BaseActions;

class SourceActions extends BaseActions<Node> {
	protected final Source source;
	
	SourceActions(Source source) {
		this.source = source;
	}
	
	<T extends Node> T posify(T node) {
		int start = source.mapPosition(getContext().getStartLocation().index);
		int end = getCurrentLocationRtrim();
		node.setPosition(new Position(start, end));
		return node;
	}
	
	int getCurrentLocationRtrim() {
		return source.mapPositionRtrim(getContext().getCurrentLocation().index);
	}
	
	void positionSpan(Node target, Node start, Node end) {
		if (target == null || start == null || end == null || start.getPosition().isUnplaced() || end.getPosition().isUnplaced()) return;
		target.setPosition(new Position(start.getPosition().getStart(), end.getPosition().getEnd()));
	}
}