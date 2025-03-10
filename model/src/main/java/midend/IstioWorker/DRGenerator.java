package midend.IstioWorker;

import midend.Exp.*;
import utils.ExpHelper;
import frontend.Config;
import frontend.istio.DestinationRule;
import frontend.istio.component.Subset;
import midend.Node.AssumeNode;
import midend.Node.Node;
import midend.Node.NullNode;
import utils.StringMap;

import java.util.ArrayList;
import java.util.Map;

import static midend.Exp.LogicExp.LogicType.AND;
import static midend.Exp.LogicExp.LogicType.OR;

public class DRGenerator {

    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        Exp guardCondition = null;
        for (Config config: list) {
            if (config instanceof DestinationRule) {
                DestinationRule dr = (DestinationRule) config;
                String name = dr.metadata.get("name");
                Node drTmpEntry = new NullNode(name + "-Entry");
                Node drTmpExit = new NullNode(name + "-Exit");

                if (guardCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(guardCondition));
                    entry.addSucc(guard);
                    guard.addSucc(drTmpEntry);
                    guardCondition = new LogicExp(OR, guardCondition, generate(dr, drTmpEntry, drTmpExit));
                } else {
                    entry.addSucc(drTmpEntry);
                    guardCondition = generate(dr, drTmpEntry, drTmpExit);
                }

                drTmpExit.addSucc(exit);
            }
        }

        AssumeNode defaultGuard;
        Exp cond = new MatchExp(MatchExp.MatchType.EXACT, new VarExp("md.subset"),
                new IntLiteralExp(StringMap.NONE));
        defaultGuard = guardCondition == null ? new AssumeNode(cond) :
                new AssumeNode(new LogicExp(AND, new NotExp(guardCondition), cond));
        entry.addSucc(defaultGuard);
        defaultGuard.addSucc(exit);
    }

    // for dr, the most important thing is subset
    public static Exp generate(DestinationRule dr, Node entry, Node exit) {
        Node curr = entry;
        ExpHelper helper = new ExpHelper();

        AssumeNode guard = VSGenerator.HostGuard(dr.host, "md.host");
        curr.addSucc(guard);
        curr = guard;

        if (!dr.subsets.isEmpty()) {
            Node subsetExit = new NullNode("Subset-Exit");

            int count = 0;
            for (Subset s: dr.subsets) {
                String name = "Subset-" + count;
                Node subsetEntry = new NullNode(name + "-Entry");
                curr.addSucc(subsetEntry);

                helper.add(subsetGenerate(s, subsetEntry, subsetExit), ExpHelper.OR);
                count++;
            }

            // default route
            AssumeNode defaultGuard = new AssumeNode(new NotExp(helper.getExp()));
            curr.addSucc(defaultGuard);
            defaultGuard.addSucc(subsetExit);
            curr = subsetExit;
        }

        if (dr.workloadSelector != null)
            for (Map.Entry<String, String> e: dr.workloadSelector.labels.entrySet()) {
                curr = VSGenerator.addAction(e.getValue(), "md.srclabel." + e.getKey(),
                        "md.srclabel." + e.getKey(), curr);
                VSGenerator.addLabel("md.srclabel." + e.getKey());
            }

        curr.addSucc(exit);
        return guard.getCondition();
    }

    private static Exp subsetGenerate(Subset s, Node entry, Node exit) {
        Node curr = entry;

        AssumeNode guard = VSGenerator.AssumeStringEq(s.name, "md.subset", "md.subset");
        curr.addSucc(guard);
        curr = guard;

        for (Map.Entry<String, String> e: s.labels.entrySet()) {
            curr = VSGenerator.addAction(e.getValue(), "md.dstlabel." + e.getKey(),
                    "md.dstlabel." + e.getKey(), curr);
            VSGenerator.addLabel("md.dstlabel." + e.getKey());
        }

        curr.addSucc(exit);
        return guard.getCondition();
    }
}
