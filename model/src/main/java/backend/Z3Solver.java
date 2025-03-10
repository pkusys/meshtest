package backend;

import com.microsoft.z3.*;
import midend.Exp.*;

import java.math.BigInteger;
import java.util.*;

import static utils.AssertionHelper.Assert;
import static com.microsoft.z3.Status.SATISFIABLE;
import static com.microsoft.z3.Status.UNSATISFIABLE;

public class Z3Solver {
    Solver solver;
    Context ctx;
    public Z3Solver(){
        ctx = new Context();
        solver = ctx.mkSolver();
    }

    public Map<String, BigInteger> pktgen() {
        Map<String, BigInteger> res = new TreeMap<>();

        Assert(solver.check() == SATISFIABLE,
                "Z3Solver: Cannot generate packet on unsatisfiable constraints");

        Model model = solver.getModel();
        FuncDecl[] constDecls = model.getConstDecls();
        for (FuncDecl constDecl : constDecls) {
            BitVecNum value = (BitVecNum) model.evaluate(constDecl.apply(), false);
            res.put(constDecl.getName().toString(), value.getBigInteger());
        }
        return res;
    }

    public ArrayList<String> condgen() {
        Assert(solver.check() == SATISFIABLE,
                "Z3Solver: Cannot generate conditions on unsatisfiable constraints");
        return Arrays.stream(solver.getAssertions()).map(Expr::toString).
                collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    public void push() {
        solver.push();
    }

    public void pop() {
        solver.pop();
    }
    public void addConstraint(Exp constraint){
        Expr boolExpr = encode(constraint);
        Assert(boolExpr.isBool(), "Z3Solver: Invalid constraint encoding");
        solver.add(boolExpr);
    }
    public Boolean isSAT () {
        Status res = solver.check();
        if (res == SATISFIABLE) {
            return true;
        }
        else if (res == UNSATISFIABLE) {
            return false;
        }
        else {
            throw new RuntimeException("Z3Solver: Cannot determine satisfiability");
        }
    }

    public Boolean isConstraintsSAT(List<Exp> constraints){
        solver.push();
        for(Exp exp: constraints){
            if (exp == null){
                throw new RuntimeException("Z3Solver: Null constraint");
            }
            if (!(exp instanceof BooleanExp)){
                throw new RuntimeException("Z3Solver: Invalid constraint type");
            }

            Expr boolExpr = encode(exp);
            Assert(boolExpr.isBool(), "Z3Solver: Invalid constraint encoding");
            solver.add(boolExpr);
        }
        Status res = solver.check();
        solver.pop();
        if (res == SATISFIABLE) {
            return true;
        }
        else if (res == UNSATISFIABLE) {
            return false;
        }
        else {
            throw new RuntimeException("Z3Solver: Cannot determine satisfiability");
        }
    }

    private Expr encode(Exp exp){
        if (exp instanceof NotExp){
            return ctx.mkNot(Objects.requireNonNull(encode(((NotExp) exp).exp)));
        } else if (exp instanceof MatchExp){
            MatchExp matchExp = (MatchExp) exp;
            if (matchExp.lhs instanceof ListExp) {
                Assert(matchExp.rhs instanceof ListExp, "Z3Solver: Inconsistent matchExp type");
                ListExp lhs = (ListExp) matchExp.lhs;
                ListExp rhs = (ListExp) matchExp.rhs;
                Assert(rhs.length instanceof IntLiteralExp,
                        "Z3Solver: rhs in ListExp matching must be concrete");
                Integer length = ((IntLiteralExp) rhs.length).value;
                // EXACT matching
                if (matchExp.type == MatchExp.MatchType.EXACT) {
                    Assert(lhs.elements.size() == rhs.elements.size(),
                            "Z3Solver: Inconsistent element size in ListExp");
                    // each element equals

                    // From Tianshuo:
                    // The code is modified in EXACT matching(here) and PREFIX matching(below)
                    // Before explaining why I did this, I assume that the rhs is concrete
                    // for the 0.yaml:
                    // we obtain the testcase like this:
                    // uri_0: NONE, uri_1: ANY, uri_2: ANY, uri_len: 3
                    // I try to match it with "prefix: /", but it failed
                    // the uri_0: NONE help it to escape the prefix(not equal) matching
                    // the uri_len: 3 help it to escape the exact matching
                    // so in the prefix match section, I remove the lhs[prefixLen] != NONE
                    // the reason is that if there is no other conditions, the lhs[prefixLen] will be resolved as ANY
                    // and in the test, for uri_i = ANY, i < uri_len, we can give it a not NONE value.
                    // but after the modification above, the testcase:
                    // uri_0: NONE, uri_1: a, uri_2: ANY, uri_len: 0
                    // can't match "prefix /"
                    // the uri_len: 0 help it to escape the prefix(not equal) matching
                    // the uri_1: a help it to escape the exact matching
                    // so I modify the exact matching from comparing MAXLEN elements to comparing rhs.length elements
                    //
                    // after these, the prefix matching doesn't contain equal matching
                    // this is right in host prefix match, but not right in uri prefix match
                    // so I modify the uri match in VSGenerator to make it right
                    Expr[] eqs = new Expr[length];
                    for (int i = 0; i < length; i++) {
                        Expr lval = encode(lhs.elements.get(i));
                        Expr rval = encode(rhs.elements.get(i));
                        eqs[i] = ctx.mkEq(lval, rval);
                    }
                    // length equals
                    Expr lenEq = ctx.mkEq(encode(lhs.length), encode(rhs.length));
                    return ctx.mkAnd(lenEq, ctx.mkAnd(eqs));
                }
                // PREFIX matching: rhs is the prefix of lhs
                // we assume that rhs is concrete
                else if (matchExp.type == MatchExp.MatchType.PREFIX) {
                    Assert(rhs.length instanceof IntLiteralExp,
                            "Z3Solver: rhs in PREFIX matching must be concrete");
                    Integer prefixLen = ((IntLiteralExp) rhs.length).value;

                    Expr[] eqs = new Expr[prefixLen];
                    for (int i = 0; i < prefixLen; i++) {
                        Expr lval = encode(lhs.elements.get(i));
                        Expr rval = encode(rhs.elements.get(i));
                        eqs[i] = ctx.mkEq(lval, rval);
                    }
                    // length of lhs is greater than to rhs
                    Expr lenGt = ctx.mkBVSGT(encode(lhs.length), encode(rhs.length));
                    return ctx.mkAnd(lenGt, ctx.mkAnd(eqs));
                }
                else {
                    return ctx.mkTrue();
                }
            } else {
                Assert(!(matchExp.rhs instanceof ListExp), "Z3Solver: Inconsistent matchExp type");
                if (matchExp.type == MatchExp.MatchType.EXACT) {
                    Expr lval = encode(matchExp.lhs);
                    Expr rval = encode(matchExp.rhs);
                    return ctx.mkEq(lval, rval);
                } else {
                    throw new RuntimeException("Z3Solver: Invalid matchExp type");
                }
            }
        } else if (exp instanceof SymbolicIntExp) {
            SymbolicIntExp symInt = (SymbolicIntExp) exp;
            return ctx.mkBVConst(symInt.name, symInt.width);
        } else if (exp instanceof IntLiteralExp) {
            return ctx.mkBV(((IntLiteralExp) exp).value, ((IntLiteralExp) exp).width);
        } else if (exp instanceof ArithExp) {
            ArithExp arith = (ArithExp) exp;
            Expr lval = encode(arith.left);
            Expr rval = encode(arith.right);
            Assert(lval instanceof BitVecExpr, "Z3Solver: Invalid lval type");
            Assert(rval instanceof BitVecExpr, "Z3Solver: Invalid rval type");
            return switch (arith.type) {
                case AND -> ctx.mkBVAND(lval, rval);
                case OR -> ctx.mkBVOR(lval, rval);
                case ADD -> ctx.mkBVAdd(lval, rval);
                case SUB -> ctx.mkBVSub(lval, rval);
                case MUL -> ctx.mkBVMul(lval, rval);
                case DIV -> ctx.mkBVSDiv(lval, rval);
                default -> throw new RuntimeException("Z3Solver: Invalid arith type");
            };
        } else if (exp instanceof CompareExp){
            CompareExp compare = (CompareExp) exp;
            Expr lval = Objects.requireNonNull(encode(compare.left));
            Expr rval = Objects.requireNonNull(encode(compare.right));
            Assert(lval instanceof BitVecExpr, "Z3Solver: Invalid lval type");
            Assert(rval instanceof BitVecExpr, "Z3Solver: Invalid rval type");
            return switch (compare.type) {
                case GE -> ctx.mkBVSGE(lval, rval);
                case GT -> ctx.mkBVSGT(lval, rval);
                case LE -> ctx.mkBVSLE(lval, rval);
                case LT -> ctx.mkBVSLT(lval, rval);
                default -> throw new RuntimeException("Z3Solver: Invalid compare type");
            };
        } else if (exp instanceof LogicExp) {
            LogicExp logic = (LogicExp) exp;
            Expr lval = encode(logic.left);
            Expr rval = encode(logic.right);
            Assert(lval instanceof BoolExpr, "Z3Solver: Invalid lval type");
            Assert(rval instanceof BoolExpr, "Z3Solver: Invalid rval type");
            return switch (logic.type) {
                case AND -> ctx.mkAnd(lval, rval);
                case OR -> ctx.mkOr(lval, rval);
                default -> throw new RuntimeException("Z3Solver: Invalid logic type");
            };
        } else if (exp instanceof TrueExp) {
            return ctx.mkTrue();
        } else if (exp instanceof FalseExp) {
            return ctx.mkFalse();
        } else {
            throw new RuntimeException("Z3Solver: Invalid constraint type");
        }
    }
}
