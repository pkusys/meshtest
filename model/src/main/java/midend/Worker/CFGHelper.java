package midend.Worker;

import midend.Exp.Exp;
import midend.Node.AssumeNode;
import midend.Node.Node;

import java.util.ArrayList;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

public class CFGHelper {
    public static void priority_competition(Node entry, ArrayList<Node> nodes) {
        Exp guard = bool(false);
        for (Node n : nodes) {
            assume(entry, not(guard)).addSucc(n);
            if (n instanceof AssumeNode) {
                guard = or(guard, ((AssumeNode) n).getCondition());
            }
        }
    }

    public static void default_route(Node entry, Node exit) {
        Exp guard = bool(false);
        for (Node n: entry.getSucc()) {
            if (n instanceof AssumeNode) {
                guard = or(guard, ((AssumeNode) n).getCondition());
            } else {
                throw new RuntimeException("There is already a default route");
            }
        }
        assume(entry, not(guard)).addSucc(exit);
    }

    public static Node hostGuard(Node curr, String host) {
        Exp target;
        boolean exact;
        if (host.startsWith("*")) {
            target = literalList("md.host", host.substring(2), "\\.", false);
            exact = false;
        } else {
            target = literalList("md.host", host, "\\.", false);
            exact = true;
        }
        return assume(curr, eq(var("md.host"), target, exact));
    }

    public static Node uriGuard(Node curr, String uri, boolean exact) {
        return assume(curr, eq(var("pkt.uri"),
                literalList("pkt.uri", uri.substring(1), "/", true), exact));
    }
}
