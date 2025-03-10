package utils;

import midend.Node.Node;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CFGGraph {
    private HashMap<Node, HashSet<Node>> graph = new HashMap<>();

    public void addNode(Node node) {
        if (!graph.containsKey(node)) {
            graph.put(node, new HashSet<>());
            if (node.succ != null) {
                graph.get(node).addAll(node.succ);
            }
            for (Node n : node.succ) {
                addNode(n);
            }
        }
    }

    public void toDotFile(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        HashMap<Node, String> node2Id = new HashMap<>();
        int id = 0;
        for (Node node : graph.keySet()) {
            node2Id.put(node, "node_" + id++);
        }
        for (Node node : graph.keySet()) {
            sb.append(node2Id.get(node) +
                    " [shape=box," + "label=\"" + "ID: " + node.NodeID + "\n" + node.msg + "\"];\n");
        }
        for (Node node : graph.keySet()) {
            for (Node succ : graph.get(node)) {
                sb.append(node2Id.get(node) + " -> " + node2Id.get(succ) + ";\n");
            }
        }
        sb.append("}\n");
        // write to path
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));
            writer.append(sb);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void toDotFileHighlight(String path, ArrayList<Node> nodes) {
        HashSet<Node> highlight = new HashSet<>(nodes);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG {\n");
        HashMap<Node, String> node2Id = new HashMap<>();
        int id = 0;
        for (Node node : graph.keySet()) {
            node2Id.put(node, "node_" + id++);
        }
        for (Node node : graph.keySet()) {
            if (highlight.contains(node)) {
                sb.append(node2Id.get(node) +
                        " [shape=box, style=filled, color=gold, " +
                        "label=\"" + "ID: " + node.NodeID + "\n" + node.msg + "\"];\n");
            } else {
                sb.append(node2Id.get(node) +
                        " [shape=box, label=\"" + "ID: " + node.NodeID + "\n" + node.msg + "\"];\n");
            }
        }
        for (Node node : graph.keySet()) {
            for (Node succ : graph.get(node)) {
                if (highlight.contains(node) && highlight.contains(succ))
                    sb.append(node2Id.get(node) + " -> " + node2Id.get(succ) + " [color=gold];\n");
                else
                    sb.append(node2Id.get(node) + " -> " + node2Id.get(succ) + ";\n");
            }
        }
        sb.append("}\n");
        // write to path
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));
            writer.append(sb);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
