package midend.Exp;

// and, or, +, -, *, /
public class CompareExp extends BooleanExp{

    // Note: EQ and NE is implemented by MatchExp and NotExp
    public static enum CompareType {
        GE, GT, LE, LT
    }
    public CompareType type;
    public Exp left;
    public Exp right;

    public CompareExp(CompareType t, Exp left, Exp right) {
        this.type = t;
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        String op;
        switch(type) {
            case GE:
                op = ">=";
                break;
            case GT:
                op = ">";
                break;
            case LE:
                op = "<=";
                break;
            case LT:
                op = "<";
                break;
            default:
                op = "unknown";
                break;
        }
        op = " " + op + " ";
        return left.toString() + op + right.toString();
    }
}
