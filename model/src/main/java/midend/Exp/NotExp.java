package midend.Exp;

import static utils.AssertionHelper.Assert;

public class NotExp extends BooleanExp{
    public Exp exp;

    public NotExp(Exp exp) {
        Assert(exp != null, "NotExp: exp cannot be null");
        this.exp = exp;
    }

    @Override
    public String toString() {
        return "NotExp(" + exp.toString() + ")";
    }
}
