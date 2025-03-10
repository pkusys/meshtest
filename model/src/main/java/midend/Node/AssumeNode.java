package midend.Node;


import midend.Exp.Exp;

public class AssumeNode extends Node{
    public Exp condition;
    public AssumeNode(Exp cond) {
        super("Assume: " + cond.toString());
        this.condition = cond;
    }

    public Exp getCondition() {
        return condition;
    }
}
