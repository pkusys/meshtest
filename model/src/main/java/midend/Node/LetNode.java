package midend.Node;


import midend.Exp.Exp;
import midend.Exp.VarExp;

public class LetNode extends Node{
    public Exp lval;
    public Exp rval;
    public LetNode(Exp l, Exp r) {
        super("Let: " + l.toString() + " = " + r.toString());
        if (l == null || r == null) {
            throw new IllegalArgumentException("LetNode: null argument");
        }
        if (!(l instanceof VarExp)) {
            throw new IllegalArgumentException("LetNode: lval is not a variable");
        }
        this.lval = l;
        this.rval = r;
    }
}
