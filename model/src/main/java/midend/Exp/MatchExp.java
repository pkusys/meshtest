package midend.Exp;

public class MatchExp extends BooleanExp{
    // REGEX is not a good choice for formal methods.
    public static enum MatchType {
        EXACT, PREFIX, REGEX, // PREFIX means rhs is prefix of lhs
        UNKNOWN     // leave this work for backend
    }
    public MatchType type;

    /*
     * eg.
     * wildcard matching: dst match *.example.com
     * target: dst, matcher: *.example.com, type: PREFIX
     */

    public Exp lhs;
    public Exp rhs;

    public MatchExp(MatchType t, Exp lhs, Exp rhs) {
        this.type = t;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String toString() {
        return "MatchExp(" + type.toString() + "," + lhs.toString() + "," + rhs.toString() + ")";
    }
}
