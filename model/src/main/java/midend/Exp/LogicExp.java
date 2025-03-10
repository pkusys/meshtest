package midend.Exp;

// and, or
public class LogicExp extends BooleanExp{
    public static enum LogicType {
        AND, OR
    }
    public LogicType type;
    public Exp left;
    public Exp right;

    public LogicExp(LogicType t, Exp left, Exp right) {
        this.type = t;
        this.left = left;
        this.right = right;
    }

    // This constructor is used for one-operand arith exp
    public LogicExp(LogicType t, Exp right) {
        this.type = t;
        this.left = null;
        this.right = right;
    }

    @Override
    public String toString() {
        String op;
        switch(type) {
            case AND:
                op = "/\\\\";
                break;
            case OR:
                op = "\\\\/";
                break;
            default:
                op = "unknown";
                break;
        }
        op = " " + op + " ";
        if (left != null)
            return left.toString() + op + right.toString();
        else
            return op + right.toString();
    }
}