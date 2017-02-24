package obp.postprocessing.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import obp.statespace.PathAlgos;
import obp.statespace.PathAlgos.IFanout;

public class PathAlgosTests {
	class Edge {
		int from;
		int to;
		Edge(int from, int to) {
			this.from = from;
			this.to = to;
		}
	}
	class MatrixGraph implements IFanout<Integer> {
		int graph[][];
		
		MatrixGraph(int nodes, Edge[] edges) {
			graph = new int[nodes][nodes];
			for (Edge edge : edges) {
				graph[edge.from][edge.to] = 1;
			}
		}
		
		@Override
		public Set<Integer> fanout(Integer source) {
			Set<Integer> fanout = new HashSet<Integer>();
			for (int i = 0; i < graph[source].length; i++) {
				if (graph[source][i] != 0) {
					fanout.add(i);
				}
			}
			return fanout;
		}
	}
	
	@Test
	public void test_ShortesPath() {
		MatrixGraph graph = new MatrixGraph(7, 
				new Edge[] {
						new Edge(0, 1),
						new Edge(0, 3),
						new Edge(1, 2),
						new Edge(1, 5),
						new Edge(2, 6),
						new Edge(3, 2),
						new Edge(3, 4),
						new Edge(5, 6),
						new Edge(1, 1)});
		Map<Integer, Integer> predecessors = new HashMap<Integer, Integer>();
		PathAlgos<Integer> algo = new PathAlgos<Integer>();
		int pathLength = algo.shortestPath(graph, new HashSet<Integer>(Arrays.asList(0)), new HashSet<Integer>(Arrays.asList(6)), predecessors);
		
		assertEquals(3, pathLength);
		
		graph.graph[0][2] = 1;
		predecessors.clear();
		pathLength = algo.shortestPath(graph, new HashSet<Integer>(Arrays.asList(0)), new HashSet<Integer>(Arrays.asList(6)), predecessors);
		assertEquals(2, pathLength);
		assertTrue(predecessors.get(6) == 2);
		assertTrue(predecessors.get(2) == 0);
		
		predecessors.clear();
		pathLength = algo.shortestPath(graph, new HashSet<Integer>(Arrays.asList(0)), new HashSet<Integer>(Arrays.asList(6, 1)), predecessors);
		assertEquals(1, pathLength);
		assertTrue(predecessors.get(1) == 0);
		assertNull(predecessors.get(6));
	}
	
	@Test
	public void test_allPaths() {
		
		MatrixGraph graph = new MatrixGraph(7, 
				new Edge[] {
						new Edge(0, 1),
						new Edge(0, 3),
						new Edge(1, 2),
						new Edge(1, 5),
						new Edge(2, 6),
						new Edge(3, 2),
						new Edge(3, 4),
						new Edge(5, 6),
						new Edge(1, 1)});
		
		Map<Integer, Set<Integer>> successors = new HashMap<Integer, Set<Integer>>();
		PathAlgos<Integer> algo = new PathAlgos<Integer>();
		algo.subgraph(graph, Arrays.asList(0, 3), Arrays.asList(6), successors);
		
		assertFalse(successors.get(3).contains(4));
		
		//reset successors
		successors.clear();
		algo.subgraph(graph, Arrays.asList(0), Arrays.asList(4), successors);
		
		assertTrue(successors.get(0).contains(3));
		assertFalse(successors.get(0).contains(1));
		assertTrue(successors.get(3).contains(4));
		assertFalse(successors.get(3).contains(2));
		
		//reset successors
		successors.clear();
		algo.subgraph(graph, Arrays.asList(1, 3), Arrays.asList(5, 6), successors);
		
		assertNull(successors.get(0));
		assertTrue(successors.get(1).contains(1));
		assertTrue(successors.get(1).contains(2));
		assertTrue(successors.get(1).contains(5));
		assertTrue(successors.get(2).contains(6));
		assertTrue(successors.get(3).contains(2));
		assertFalse(successors.get(3).contains(4));
		
		//reset successors
		successors.clear();
		algo.subgraph(graph, Arrays.asList(2), Arrays.asList(6), successors);
		
		assertNull(successors.get(0));
		assertNull(successors.get(1));
		assertNull(successors.get(3));
		assertNull(successors.get(4));
		assertNull(successors.get(5));
		assertNull(successors.get(6));
		assertTrue(successors.get(2).contains(6));
		assertTrue(successors.size() == 1);
		
		successors.clear();
		algo.subgraph(graph, Arrays.asList(2), Arrays.asList(0), successors);
		
		assertNull(successors.get(0));
		assertNull(successors.get(1));
		assertNull(successors.get(2));
		assertNull(successors.get(3));
		assertNull(successors.get(4));
		assertNull(successors.get(5));
		assertNull(successors.get(6));
		assertTrue(successors.isEmpty());
	}
}
