package lombok.ast;

public interface ExecutableDeclaration extends TypeMember, JavadocContainer {
	Comment astJavadoc();
	ExecutableDeclaration astJavadoc(Comment javadoc);
	Node rawJavadoc();
	ExecutableDeclaration rawJavadoc(Node javadoc);
	Modifiers astModifiers();
	ExecutableDeclaration astModifiers(Modifiers modifiers);
	RawListAccessor<TypeVariable, ? extends ExecutableDeclaration> rawTypeVariables();
	StrictListAccessor<TypeVariable, ? extends ExecutableDeclaration> astTypeVariables();
	RawListAccessor<VariableDefinition, ? extends ExecutableDeclaration> rawParameters();
	StrictListAccessor<VariableDefinition, ? extends ExecutableDeclaration> astParameters();
	RawListAccessor<TypeReference, ? extends ExecutableDeclaration> rawThrownTypeReferences();
	StrictListAccessor<TypeReference, ? extends ExecutableDeclaration> astThrownTypeReferences();
	Node rawBody();
	ExecutableDeclaration rawBody(Node body);
	Block astBody();
	ExecutableDeclaration astBody(Block body);
	TypeDeclaration upUpToTypeDeclaration();
}
