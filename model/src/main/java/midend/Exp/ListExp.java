package midend.Exp;

import java.util.ArrayList;

import static utils.AssertionHelper.Assert;
import static utils.StringMap.NONE;

public class ListExp extends Exp{
    public ArrayList<Exp> elements;
    public Exp length;
    public static Integer MAXLEN = 10;

    public ListExp(ArrayList<Exp> ele, Exp length) {
        Integer iptEleSize = ele.size();
        Assert(iptEleSize <= MAXLEN, "ListExp: Too many elements");
        this.elements = new ArrayList<>();
        for (int i = 0; i < iptEleSize; i++) {
            this.elements.add(ele.get(i));
        }
        for (int i = 0; i < MAXLEN - iptEleSize; i++) {
            this.elements.add(new IntLiteralExp(NONE));
        }
        this.length = length;
    }



    @Override
    public String toString() {
        return "ListExp(" + elements.toString() + ")";
    }
}
