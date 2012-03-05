package lombok.ast.grammar;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.ast.AstException;
import lombok.ast.Position;

import org.parboiled.Node;
import org.parboiled.buffers.InputBuffer;
import org.parboiled.support.ParseTreeUtils;
import org.parboiled.support.ParsingResult;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class NodeBuilder<T extends lombok.ast.Node> {
	static final List<String> STRUCTURE_LABELS = ImmutableList.of("Ch", "String");
	static final List<String> IGNORABLE_LABELS = ImmutableList.of("testLexBreak", "optWS", "mandatoryWS");
	static Map<String, Class<? extends NodeBuilder<?>>> builders = Maps.newHashMap();
	
	protected T result;
	private InputBuffer buffer;
	
	private Integer startPos, endPos;
	
	List<SourceStructure> sourceStructures = new ArrayList<SourceStructure>();
	
	private void initBuffer(InputBuffer buffer) {
		this.buffer = buffer;
	}
	
	protected NodeBuilder() {
		Class<?> resultType = null;
		
		Type sc = getClass().getGenericSuperclass();
		if (sc instanceof ParameterizedType) {
			Type[] typeArgs = ((ParameterizedType) sc).getActualTypeArguments();
			if (typeArgs != null && typeArgs.length == 1 && typeArgs[0] instanceof Class<?>) {
				resultType = (Class<?>) typeArgs[0];
			}
		}
		
		if (resultType != null && lombok.ast.Node.class.isAssignableFrom(resultType) && !resultType.isInterface()) {
			try {
				@SuppressWarnings("unchecked")
				T newInstance = (T) resultType.newInstance();
				this.result = newInstance;
			} catch (InstantiationException e) {
				// ignore
			} catch (IllegalAccessException e) {
				// ignore
			}
		}
	}
	
	public void visitSourceStructure(Node<?> node) {
		int start = node.getStartIndex();
		int end = node.getEndIndex();
		sourceStructures.add(new SourceStructure(new Position(start, end), buffer.extract(start, end)));
	}
	
	@SneakyThrows({InstantiationException.class, IllegalAccessException.class})
	public static lombok.ast.Node buildFromResult(ParsingResult<?> result) {
		Class<? extends NodeBuilder<?>> subBuilderType = builders.get(result.parseTreeRoot.getLabel());
		
		if (subBuilderType != null) {
			NodeBuilder<?> subBuilder;
			subBuilder = subBuilderType.newInstance();
			subBuilder.initBuffer(result.inputBuffer);
			return subBuilder.build(result.parseTreeRoot);
		}
		
		throw new AstException(null, "Top level node does not have a handler: " + ParseTreeUtils.printNodeTree(result));
	}
	
	public T build(Node<?> pNode) {
		visitUnknownChild("", pNode);
		Position p = new Position(pNode.getStartIndex(), pNode.getEndIndex());
		if (startPos != null) p = p.withStart(startPos);
		if (endPos != null) p = p.withEnd(endPos);
		return result;
	}
	
	protected void start(lombok.ast.Node start) {
		this.startPos = start.getPosition().getStart();
	}
	
	protected void end(lombok.ast.Node end) {
		this.endPos = end.getPosition().getEnd();
	}
	
	protected void start(Node<?> pNode) {
		this.startPos = pNode.getStartIndex();
	}
	
	protected void end(Node<?> pNode) {
		this.endPos = pNode.getEndIndex();
	}
	
	public String extract(Node<?> pNode) {
		return ParseTreeUtils.getNodeText(pNode, buffer);
	}
	
	public void visitHandledChild(String category, lombok.ast.Node builtNode) {
		throw new IllegalStateException("Handled child not handled");
	}
	
	@SneakyThrows({InstantiationException.class, IllegalAccessException.class})
	public void visitUnknownChild(String category, Node<?> pNode) {
		for (Node<?> child : pNode.getChildren()) {
			String label = child.getLabel();
			
			// ignorable children
			if (label == null || IGNORABLE_LABELS.contains(label)) continue;
			
			// atomic children
			if (STRUCTURE_LABELS.contains(label)) {
				visitSourceStructure(child);
				continue;
			}
			
			if (label.startsWith("CAT_")) {
				visitUnknownChild(label.substring(4), child);
				continue;
			}
			
			Class<? extends NodeBuilder<?>> subBuilderType = builders.get(child.getLabel());
			if (subBuilderType != null) {
				NodeBuilder<?> subBuilder;
				subBuilder = subBuilderType.newInstance();
				subBuilder.initBuffer(buffer);
				visitHandledChild(category, subBuilder.build(child));
				continue;
			}
			
			visitUnknownChild(category, child);
		}
	}
	
	public static void registerBuilders(Class<?> parser) {
		for (Method m : parser.getDeclaredMethods()) {
			BuilderType bt = m.getAnnotation(BuilderType.class);
			if (bt == null) continue;
			builders.put(m.getName(), bt.value());
		}
	}
}
