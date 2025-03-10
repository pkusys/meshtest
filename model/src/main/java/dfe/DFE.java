package dfe;

import utils.Dump;
import utils.Rand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Main callee of DFE, executing the whole process.
 */
public class DFE {
    public final Skeleton skeleton;

    public DFE(ArrayList<String> kinds, HashMap<String, Integer> outDegrees,
                    HashMap<String, ArrayList<String>> connections) {
        this.skeleton = new Skeleton(kinds, outDegrees, connections);
        // Seed must be set here, for the Util needs it as well.
        Rand.setSeed(System.currentTimeMillis());
    }

    private boolean isReasonable(ArrayList<Skeleton.Node> nodes) {
        return nodes.size() > 1;
    }

    private void generateCase(ArrayList<Skeleton.Node> nodes, String casePath) {
        // Select the reasonable and valuable cases
        if (!isReasonable(nodes)) {
            return;
        }

        Util util = new Util();
        Connector connector = new Connector(nodes, util);
        connector.connect();

        Filler filler = new Filler(nodes, connector.getConfigs(), util);
        filler.fill();

        Dump.DumpResources(filler.sort(), casePath);
    }

    public void generate(String caseDir, int caseNum) {
        skeleton.generate();
        ArrayList<ArrayList<Skeleton.Node>> cases = skeleton.getCases();
        int margin = cases.size() / caseNum;
        for (int i = 0; i < caseNum; ++ i) {
            int index = i * margin + Rand.RandInt(margin);
            generateCase(cases.get(index), caseDir + "/" + index + ".yaml");
        }
    }

    public static void main(String[] args) {
        DFE dfe = new DFE(
                new ArrayList<>() {{
                    add("GW");
                    add("SE-G");
                    add("VS");
                    add("DR");
                    add("SE-S");
                    add("SVC");
                    add("Exit");
                }},
                new HashMap<>() {{
                    put("Entry", 5);
                    put("GW", 2);
                    put("SE-G", 2);
                    put("VS", 2);
                    put("DR", 1);
                    put("SE-S", 1);
                    put("SVC", 1);
                    put("Exit", 0);
                }},
                new HashMap<>() {{
                    put("GW", new ArrayList<>(List.of("Entry")));
                    put("SE-G", new ArrayList<>(List.of("Entry")));
                    put("VS", new ArrayList<>(List.of("GW", "SE-G", "VS")));
                    put("DR", new ArrayList<>(List.of("VS")));
                    put("SE-S", new ArrayList<>(List.of("Entry", "VS", "DR")));
                    put("SVC", new ArrayList<>(List.of("Entry", "VS", "DR")));
                    put("Exit", new ArrayList<>(List.of("SVC", "SE-S")));
                }}
        );
//        DFE dfe = new DFE(
//                new ArrayList<>() {{
//                    add("GW");
//                    add("HR");
//                    add("SVC");
//                    add("Exit");
//                }},
//                new HashMap<>() {{
//                    put("Entry", 5);
//                    put("GW", 2);
//                    put("HR", 2);
//                    put("SVC", 1);
//                    put("Exit", 0);
//                }}
//                new HashMap<>() {{
//                    put("GW", new ArrayList<>(List.of("Entry")));
//                    put("HR", new ArrayList<>(List.of("Entry", "GW")));
//                    put("SVC", new ArrayList<>(List.of("HR")));
//                    put("Exit", new ArrayList<>(List.of("SVC", "SE-S")));
//                }}
//        );
        dfe.skeleton.setMaxNodeNum(9);
        dfe.generate("testconf", 1);
    }
}
