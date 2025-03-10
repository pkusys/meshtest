package utils;

import midend.Exp.Exp;
import midend.Exp.FalseExp;
import midend.Exp.LogicExp;

public class ExpHelper {
    public static final int AND = 0;
    public static final int OR = 1;

    private Exp exp;

    public void add(Exp exp, Integer op) {
        if (this.exp == null) {
            this.exp = exp;
        } else {
            switch (op) {
                case AND: this.exp = new LogicExp(LogicExp.LogicType.AND, this.exp, exp); break;
                case OR: this.exp = new LogicExp(LogicExp.LogicType.OR, this.exp, exp); break;
                default: break;     // not support other op
            }
        }
    }

    // this function will automatically reset the helper
    public Exp getExp() {
        if (this.exp == null)
            return new FalseExp();
        Exp result = this.exp;
        this.exp = null;
        return result;
    }
}
