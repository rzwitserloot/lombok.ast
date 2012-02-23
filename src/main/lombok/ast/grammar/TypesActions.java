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

import lombok.ast.Node;
import lombok.ast.Position;
import lombok.ast.TypeReference;
import lombok.ast.TypeReferencePart;

public class TypesActions extends SourceActions {
	public TypesActions(Source source) {
		super(source);
	}
	
	public boolean addTypeArgsToTypeRefPart(Node typeRefPart, Node typeArgs) {
		if (typeArgs instanceof TemporaryNode.TypeArguments && typeRefPart instanceof TypeReferencePart) {
			TypeReferencePart trp = (TypeReferencePart) typeRefPart;
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				trp.rawTypeArguments().addToEnd(arg);
			}
			source.transportLogistics(typeArgs, typeRefPart);
		}
		
		return true;
	}
	
	public boolean mergeTypeRefs() {
		Node main_ = pop();
		Node wildcard_ = pop();
		
		source.transportLogistics(wildcard_, main_);
		if (wildcard_ instanceof TypeReference && main_ instanceof TypeReference) {
			TypeReference wildcard = (TypeReference) wildcard_;
			TypeReference main = (TypeReference) main_;
			main.astWildcard(wildcard.astWildcard());
			main.setPosition(new Position(wildcard.getPosition().getStart(), main.getPosition().getEnd()));
		}
		
		
		push(main_);
		return true;
	}
	
	public boolean incrementArrayDimensions() {
		Node top = peek();
		if (top instanceof TypeReference) {
			TypeReference ref = (TypeReference) top;
			ref.astArrayDimensions(ref.astArrayDimensions() + 1);
		}
		return true;
	}
}
