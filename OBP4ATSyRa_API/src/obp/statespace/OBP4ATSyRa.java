package obp.statespace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.xid.basics.Basics;

import obp.cc.ConcreteContext;
import obp.cdl.compiler.CDLCompiler;
import obp.explorer.ConfsFileHandler;
import obp.explorer.runtime.Configuration;
import obp.explorer.runtime.ProgramLoader;
import obp.explorer.runtime.evaluator.Tester;
import obp.explorer.runtime.obs.Action;
import obp.fiacre.compiler.FiacreCompiler;
import obp.fiacre.compiler.ProgramGenerator;
import obp.statespace.PathAlgos.IFanout;
import obp.tool.ToolController;
import obp.util.ConcreteContextUtil;

public class OBP4ATSyRa {
	
	//Fiacre file loading
	FiacreCompilationResults loadFiacre(File fiacreFile) {
		FiacreCompilationResults fiacreCompilationResults = new FiacreCompilationResults();
		//retrieve the program qualified name
		fiacreCompilationResults.programQualifiedName = ProgramGenerator.getProgramQualifiedName(null, getBaseName(fiacreFile));
		//compile fiacre and get the bin path
		FiacreCompiler fcrCompiler = new FiacreCompiler();
		fiacreCompilationResults.binPath = fcrCompiler.compile(fiacreFile);
		
		return fiacreCompilationResults;
 	}
	
	//CDL file loading
	File loadCDL(File cdlFile, String cdlName, File contextDirectory)  {
		CDLCompiler cdlCompiler = new CDLCompiler();
		
		return cdlCompiler.compileCDL(cdlFile, cdlName, contextDirectory);
	}
	
	//concrete context generation from CDL
	String generateConcreteContext()  {return null;}
	
	String javaExecutable() {
		StringBuilder executable = new StringBuilder();
		executable.append(System.getProperty("java.home"));
		executable.append(System.getProperty("file.separator"));
		executable.append("bin");
		executable.append(System.getProperty("file.separator"));
		executable.append("java");
		if ( Basics.isWindows() ) {
			executable.append(".exe");
		}
		return executable.toString();
	}
	
	String heapSize = "1024m";
	String[] vmArgs() {
		return new String[] { 
				"-Xms"+ heapSize, 
				"-Xmx"+ heapSize, 
				"-XX:+UseParallelGC", 
				"-XX:NewRatio=20",
				"-classpath",
				System.getProperty("java.class.path")
			};
	}
	
	String[] explorerConfiguration(File binPath, File ccPath, String programQualifiedName, File stateSpaceDirectory) {
		return new String[] {
			"--program-path", binPath.getAbsolutePath(),
			"--cc", ccPath.getAbsolutePath(),
			"--context-driven", Integer.toString(0),
			"--out", stateSpaceDirectory.getAbsolutePath(),
			programQualifiedName
		};
	}
	
