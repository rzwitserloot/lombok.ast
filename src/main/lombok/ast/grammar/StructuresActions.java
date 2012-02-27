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

import lombok.ast.ClassDeclaration;
import lombok.ast.DanglingNodes;
import lombok.ast.EnumConstant;
import lombok.ast.EnumDeclaration;
import lombok.ast.ExecutableDeclaration;
import lombok.ast.InterfaceDeclaration;
import lombok.ast.MethodDeclaration;
import lombok.ast.Modifiers;
import lombok.ast.Node;
import lombok.ast.TypeReference;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;

public class StructuresActions extends SourceActions {
	public StructuresActions(Source source) {
		super(source);
	}
	
	public boolean addTypeVarsToTypeDeclaration(Node typeDeclaration, Node typeVars) {
		if (typeVars instanceof TemporaryNode.OrphanedTypeVariables && typeDeclaration instanceof ClassDeclaration) {
			ClassDeclaration cd = (ClassDeclaration) typeDeclaration;
			for (Node arg : ((TemporaryNode.OrphanedTypeVariables)typeVars).variables) {
				cd.rawTypeVariables().addToEnd(arg);
			}
			source.transportLogistics(typeVars, typeDeclaration);
		}
		if (typeVars instanceof TemporaryNode.OrphanedTypeVariables && typeDeclaration instanceof InterfaceDeclaration) {
			InterfaceDeclaration id = (InterfaceDeclaration) typeDeclaration;
			for (Node arg : ((TemporaryNode.OrphanedTypeVariables)typeVars).variables) {
				id.rawTypeVariables().addToEnd(arg);
			}
			source.transportLogistics(typeVars, typeDeclaration);
		}
		
		return true;
	}
	
	public boolean addTypeVarsToExecutableDeclaration(Node executableDeclaration, Node typeVars) {
		if (typeVars instanceof TemporaryNode.OrphanedTypeVariables && executableDeclaration instanceof ExecutableDeclaration) {
			ExecutableDeclaration ed = (ExecutableDeclaration) executableDeclaration;
			for (Node arg : ((TemporaryNode.OrphanedTypeVariables)typeVars).variables) {
				ed.rawTypeVariables().addToEnd(arg);
			}
			source.transportLogistics(typeVars, executableDeclaration);
		}
		
		return true;
	}
	
	public boolean addExtendsToTypeDeclaration(Node typeDecl, Node extendsClause_) {
		if (!(extendsClause_ instanceof TemporaryNode.ExtendsClause)) {
			DanglingNodes.addDanglingNode(typeDecl, extendsClause_);
			return true;
		}
		
		TemporaryNode.ExtendsClause extendsClause = (TemporaryNode.ExtendsClause) extendsClause_;
		if (typeDecl instanceof ClassDeclaration) {
			if (!extendsClause.superTypes.isEmpty()) {
				((ClassDeclaration) typeDecl).rawExtending(extendsClause.superTypes.get(0));
				for (int i = 1; i < extendsClause.superTypes.size(); i++) {
					DanglingNodes.addDanglingNode(typeDecl, extendsClause.superTypes.get(i));
				}
			}
		} else if (typeDecl instanceof InterfaceDeclaration) {
			for (Node superType : extendsClause.superTypes) {
				((InterfaceDeclaration) typeDecl).rawExtending().addToEnd(superType);
			}
		} else {
			for (Node superType : extendsClause.superTypes) {
				DanglingNodes.addDanglingNode(typeDecl, superType);
			}
		}
		
		source.transportLogistics(extendsClause_, typeDecl);
		
		return true;
	}
	
	public boolean addImplementsToTypeDeclaration(Node typeDecl, Node implementsClause_) {
		if (!(implementsClause_ instanceof TemporaryNode.ImplementsClause)) {
			DanglingNodes.addDanglingNode(typeDecl, implementsClause_);
			return true;
		}
		
		TemporaryNode.ImplementsClause implementsClause = (TemporaryNode.ImplementsClause) implementsClause_;
		if (typeDecl instanceof ClassDeclaration) {
			for (Node superType : implementsClause.superInterfaces) {
				((ClassDeclaration) typeDecl).rawImplementing().addToEnd(superType);
			}
		} else if (typeDecl instanceof EnumDeclaration) {
			for (Node superType : implementsClause.superInterfaces) {
				((EnumDeclaration) typeDecl).rawImplementing().addToEnd(superType);
			}
		} else {
			for (Node superType : implementsClause.superInterfaces) {
				DanglingNodes.addDanglingNode(typeDecl, superType);
			}
		}
		
		source.transportLogistics(implementsClause_, typeDecl);
		
		return true;
	}
	
	public boolean addMethodArgumentsToEnumConstant(Node enumConstant, Node args) {
		if (args instanceof TemporaryNode.MethodArguments && enumConstant instanceof EnumConstant) {
			EnumConstant ec = (EnumConstant) enumConstant;
			for (Node arg : ((TemporaryNode.MethodArguments)args).arguments) {
				ec.rawArguments().addToEnd(arg);
			}
			source.transportLogistics(args, enumConstant);
		} else {
			DanglingNodes.addDanglingNode(enumConstant, args);
		}
		
		return true;
	}
	
	public boolean addParamsToExecutableDeclaration(Node executableDeclaration, Node params) {
		if (params instanceof TemporaryNode.MethodArguments && executableDeclaration instanceof ExecutableDeclaration) {
			ExecutableDeclaration ed = (ExecutableDeclaration) executableDeclaration;
			for (Node arg : ((TemporaryNode.MethodParameters)params).parameters) {
				ed.rawParameters().addToEnd(arg);
			}
			source.transportLogistics(params, executableDeclaration);
		} else {
			DanglingNodes.addDanglingNode(executableDeclaration, params);
		}
		
		return true;
	}
	
	public boolean incrementMethodReturnTypeDimensions() {
		Node top = peek();
		if (top instanceof MethodDeclaration) {
			MethodDeclaration md = (MethodDeclaration) top;
			TypeReference ref = md.astReturnTypeReference();
			if (ref != null) {
				ref.astArrayDimensions(ref.astArrayDimensions() + 1);
			}
		}
		return true;
	}
	
	public boolean incrementVarDefEntryDimensions() {
		Node top = peek();
		if (top instanceof VariableDefinitionEntry) {
			VariableDefinitionEntry vde = (VariableDefinitionEntry) top;
			vde.astArrayDimensions(vde.astArrayDimensions() + 1);
		}
		return true;
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
}
