package midend.Worker;

import frontend.Config;
import midend.Node.Node;
import utils.CFGGraph;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Generator is the abstract class for all the generators.
 * We provide implementations of it, e.g., IstioGenerator, GatewayAPIGenerator.
 */
public abstract class Generator {
    public ArrayList<Config> resources = new ArrayList<>();
    public Node entryNode;
    public Node exitNode;
    public CFGGraph graph = new CFGGraph();

    public abstract void fromYaml(String inputPath) throws IOException;

    public void addResource(Config config) {
        resources.add(config);
    }

    public abstract void generateCFG();

    public void toDotFile(String path) {
        graph.toDotFile(path);
    }

    public void toDotFileHighlight(String path, ArrayList<Node> nodes) {
        graph.toDotFileHighlight(path, nodes);
    }
}
