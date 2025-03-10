package midend.Worker;

import midend.Exp.Exp;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.ArrayList;

import static midend.Worker.ExpHelper.*;

public class NodeHelper {
    public static Node node(Node curr, String msg) {
        Node newNode = new NullNode(msg);
        curr.addSucc(newNode);
        return newNode;
    }

    public static Node assume(Node curr, Exp exp) {
        Node newNode = new AssumeNode(exp);
        curr.addSucc(newNode);
        return newNode;
    }

    public static Node let(Node curr, String varName, Exp exp) {
        Node newNode = new LetNode(var(varName), exp);
        curr.addSucc(newNode);
        return newNode;
    }

    public static Exp summary(Node start, Node end) {
        Exp res = bool(true);
        Node curr = start;
        while (curr != end) {
            if (curr instanceof AssumeNode) {
                res = and(res, ((AssumeNode) curr).getCondition());
            }
            curr = curr.getSucc()[0];
        }
        if (end instanceof AssumeNode) {
            res = and(res, ((AssumeNode) end).getCondition());
        }
        return res;
    }
}
