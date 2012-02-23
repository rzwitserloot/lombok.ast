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

import lombok.ast.ConstructorInvocation;
import lombok.ast.DanglingNodes;
import lombok.ast.Expression;
import lombok.ast.Identifier;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.Position;
import lombok.ast.TypeReference;
import lombok.ast.TypeReferencePart;
import lombok.ast.VariableReference;

public class ExpressionsActions extends SourceActions {
	public ExpressionsActions(Source source) {
		super(source);
	}
	
	public Node createSimpleMethodInvocation(Node varRef, Node methodArguments) {
		VariableReference vRef = (VariableReference) varRef;
		MethodInvocation mInv = new MethodInvocation().astName(vRef.astIdentifier());
		if (methodArguments instanceof TemporaryNode.MethodArguments) {
			for (Node arg : ((TemporaryNode.MethodArguments)methodArguments).arguments) {
				mInv.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(methodArguments, mInv);
			mInv.setPosition(new Position(varRef.getPosition().getStart(), methodArguments.getPosition().getEnd()));
		} else {
			mInv.setPosition(vRef.getPosition());
		}
		return mInv;
	}
	
	public boolean addTypeArgsToConstructorInvocation(Node constructorInvocation, Node typeArgs) {
		if (typeArgs instanceof TemporaryNode.TypeArguments && constructorInvocation instanceof ConstructorInvocation) {
			ConstructorInvocation ci = (ConstructorInvocation) constructorInvocation;
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				ci.rawConstructorTypeArguments().addToEnd(arg);
			}
			source.transportLogistics(typeArgs, constructorInvocation);
		}
		
		return true;
	}
	
	public boolean addInnerTypeToConstructorInvocation(Node constructorInvocation, Identifier typeName, Node typeArgs) {
		TypeReferencePart trp = new TypeReferencePart().astIdentifier(typeName);
		if (typeArgs instanceof TemporaryNode.TypeArguments) {
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				trp.rawTypeArguments().addToEnd(arg);
			}
		}
		TypeReference ref = new TypeReference().rawParts().addToEnd(trp);
		if (constructorInvocation instanceof ConstructorInvocation) {
			((ConstructorInvocation) constructorInvocation).rawTypeReference(ref);
		} else {
			DanglingNodes.addDanglingNode(constructorInvocation, ref);
		}
		return true;
	}
	
	public boolean addTypeArgsToMethodInvocation(Node methodInvocation, Node typeArgs) {
		if (typeArgs instanceof TemporaryNode.TypeArguments && methodInvocation instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) methodInvocation;
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				mi.rawMethodTypeArguments().addToEnd(arg);
			}
			source.transportLogistics(typeArgs, methodInvocation);
		}
		
		return true;
	}
	
	public boolean addArgsToConstructorInvocation(Node constructorInvocation, Node args) {
		if (args instanceof TemporaryNode.MethodArguments && constructorInvocation instanceof ConstructorInvocation) {
			ConstructorInvocation ci = (ConstructorInvocation) constructorInvocation;
			for (Node arg : ((TemporaryNode.MethodArguments)args).arguments) {
				ci.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(args, constructorInvocation);
		}
		
		return true;
	}
	
	public boolean addArgsToMethodInvocation(Node methodInvocation, Node args) {
		if (args instanceof TemporaryNode.MethodArguments && methodInvocation instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) methodInvocation;
			for (Node arg : ((TemporaryNode.MethodArguments)args).arguments) {
				mi.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(args, methodInvocation);
		}
		
		return true;
	}
	
	public Node addParens(Node v) {
		if (v instanceof Expression) {
			((Expression)v).astParensPositions().add(new Position(startPos(), currentPos()));
		}
		return v;
	}
	
	public boolean typeIsAlsoLegalAsExpression(Node type) {
		if (!(type instanceof TypeReference)) return true;
		TypeReference tr = (TypeReference)type;
		if (tr.astArrayDimensions() > 0) return false;
		if (tr.isPrimitive() || tr.isVoid()) return false;
		for (Node part : tr.rawParts()) {
			if (part instanceof TypeReferencePart) {
				if (!((TypeReferencePart)part).rawTypeArguments().isEmpty()) return false;
			}
		}
		
		return true;
	}
}
