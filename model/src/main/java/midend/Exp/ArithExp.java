package midend.Exp;

// and, or, +, -, *, /
public class ArithExp extends Exp{
    public static enum ArithType {
        AND, OR, ADD, SUB, MUL, DIV
    }
    public ArithType type;
    public Exp left;
    public Exp right;

    public ArithExp(ArithType t, Exp left, Exp right) {
        this.type = t;
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        String op;
        switch(type) {
            case AND:
                op = "&";
                break;
            case OR:
                op = "|";
                break;
            case ADD:
                op = "+";
                break;
            case SUB:
                op = "-";
                break;
            case MUL:
                op = "*";
                break;
            case DIV:
                op = "/";
                break;
            default:
                op = "unknown";
                break;
        }
        op = " " + op + " ";
        return left.toString() + op + right.toString();
    }
}
