package midend.Exp;

public class SymbolicIntExp extends Exp{
    public String name;
    public Integer width;

    public SymbolicIntExp(String name, Integer width) {
        super();
        this.name = name;
        this.width = width;
    }

    @Override
    public String toString() {
        return "sym::" + name;
    }
}