	//model exploration
	void exploreModel(File binPath, File ccPath, String programQualifiedName, File stateSpaceDirectory)  {
		List<String> explorationCommand = new ArrayList<String>();
		
		explorationCommand.add(javaExecutable());
		explorationCommand.addAll(Arrays.asList(vmArgs()));
		explorationCommand.add("obp.explorer.Explorer");
		explorationCommand.addAll(Arrays.asList(explorerConfiguration(binPath, ccPath, programQualifiedName, stateSpaceDirectory)));
		
		new ToolController(new PrintWriter(System.out), new PrintWriter(System.err)) {
			public void runExplorer() {
				schedule(explorationCommand.toArray(new String[explorationCommand.size()]));
				try {
					join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.runExplorer();;
		
		
	}
	
	//after exploration : load the state-space for analysis
	ConfsFileHandler loadStateSpace(String programQualifiedName, File binDirectory, File ccFile, File confsFile) throws Exception {
		final ProgramLoader loader = new ProgramLoader.FromClass(programQualifiedName, binDirectory);
		final ConcreteContext cc = ccFile != null ? ConcreteContextUtil.loadCC(new FileInputStream(ccFile)) : null;
		
		ConfsFileHandler stateSpaceAPI = new ConfsFileHandler(loader, cc, null, confsFile);
		stateSpaceAPI.readAll();
		return stateSpaceAPI;
	}
	
	List<Integer> evaluatePredicate(ConfsFileHandler stateSpaceAPI, String predicate) throws Exception {
		Tester testerPredicate = stateSpaceAPI.createTester(predicate);
		
		List<Integer> predicateTrueList = new ArrayList<Integer>();
		
		for ( int i=0; i<stateSpaceAPI.getConfigurationCount(); i++) {
			Configuration configuration = stateSpaceAPI.getConfiguration(i);
			if ( testerPredicate.test(configuration) ) {
				predicateTrueList.add(configuration.id);
				//System.out.println(predicate + " is true in configuration " + configuration.id);
			}
		}
		return predicateTrueList;
	}
	
	Set<Integer> fanout(ConfsFileHandler stateSpaceAPI, Integer source) {
		return stateSpaceAPI.getActionsFrom(source).stream().map(each -> each.targetId).collect(Collectors.toSet());
	}
	
	//build the shortest trace from source to any of the configurations satisfying the predicate
	List<Integer> shortestTrace(ConfsFileHandler stateSpaceAPI, String predicate) throws Exception {
		List<Integer> targets = evaluatePredicate(stateSpaceAPI, predicate);
		return shortestTrace(stateSpaceAPI, new HashSet<>(targets));
	}
	
	//build the shortest traces from source to a set of configurations satisfying the predicate
	List<Integer>[] shortestTraces(ConfsFileHandler stateSpaceAPI, String predicate) throws Exception {
		List<Integer> targets = evaluatePredicate(stateSpaceAPI, predicate);
		
		List<Integer> traces[] = new List[targets.size()];
		for (int i = 0; i<targets.size(); i++) {
			traces[i] = shortestTrace(stateSpaceAPI, Collections.singleton(targets.get(i)));
		}
		
		return traces;
	}
	
	List<Integer> shortestTrace(ConfsFileHandler stateSpaceAPI, Set<Integer> targets) throws Exception {
		PathAlgos<Integer> searchAlgo = new PathAlgos<Integer>();
		IFanout<Integer> fanout = (Integer source) -> fanout(stateSpaceAPI, source);//stateSpaceAPI.getActionsFrom(source).stream().map(each -> each.targetId).collect(Collectors.toSet());
		
		Map<Integer, Integer> predecessors = new HashMap<Integer, Integer>();
		int traceSize = searchAlgo.shortestPath(fanout, Collections.singleton(0), targets, predecessors);
		
		Integer[] trace = new Integer[traceSize+1];
		
		int foundParent = targets.stream().filter(t-> predecessors.get(t) != null).findFirst().get();
	
		for (int i = trace.length - 1; i>0; i--) {
			trace[i] = foundParent;
			foundParent = predecessors.get(foundParent);
		}
		trace[0] = 0;
		
		return Arrays.asList(trace);
	}
	
	//prints a textual representation of a trace
	void printTrace(ConfsFileHandler stateSpaceAPI, List<Integer> trace) {
		System.out.println("----------------------------------");
		for (int configID : trace) {
			Configuration configuration = stateSpaceAPI.getConfiguration(configID);
			System.out.println(stateSpaceAPI.configurationToString(configuration));
			System.out.println("----------------------------------");
		}
	}
	
	//extract the subgraph between the the precondition to the postcondition
	Map<Integer,Set<Integer>> subgraph(ConfsFileHandler stateSpaceAPI, String precondition, String postcondition) throws Exception {	
		List<Integer> sources = evaluatePredicate(stateSpaceAPI, precondition);
		List<Integer> targets = evaluatePredicate(stateSpaceAPI, precondition);
		
		Map<Integer, Set<Integer>> successors = new HashMap<Integer, Set<Integer>>();
		PathAlgos<Integer> algo = new PathAlgos<Integer>();
		algo.subgraph(source -> fanout(stateSpaceAPI, source), sources, targets, successors);
		
		return successors;
	}
	
	void printTGF(Map<Integer, Set<Integer>> graph, File filename) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		Set<Integer> vertices = new HashSet<>(graph.keySet());
		vertices.addAll(graph.values().stream().flatMap(Set::stream).collect(Collectors.toSet()));
		
		for (int vertex : vertices) {
			writer.write(vertex + "\n");
		}
		writer.write("#\n");
		for (Map.Entry<Integer, Set<Integer>> entry : graph.entrySet()) {
			int source = entry.getKey();
			for (int target : entry.getValue()) {
				writer.write(source + " " + target + "\n");
			}
		}
		writer.close();
	}
	
	public String getBaseName(File fiacreFile) {
		String name = fiacreFile.getName();
		int index = name.lastIndexOf('.');
		if ( index > 0 ) {
			name = name.substring(0, index);
		}
		return name;
	}
	
	public static void main(String args[]) throws Exception, IOException {
		File fiacreFile = new File("example/LamportCorrect_280116.fcr");
		File cdlFile = new File("example/Lamport83Figure5.cdl");
		String cdlName = "lamport_safety";
		File contextDirectory = new File("example/LamportCorrect_280116.obp/ctx/");
		contextDirectory.mkdirs();
		File stateSpaceFile = new File("example/LamportCorrect_280116.obp/confs/state_space.confs");
		stateSpaceFile.getParentFile().mkdirs();
		
		String predicateString = "{sys}1:flags[0] = true and {Bob}1@waiting";
		
		OBP4ATSyRa tool = new OBP4ATSyRa();
		//load fiacre
		FiacreCompilationResults fcrResult = tool.loadFiacre(fiacreFile);
		System.out.println(fcrResult);
		
		//load CDL
		File ccFile = tool.loadCDL(cdlFile, cdlName, contextDirectory);
		System.out.println(ccFile);
		
		tool.exploreModel(fcrResult.binPath, ccFile, fcrResult.programQualifiedName, stateSpaceFile);
		
		ConfsFileHandler stateSpaceAPI = tool.loadStateSpace(fcrResult.programQualifiedName, fcrResult.binPath, ccFile, stateSpaceFile);
		
		List<Integer> resultConfigurations = tool.evaluatePredicate(stateSpaceAPI, predicateString);
		System.out.println("The predicate '" + predicateString + "' is true in the following configurations: " + resultConfigurations);
		
		//get the shortest trace from source to predicate satisfaction
		List<Integer> trace = tool.shortestTrace(stateSpaceAPI, predicateString);
		System.out.println("The shortest trace is : " + trace);
		
		//print the configurations in the trace
		tool.printTrace(stateSpaceAPI, trace);
	
		//get the shortest traces from source to all the configurations satisfying the predicate
		List<Integer> traces[] = tool.shortestTraces(stateSpaceAPI, predicateString);
		System.out.println("The shortest traces are: " + Arrays.toString(traces));
		
		//print the configurations in the trace
		for (int i = 0; i < traces.length; i++) {
			System.out.println("======================================");
			System.out.println("\t\t -> The trace " + i + " is: ");
			tool.printTrace(stateSpaceAPI, traces[i]);
		}
		
		String precondition = "{Bob}1@catInYard";
		String postcondition = "{Alice}1@dogInYard";
		
		Map<Integer, Set<Integer>> subgraph = tool.subgraph(stateSpaceAPI, precondition, postcondition);
		System.out.println(subgraph);
		
		File tgfFile = new File("example/LamportCorrect_280116.tgf");
		tool.printTGF(subgraph, tgfFile);
		
	}
	
	class FiacreCompilationResults {
		File binPath;
		String programQualifiedName;
		
		@Override
		public String toString() {
			return String.format("%s [%s]", binPath.getAbsolutePath(), programQualifiedName);
		}
	}
}
