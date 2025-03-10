package midend.Node;

import java.util.ArrayList;

/**
 * Node in the control flow graph.
 */
public class Node {
    // successors of the node
    public ArrayList<Node> succ = new ArrayList<>();

    // (optional) message to be printed
    public String msg;
    public Integer NodeID;
    private static Integer cnt = 0;
    public static void resetCnt() {
        cnt = 0;
    }

    public Node(String msg) {
        this.msg = msg;
        NodeID = cnt++;
    }

    public void addSucc(Node n) {
        succ.add(n);
    }

    public Node[] getSucc() {
        return succ.toArray(new Node[0]);
    }
}
