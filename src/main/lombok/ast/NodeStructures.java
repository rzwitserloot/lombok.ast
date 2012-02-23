package lombok.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

import lombok.ast.grammar.SourceStructure;

/**
 * Lombok.ast node objects can have associated 'structures'. These are the positions of the structural elements that make up a node, such as keywords, parentheses, and dots.
 * The positions of these structures is necessary to convert from lombok.ast parsed ASTs to other ASTs in a way that perfectly emulates position information. It can also be
 * useful for generating the best possible (start, end) pair for error messages.
 */
public class NodeStructures {
	private static WeakHashMap<Node, List<SourceStructure>> store = new WeakHashMap<Node, List<SourceStructure>>();
	
	public static void addSourceStructure(Node on, SourceStructure structure) {
		if (on instanceof AbstractNode) {
			((AbstractNode) on).addSourceStructure(structure);
		} else {
			synchronized (store) {
				List<SourceStructure> list = store.get(on);
				if (list == null) {
					list = new ArrayList<SourceStructure>();
					store.put(on, list);
				}
				list.add(structure);
			}
		}
	}
	
	public static List<SourceStructure> getSourceStructures(Node on) {
		if (on instanceof AbstractNode) {
			return ((AbstractNode) on).getSourceStructures();
		} else {
			synchronized (store) {
				List<SourceStructure> list = store.get(on);
				if (list == null) return Collections.emptyList();
				return Collections.unmodifiableList(list);
			}
		}
	}
	
	public static void removeSourceStructure(Node on, SourceStructure sourceStructure) {
		if (on instanceof AbstractNode) {
			((AbstractNode) on).removeSourceStructure(sourceStructure);
		} else {
			List<SourceStructure> list = store.get(on);
			if (list != null) list.remove(sourceStructure);
		}
	}
}
