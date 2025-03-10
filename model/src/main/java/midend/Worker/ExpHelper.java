package midend.Worker;

import midend.Exp.*;
import utils.StringMap;

import java.util.ArrayList;
import static utils.AssertionHelper.Assert;

public class ExpHelper {
    public static Exp sym(String symName) {
        return new SymbolicIntExp(symName, 32);
    }

    public static Exp symList(String symName) {
        Exp len = new SymbolicIntExp(symName + "_len", 32);
        ArrayList<Exp> elements = new ArrayList<>();
        for (int i = 0; i < ListExp.MAXLEN; ++ i) {
            elements.add(new SymbolicIntExp(symName + "_element_" + i, 32));
        }
        return new ListExp(elements, len);
    }

    public static Exp literal(int value) {
        return new IntLiteralExp(value);
    }

    public static Exp literal(String field, String str) {
        return new IntLiteralExp(StringMap.get(field, str));
    }

    public static Exp literalList(String field, String str, String delim, boolean order) {
        String[] parts = str.split(delim);
        Exp len = new IntLiteralExp(parts.length);
        ArrayList<Exp> elements = new ArrayList<>();
        if (order) {
            for (String part : parts) {
                elements.add(new IntLiteralExp(StringMap.get(field, part)));
            }
        } else {
            for (int i = 0; i < parts.length; ++ i) {
                elements.add(new IntLiteralExp(StringMap.get(field, parts[parts.length - i - 1])));
            }
        }
        return new ListExp(elements, len);
    }

    public static Exp literalList(String field, String str, String delim) {
        return literalList(field, str, delim, true);
    }

    public static Exp var(String varName) {
        return new VarExp(varName);
    }

    public static Exp or(Exp left, Exp right) {
        Assert(left instanceof BooleanExp && right instanceof BooleanExp, "or: left and right should be boolean expressions");
        return new LogicExp(LogicExp.LogicType.OR, left, right);
    }

    public static Exp and(Exp left, Exp right) {
        Assert(left instanceof BooleanExp && right instanceof BooleanExp, "and: left and right should be boolean expressions");
        return new LogicExp(LogicExp.LogicType.AND, left, right);
    }

    public static Exp not(Exp exp) {
        Assert(exp instanceof BooleanExp, "not: exp should be a boolean expression");
        return new NotExp(exp);
    }

    public static Exp eq(Exp left, Exp right, boolean exact) {
        if (exact) {
            return new MatchExp(MatchExp.MatchType.EXACT, left, right);
        } else {
            return new MatchExp(MatchExp.MatchType.PREFIX, left, right);
        }
    }

    public static Exp bool(boolean value) {
        if (value) {
            return new TrueExp();
        } else {
            return new FalseExp();
        }
    }
}
