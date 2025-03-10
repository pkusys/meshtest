package backend;

import midend.Exp.*;

import java.math.BigInteger;
import java.util.*;

import static utils.AssertionHelper.Assert;

public class SymbolicState {
    static class TimedExp {
        int timestamp;
        Exp exp;

        public TimedExp(int timestamp, Exp exp) {
            this.timestamp = timestamp;
            this.exp = exp;
        }

        @Override
        public String toString() {
            return exp.toString() + "@" + timestamp;
        }
    }

    private int timestamp = 0;
    private HashMap<String, ArrayList<TimedExp>> vars = new HashMap<>();
    private ArrayList<TimedExp> constraints = new ArrayList<>();
    private Z3Solver z3solver = new Z3Solver();

    private void rollback(ArrayList<TimedExp> list) {
        int last = list.size() - 1;
        while (last >= 0 && list.get(last).timestamp > timestamp) {
            list.remove(last);
            last --;
        }
    }

    public void push() {
        timestamp ++;
        z3solver.push();
    }

    public void pop() {
        timestamp --;
        rollback(constraints);
        HashSet<String> toRemove = new HashSet<>();
        for (Map.Entry<String, ArrayList<TimedExp>> entry: vars.entrySet()) {
            // can't remove an entry while iterating
            rollback(entry.getValue());
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key: toRemove) {
            vars.remove(key);
        }
        z3solver.pop();
    }

    public void setVal(Exp name, Exp exp) {
        VarExp var;
        if (name instanceof VarExp) {
            var = (VarExp) name;
        } else {
            throw new RuntimeException("Invalid variable type on LHS of LetNode");
        }

        ArrayList<TimedExp> list = vars.get(var.name);
        if (list == null) {
            list = new ArrayList<>();
            list.add(new TimedExp(timestamp, concretize(exp, vars)));
            vars.put(var.name, list);
        } else {
            list.add(new TimedExp(timestamp, concretize(exp, vars)));
        }
    }

    public void addCondition(Exp exp) {
        if (exp instanceof BooleanExp){
            constraints.add(new TimedExp(timestamp, concretize(exp, vars)));
            z3solver.addConstraint(concretize(exp, vars));
        } else {
            throw new RuntimeException("Invalid condition type on AssumeNode");
        }
    }

    private Exp concretize(Exp exp, HashMap<String, ArrayList<TimedExp>> varValue) {
        if (exp instanceof VarExp) {
            VarExp var = (VarExp) exp;
            if (varValue.containsKey(var.name)) {
                ArrayList<TimedExp> list = varValue.get(var.name);
                return concretize(list.get(list.size() - 1).exp, varValue);
            } else {
                // every variable should be assigned a value (concrete or symbolic)
                System.out.println(var.name);
                throw new RuntimeException("Variable not found");
            }
        }

        else if (exp instanceof NotExp) {
            NotExp not = (NotExp) exp;
            return new NotExp(concretize(not.exp, varValue));
        }

        else if (exp instanceof MatchExp) {
            MatchExp match = (MatchExp) exp;
            return new MatchExp(match.type, concretize(match.lhs, varValue), concretize(match.rhs, varValue));
        }

        else if (exp instanceof ArithExp) {
            ArithExp arith = (ArithExp) exp;
            return new ArithExp(arith.type, concretize(arith.left, varValue), concretize(arith.right, varValue));
        }

        else if (exp instanceof LogicExp) {
            LogicExp logic = (LogicExp) exp;
            return new LogicExp(logic.type, concretize(logic.left, varValue), concretize(logic.right, varValue));
        }

        else if (exp instanceof ListExp) {
            ListExp list = (ListExp) exp;
            ArrayList<Exp> newExp = new ArrayList<>();
            for (Exp e: list.elements) {
                newExp.add(concretize(e, varValue));
            }
            return new ListExp(newExp, concretize(list.length, varValue));
        }

        else if (exp instanceof CompareExp) {
            CompareExp compare = (CompareExp) exp;
            return new CompareExp(compare.type, concretize(compare.left, varValue), concretize(compare.right, varValue));
        }

        else if (exp instanceof DereferenceExp) {
            DereferenceExp deref = (DereferenceExp) exp;
            Exp target = concretize(deref.target, varValue);
            Assert(target instanceof ListExp, "DereferenceExp: Invalid target type");
            ListExp list = (ListExp) target;
            if (Objects.equals(deref.index, DereferenceExp.LENGTH))
                return concretize(list.length, varValue);
            else
                return concretize(list.elements.get(deref.index), varValue);
        }

        else {
            return exp;
        }
    }

    public Boolean isSAT() {
        return z3solver.isSAT();
    }

    public Map<String, BigInteger> pktgen() {
        return z3solver.pktgen();
    }

    public ArrayList<String> condgen() {
        return z3solver.condgen();
    }

    public void debug() {
        System.out.println("vars:");
        for (Map.Entry<String, ArrayList<TimedExp>> entry: vars.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().get(entry.getValue().size() - 1).exp);
        }
        System.out.println("constraints:");
        for (TimedExp exp: constraints) {
            System.out.println(exp.exp);
        }
    }

    public HashMap<String, Exp> snapshot() {
        HashMap<String, Exp> snapshot = new HashMap<>();
        for (Map.Entry<String, ArrayList<TimedExp>> entry: vars.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get(entry.getValue().size() - 1).exp);
        }
        return snapshot;
    }
}