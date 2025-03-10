package dfe;

import java.util.ArrayList;
import java.util.HashMap;

import static utils.AssertionHelper.Assert;

/**
 * Generate the skeleton of service mesh configuration. Explore all possible space
 * instead of random heuristic search.
 * Input:
 * <ul>
 *     <li>The kinds of actual CRDs.</li>
 *     <li>The maximum out degrees of each kind.</li>
 *     <li>The connection between these kinds in format of adjacent matrix, describing
 *     the kinds that can be the predecessor of a specific kind.</li>
 * </ul>
 */
public class Skeleton {
    private final ArrayList<String> kinds;
    private final HashMap<String, Integer> outDegrees;
    private final HashMap<String, ArrayList<String>> connections;

    /**
     * Abstract node in the skeleton, representing an actual resource.
     */
    public static class Node {
        public String kind;
        public ArrayList<Node> pred;
        public ArrayList<Node> succ;

        public Node(String kind) {
            this.kind = kind;
            this.pred = new ArrayList<>();
            this.succ = new ArrayList<>();
        }

        public void addPred(Node node) {
            this.pred.add(node);
        }

        public void addSucc(Node node) {
            this.succ.add(node);
        }
    }
    private final ArrayList<ArrayList<Node>> cases;
    private Node entry;
    private Integer maxCaseNum = 2147483647;
    private Integer maxNodeNum = 8;

    // Algorithm Optimization:
    // New added node's topological distance should be equal or
    // larger than the current node.
    private HashMap<Node, Integer> distances;

    public Skeleton(ArrayList<String> kinds, HashMap<String, Integer> outDegrees,
                    HashMap<String, ArrayList<String>> connections) {
        Assert(kinds.contains("Exit") && connections.containsKey("Exit"),
            "Virtual CRD kind \"Exit\" must be included in kinds and connections.");
        Assert(outDegrees.containsKey("Entry") && outDegrees.containsKey("Exit"),
            "Virtual CRD kind \"Entry\" and \"Exit\" must define max out degrees.");
        this.kinds = kinds;
        this.outDegrees = outDegrees;
        this.connections = connections;
        this.cases = new ArrayList<>();
    }

    public void setMaxCaseNum(Integer maxCaseNum) {
        this.maxCaseNum = maxCaseNum;
    }

    public void setMaxNodeNum(Integer maxNodeNum) {
        this.maxNodeNum = maxNodeNum;
    }

    private void addCase(ArrayList<Node> nodes) {
        System.out.println("Case #" + cases.size() + ": nodes = " + nodes.size());
        ArrayList<Node> newNodes = new ArrayList<>();
        for (Node node : nodes) {
            newNodes.add(new Node(node.kind));
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            for (Node pred : node.pred) {
                newNodes.get(i).addPred(newNodes.get(nodes.indexOf(pred)));
            }
            for (Node succ : node.succ) {
                newNodes.get(i).addSucc(newNodes.get(nodes.indexOf(succ)));
            }
        }
        cases.add(newNodes);
    }

    public ArrayList<ArrayList<Node>> getCases() {
        return cases;
    }

    public void generate() {
        ArrayList<Node> nodes = new ArrayList<>();
        entry = new Node("Entry");
        distances = new HashMap<>();
        nodes.add(entry);
        distances.put(entry, 0);
        dfs(nodes);
        System.out.println("Total cases: " + cases.size());
    }

    private ArrayList<ArrayList<Node>> getPossiblePreds(String kind, ArrayList<Node> nodes) {
        ArrayList<Node> preds = new ArrayList<>();
        for (Node n: nodes) {
            if (n.kind.equals("Entry")) {
                continue;
            }
            // avoid multiple VS delegation
            if (kind.equals("VS") && n.kind.equals("VS") && delegateVs(n)) {
                continue;
            }
            if (connections.get(kind).contains(n.kind) &&
                    n.succ.size() < outDegrees.get(n.kind)) {
                preds.add(n);
            }
        }

        ArrayList<ArrayList<Node>> plans = new ArrayList<>();

        // Exit should connect to all possible predecessors
        if (kind.equals("Exit")) {
            plans.add(preds);
            return plans;
        }

        // Istio based code for delegate
        if (kind.equals("VS")) {
            ArrayList<Node> vsPreds = new ArrayList<>();
            for (Node node: preds) {
                if (node.kind.equals("VS")) {
                    ArrayList<Node> plan = new ArrayList<>();
                    plan.add(node);
                    plans.add(plan);
                    vsPreds.add(node);
                }
            }
            for (Node vsNode: vsPreds) {
                preds.remove(vsNode);
            }
        }

        // For the nodes can be successor of Entry,
        // they must add Entry as their predecessor.
        // So there is special handling for Entry.
        boolean trafficEntry = connections.get(kind).contains("Entry");
        // Since Entry will be added into plan, the bits can start from 0.
        int bits = trafficEntry ? 0 : 1;
        for ( ; bits < (1 << preds.size()); ++ bits) {
            ArrayList<Node> plan = new ArrayList<>();
            if (trafficEntry) {
                plan.add(entry);
            }
            for (int i = 0; i < preds.size(); ++ i) {
                if ((bits & (1 << i)) != 0) {
                    plan.add(preds.get(i));
                }
            }
            plans.add(plan);
        }

        return plans;
    }

    private boolean delegateVs(Node node) {
        for (Node n: node.pred) {
            if (n.kind.equals("VS")) {
                return true;
            }
        }
        return false;
    }

    private int getMaxDistance(ArrayList<Node> nodes) {
        int maxDistance = 0;
        for (Node node: nodes) {
            maxDistance = Math.max(maxDistance, distances.get(node));
        }
        return maxDistance;
    }

    private Boolean isValid(ArrayList<Node> nodes) {
        if (nodes.size() <= 1) {
            return false;
        }

        // Limit on the number of entry nodes
        int entryNum = 0;
        for (Node node: nodes) {
            if (node.succ.isEmpty() && !node.kind.equals("Exit")) {
                return false;
            }
            if (connections.containsKey(node.kind) &&
                    connections.get(node.kind).contains("Entry")) {
                entryNum += 1;
            }
        }

        return entryNum <= 4;
    }

    private void dfs(ArrayList<Node> nodes) {
        if (nodes.get(nodes.size() - 1).kind.equals("Exit")) {
            if (isValid(nodes)) {
                addCase(nodes);
            }
            return;
        }
        if (nodes.size() >= maxNodeNum) {
            return;
        }

        // dfs
        for (String kind: kinds) {
            ArrayList<ArrayList<Node>> plans = getPossiblePreds(kind, nodes);
            int maxDistance = getMaxDistance(nodes);
            for (ArrayList<Node> plan: plans) {
                Node node = new Node(kind);
                int curDistance = 0;
                for (Node pred: plan) {
                    pred.addSucc(node);
                    node.addPred(pred);
                    curDistance = Math.max(curDistance, distances.get(pred) + 1);
                }
                nodes.add(node);
                distances.put(node, curDistance);
                if (cases.size() < maxCaseNum &&
                    curDistance >= maxDistance) {
                    dfs(nodes);
                }
                nodes.remove(node);
                distances.remove(node);
                for (Node pred: plan) {
                    pred.succ.remove(node);
                    node.pred.remove(pred);
                }
            }
        }
    }
}
