package conf;

import utils.Rand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static utils.Dump.DumpResources;

public class Connector {
    enum Type {
        IN,
        OUT,
    }

    static class Pair {
        String res;
        Type type;
        Pair(String res, Type type) {
            this.res = res;
            this.type = type;
        }

        @Override
        public String toString() {
            return res + " " + type;
        }
    }

    static class Output {
        Pair pair1;
        Pair pair2;
        int connectType;
        Output(Pair pair1, Pair pair2, int connectType) {
            this.pair1 = pair1;
            this.pair2 = pair2;
            this.connectType = connectType;
        }
    }

    HashMap<String, ArrayList<String>> matrix;
    HashMap<Pair, ArrayList<String>> pairs = new HashMap<>();
    ArrayList<Output> outputs = new ArrayList<>();
    ArrayList<ArrayList<Resource>> results = new ArrayList<>();
    RationalMaker maker = new RationalMaker();

    public Connector(HashMap<String, ArrayList<String>> matrix, HashMap<Pair, ArrayList<String>> pairs) {
        this.matrix = matrix;
        this.pairs = pairs;
        System.out.println("Number of connectors: " + pairs.size());
    }

    public void generate() {
        for (int i = 0; i < pairs.size(); i++) {
            for (int j = i; j < pairs.size(); j++) {
                Pair pair1 = pairs.keySet().toArray(new Pair[0])[i];
                Pair pair2 = pairs.keySet().toArray(new Pair[0])[j];
                if (pair1.type == Type.IN && pair2.type == Type.IN) {
                    outputs.add(new Output(pair1, pair2, 0));
                } else if (pair1.type == Type.OUT && pair2.type == Type.OUT) {
                    outputs.add(new Output(pair1, pair2, 1));
                } else if (pair1.type == Type.IN && pair2.type == Type.OUT) {
                    if (matrix.get(pair2.res).contains(pair1.res)) {
                        outputs.add(new Output(pair1, pair2, 2));
                    }
                } else if (pair1.type == Type.OUT && pair2.type == Type.IN) {
                    if (matrix.get(pair1.res).contains(pair2.res)) {
                        outputs.add(new Output(pair1, pair2, 2));
                    }
                }
            }
        }
        System.out.println("Number of outputs: " + outputs.size());

        int count = 0;
        for (Output output : outputs) {
            ArrayList<String> fields1 = pairs.get(output.pair1);
            ArrayList<String> fields2 = pairs.get(output.pair2);
            ArrayList<String> fields = intersect(fields1, fields2);
            ArrayList<ArrayList<String>> subsets;
            if (output.connectType < 2) {
                subsets = getSubsetNotEmpty(fields);
            } else {
                subsets = new ArrayList<>(List.of(fields));
            }
            count += subsets.size();
            for (ArrayList<String> subset : subsets) {
                String host = maker.getHost();
                Resource res1 = new Resource(switch (output.pair1.res) {
                    case "GW" -> Resource.Kind.GATEWAY;
                    case "SE-G", "SE-S" -> Resource.Kind.SERVICE_ENTRY;
                    case "VS" -> Resource.Kind.VIRTUAL_SERVICE;
                    case "DR" -> Resource.Kind.DESTINATION_RULE;
                    case "SVC" -> Resource.Kind.SERVICE;
                    default -> null;
                }, host);
                Resource res2 = new Resource(switch (output.pair2.res) {
                    case "GW" -> Resource.Kind.GATEWAY;
                    case "SE-G", "SE-S" -> Resource.Kind.SERVICE_ENTRY;
                    case "VS" -> Resource.Kind.VIRTUAL_SERVICE;
                    case "DR" -> Resource.Kind.DESTINATION_RULE;
                    case "SVC" -> Resource.Kind.SERVICE;
                    default -> null;
                }, host);
                if (subset.contains("gateway")) {
                    String gateway = maker.getGateway();
                    res1.gateway = gateway; res2.gateway = gateway;
                }
                if (subset.contains("port")) {
                    int port = maker.getPort();
                    if (output.pair1.type == Type.IN) {
                        res1.frontPort = port;
                    } else {
                        res2.backPort = port;
                    }
                    if (output.pair2.type == Type.IN) {
                        res2.frontPort = port;
                    } else {
                        res1.backPort = port;
                    }
                }
                if (subset.contains("subset")) {
                    String s = "v1";
                    res1.subset = s; res2.subset = s;
                }
                if (subset.contains("label")) {
                    String l = "Alice";
                    res1.label = l; res2.label = l;
                }
                results.add(new ArrayList<>(List.of(res1, res2)));
                if (subset.contains("host")) {
                    results.add(new ArrayList<>(List.of(res1, res2)));
                }
            }
        }
        System.out.println("Number of cases: " + results.size());
        System.out.println("Count = " + count);
    }

