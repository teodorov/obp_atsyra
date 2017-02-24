package obp.statespace;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class PathAlgos<GraphNode> {
	public interface IFanout<GraphNode> {
		Set<GraphNode> fanout(GraphNode source);
	}

	class Pair<L, R> {
		L left;
		R right;

		Pair(L left, R right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public String toString() {
			return "(" + left.toString() + ", " + right.toString() + ")";
		}
	}

	//this will be usefull when costs are added to the node to remove them from the shortest path
	@Deprecated
	public int shortestPathDijkstra(IFanout<GraphNode> graph, Set<GraphNode> sources, Set<GraphNode> targets,
			Map<GraphNode, Set<GraphNode>> predecessors) {

		Comparator<Pair<Integer, GraphNode>> comparator = (x, y) -> {
			if (x.left < y.left)
				return -1;
			if (x.left == y.left)
				return 0;
			return 1;
		};

		Map<GraphNode, Integer> distances = new HashMap<GraphNode, Integer>();
		Set<GraphNode> known = new HashSet<GraphNode>();
		PriorityQueue<Pair<Integer, GraphNode>> fringe = new PriorityQueue<Pair<Integer, GraphNode>>(comparator);

		for (GraphNode source : sources) {
			fringe.add(new Pair<Integer, GraphNode>(0, source));
			distances.put(source, 0);
		}

		while (!fringe.isEmpty()) {
			Pair<Integer, GraphNode> current = fringe.poll();
			
			if (targets.contains(current.right)) {
				//target found return the path length
				return current.left;
			}
			known.add(current.right);

			for (GraphNode next : graph.fanout(current.right)) {
				//if the graph loops back, ignore the node
				if (known.contains(next)) continue;
				int distance = distances.get(current.right) + 1;

				if (!distances.containsKey(next) || distances.get(next) > distance) {
					distances.put(next, distance);
					addPredecessor(next, current.right, predecessors);
					fringe.add(new Pair<Integer, GraphNode>(distance, next));
				}
			}
		}
		return -1;
	}
	
	class BFSQueue {
		@SuppressWarnings("unchecked")
		LinkedList<GraphNode> fringe[] = (LinkedList<GraphNode>[]) new LinkedList[] {
				new LinkedList<GraphNode>(),
				new LinkedList<GraphNode>()
		};
		
		int currentQueueID = 0;
		int currentDepth = 0;
		
		public boolean atEnd() {
			if (fringe[currentQueueID].isEmpty()) {
				currentQueueID = 1-currentQueueID;
				if (fringe[currentQueueID].isEmpty()) {
					return true;
				}
				currentDepth++;
			}
			return false;
		}
		
		public GraphNode next() {
			return fringe[currentQueueID].remove();
		}
		
		public void schedule(GraphNode conf) {
			fringe[1 - currentQueueID].add(conf);
		}
	}
	
	//This method returns the shortest path between any of the source node and any of the target node
	public int shortestPath(IFanout<GraphNode> graph, Set<GraphNode> sources, Set<GraphNode> targets,
			Map<GraphNode, GraphNode> predecessors) {
		Set<GraphNode> known = Collections.newSetFromMap(new IdentityHashMap<GraphNode, Boolean>());
		
		BFSQueue fringe = new BFSQueue();
		
		for (GraphNode source : sources) {
			fringe.schedule(source);
		}

		while (!fringe.atEnd()) {
			GraphNode current = fringe.next();
			
			if (targets.contains(current)) {
				//target found return the path length
				return fringe.currentDepth - 1;
			}
			
			known.add(current);

			for (GraphNode next : graph.fanout(current)) {
				if (!known.contains(next)) {
					if (predecessors.get(next) == null) predecessors.put(next, current);
					fringe.schedule(next);
				}
			}
		}
		return -1;
	}
	
	public void reachableFrom(IFanout<GraphNode> graph, List<GraphNode> sources,
			Map<GraphNode, Set<GraphNode>> predecessors) {

		Set<GraphNode> known = Collections.newSetFromMap(new IdentityHashMap<GraphNode, Boolean>());
		LinkedList<GraphNode> fringe = new LinkedList<GraphNode>();

		for (GraphNode source : sources) {
			fringe.addLast(source);
		}

		while (!fringe.isEmpty()) {
			GraphNode current = fringe.removeLast();
			known.add(current);

			for (GraphNode next : graph.fanout(current)) {
				addPredecessor(next, current, predecessors);
				if (!known.contains(next)) {
					fringe.addLast(next);
				}
			}
		}
	}

	public void subgraph(IFanout<GraphNode> graph, List<GraphNode> sources, List<GraphNode> targets,
			Map<GraphNode, Set<GraphNode>> successors) {

		Map<GraphNode, Set<GraphNode>> predecessors = new HashMap<GraphNode, Set<GraphNode>>();
		reachableFrom(graph, sources, predecessors);

		IFanout<GraphNode> predGraph = (source) -> {
			Set<GraphNode> fanout = predecessors.get(source);
			return fanout == null ? Collections.emptySet() : fanout;
		};
		
		reachableFrom(predGraph, targets, successors);
	}

	private void addPredecessor(GraphNode node, GraphNode predecessor, Map<GraphNode, Set<GraphNode>> predecessors) {
		Set<GraphNode> predecessorsOfNode = predecessors.get(node);
		if (predecessorsOfNode == null) {
			predecessors.put(node, predecessorsOfNode = Collections.newSetFromMap(new IdentityHashMap<GraphNode, Boolean>()));
		}
		predecessorsOfNode.add(predecessor);
	}
}
