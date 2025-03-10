package midend.Exp;


public class IntLiteralExp extends Exp{
    public Integer value;
    public Integer width;
    public IntLiteralExp(Integer value) {
        this.value = value;
        this.width = 32;
    }

    public IntLiteralExp(Integer value, Integer width) {
        this.value = value;
        this.width = width;
    }

    @Override
    public String toString() {
        return value.toString()+"w"+width.toString();
    }
}