    public void buildCase(ArrayList<Resource> resources, String path) {
        RationalGen rationalGen = new RationalGen(new Parameters());
        for (Resource resource : resources) {
            resource.pred.clear(); resource.succ.clear();
            rationalGen.addResource(resource);
        }
//        rationalGen.showAll();
        rationalGen.fill();
        rationalGen.merge();
        rationalGen.sort();
        DumpResources(rationalGen.configs, path);
    }

    private ArrayList<String> intersect(ArrayList<String> fields1, ArrayList<String> fields2) {
        ArrayList<String> fields = new ArrayList<>();
        for (String field : fields1) {
            if (fields2.contains(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private ArrayList<ArrayList<String>> getSubsetNotEmpty(ArrayList<String> fields) {
        ArrayList<ArrayList<String>> subsets = new ArrayList<>();
        for (int i = 1; i < Math.pow(2, fields.size()); i++) {
            ArrayList<String> subset = new ArrayList<>();
            for (int j = 0; j < fields.size(); j++) {
                if ((i & (1 << j)) > 0) {
                    subset.add(fields.get(j));
                }
            }
            if (!subset.isEmpty()) {
                subsets.add(subset);
            }
        }
        return subsets;
    }

    public static void main(String[] args) {
        double start = System.currentTimeMillis();
        Rand.setSeed(0);
        Connector istio = new Connector(new HashMap<String, ArrayList<String>>() {{
            put("SE-G", new ArrayList<String>() {{
                add("VS");
            }});
            put("GW", new ArrayList<String>() {{
                add("VS");
            }});
            put("VS", new ArrayList<String>() {{
                add("VS");
                add("DR");
                add("SE-S");
                add("SVC");
            }});
            put("DR", new ArrayList<String>() {{
                add("SE-S");
                add("SVC");
            }});
            put("SE-S", new ArrayList<String>() {
            });
            put("SVC", new ArrayList<String>() {
            });
        }},
                new HashMap<Pair, ArrayList<String>>() {{
                    put(new Pair("GW", Type.IN), new ArrayList<String>(List.of("host", "port")));
                    put(new Pair("GW", Type.OUT), new ArrayList<String>(List.of("host", "gateway", "port")));
                    put(new Pair("SE-G", Type.IN), new ArrayList<String>(List.of("host", "port")));
                    put(new Pair("SE-G", Type.OUT), new ArrayList<String>(List.of("host", "port")));
                    put(new Pair("VS", Type.IN), new ArrayList<String>(List.of("host", "gateway", "port")));
                    put(new Pair("VS", Type.OUT), new ArrayList<String>(List.of("host", "port", "subset")));
                    put(new Pair("DR", Type.IN), new ArrayList<String>(List.of("host", "subset")));
                    put(new Pair("DR", Type.OUT), new ArrayList<String>(List.of("host", "label")));
                    put(new Pair("SE-S", Type.IN), new ArrayList<String>(List.of("host", "port")));
                    put(new Pair("SE-S", Type.OUT), new ArrayList<String>(List.of("label")));
                    put(new Pair("SVC", Type.IN), new ArrayList<String>(List.of("host", "port")));
                    put(new Pair("SVC", Type.OUT), new ArrayList<String>(List.of("label")));
                }});
        istio.generate();
        for (int i = 0; i < istio.results.size(); i++) {
            istio.buildCase(istio.results.get(i), "testconf/test.yaml");
        }
        System.out.println("Time: " + (System.currentTimeMillis() - start) + "ms");

//        Connector linkerd = new Connector(new HashMap<>() {{
//            put("GW", new ArrayList<>(List.of("HR")));
//            put("HR", new ArrayList<>(List.of("SVC")));
//            put("SVC", new ArrayList<>());
//        }},
//           new HashMap<>() {{
//               put(new Pair("GW", Type.IN), new ArrayList<>(List.of("host", "port")));
//                put(new Pair("GW", Type.OUT), new ArrayList<>(List.of("host", "port", "gateway")));
//                put(new Pair("HR", Type.IN), new ArrayList<>(List.of("host", "port", "gateway")));
//                put(new Pair("HR", Type.OUT), new ArrayList<>(List.of("host", "port")));
//                put(new Pair("SVC", Type.IN), new ArrayList<>(List.of("host", "port")));
//                put(new Pair("SVC", Type.OUT), new ArrayList<>(List.of("label")));
//           }});
//        linkerd.generate();
    }
}
