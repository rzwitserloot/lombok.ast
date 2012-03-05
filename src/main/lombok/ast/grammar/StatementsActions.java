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
import lombok.ast.DanglingNodes;
import lombok.ast.For;
import lombok.ast.LabelledStatement;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.SuperConstructorInvocation;
import lombok.ast.VariableDefinition;

public class StatementsActions extends SourceActions {
	public StatementsActions(Source source) {
		super(source);
	}
	
	public boolean setModifiersOnVarDef(Modifiers modifiers, Node varDef) {
		if (varDef instanceof VariableDefinition) {
			((VariableDefinition) varDef).astModifiers(modifiers);
			varDef.setPosition(varDef.getPosition().withStart(modifiers.getPosition().getStart()));
		} else {
			DanglingNodes.addDanglingNode(varDef, modifiers);
		}
		return true;
	}
	
	public boolean addTypeArgsToAlternateConstructorInvocation(Node aci, Node typeArgs) {
		if (typeArgs instanceof TemporaryNode.TypeArguments && aci instanceof AlternateConstructorInvocation) {
			AlternateConstructorInvocation inv = (AlternateConstructorInvocation) aci;
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				inv.rawConstructorTypeArguments().addToEnd(arg);
			}
			source.transportLogistics(typeArgs, aci);
		}
		
		return true;
	}
	
	public boolean addArgsToAlternateConstructorInvocation(Node aci, Node args) {
		if (args instanceof TemporaryNode.MethodArguments && aci instanceof AlternateConstructorInvocation) {
			AlternateConstructorInvocation inv = (AlternateConstructorInvocation) aci;
			for (Node arg : ((TemporaryNode.MethodArguments)args).arguments) {
				inv.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(args, aci);
		}
		
		return true;
	}
	
	public boolean addTypeArgsToSuperConstructorInvocation(Node sci, Node typeArgs) {
		if (typeArgs instanceof TemporaryNode.TypeArguments && sci instanceof SuperConstructorInvocation) {
			SuperConstructorInvocation inv = (SuperConstructorInvocation) sci;
			for (Node arg : ((TemporaryNode.TypeArguments)typeArgs).arguments) {
				inv.rawConstructorTypeArguments().addToEnd(arg);
			}
			source.transportLogistics(typeArgs, sci);
		}
		
		return true;
	}
	
	public boolean addArgsToSuperConstructorInvocation(Node sci, Node args) {
		if (args instanceof TemporaryNode.MethodArguments && sci instanceof SuperConstructorInvocation) {
			SuperConstructorInvocation inv = (SuperConstructorInvocation) sci;
			for (Node arg : ((TemporaryNode.MethodArguments)args).arguments) {
				inv.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(args, sci);
		}
		
		return true;
	}
	
	public boolean addStatementExpressionsToForInit(Node forNode, Node sel) {
		if (sel instanceof TemporaryNode.StatementExpressionList && forNode instanceof For) {
			For f = (For) forNode;
			for (Node init : ((TemporaryNode.StatementExpressionList)sel).expressions) {
				f.rawExpressionInits().addToEnd(init);
			}
			source.transportLogistics(sel, forNode);
		}
		
		return true;
	}
	
	public boolean addStatementExpressionsToForUpdate(Node forNode, Node sel) {
		if (sel instanceof TemporaryNode.StatementExpressionList && forNode instanceof For) {
			For f = (For) forNode;
			for (Node init : ((TemporaryNode.StatementExpressionList)sel).expressions) {
				f.rawUpdates().addToEnd(init);
			}
			source.transportLogistics(sel, forNode);
		}
		
		return true;
	}
	
	public boolean buildLabelStack() {
		Node statement = pop();
		Node label = pop();
		while (!(label instanceof TemporaryNode.SentinelNode)) {
			if (label instanceof LabelledStatement) {
				((LabelledStatement) label).rawStatement(statement);
				label.setPosition(label.getPosition().withEnd(statement.getPosition().getEnd()));
			} else {
				DanglingNodes.addDanglingNode(label, statement);
			}
			statement = label;
			label = pop();
		}
		push(statement);
		return true;
	}
}
