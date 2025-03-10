package midend.Exp;

public class DereferenceExp extends Exp {
    public static final Integer LENGTH = 65536;
    public Exp target;
    public Integer index;

    public DereferenceExp(Exp target, Integer index) {
        this.target = target;
        this.index = index;
    }

    @Override
    public String toString() {
        return "Deref(" + target.toString() + ", " + index.toString() + ")";
    }
}
