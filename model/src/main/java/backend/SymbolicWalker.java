package backend;

import utils.FileHelper;
import utils.PktHelper;
import utils.StringMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import midend.IstioWorker.IstioGenerator;
import midend.Exp.Exp;
import midend.Exp.IntLiteralExp;
import midend.Exp.ListExp;
import midend.Exp.SymbolicIntExp;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static utils.AssertionHelper.Assert;

public class SymbolicWalker {
    private IstioGenerator cfg;
    private SymbolicState state;
    private Stack<Node> nodesInPath;
    private ArrayList<Map<String, BigInteger>> testPackets;
    private ArrayList<ArrayList<String>> testPacketsConditions;
    private ArrayList<HashMap<String, Exp>> referencePackets;
    private ArrayList<ArrayList<String>> standardPackets;
    private ArrayList<String> currPkt;
    private Integer pathCnt;

    public SymbolicWalker(IstioGenerator cfg) {
        this.cfg = cfg;
        state = new SymbolicState();
        nodesInPath = new Stack<>();
        nodesInPath.push(cfg.entryNode);
        testPackets = new ArrayList<>();
        testPacketsConditions = new ArrayList<>();
        referencePackets = new ArrayList<>();
        standardPackets = new ArrayList<>();
        currPkt = new ArrayList<>();
        pathCnt = 0;
    }

    public void collectPath(SymbolicState state) {
        // collect test case
        testPackets.add(state.pktgen());
        testPacketsConditions.add(state.condgen());
        standardPackets.add((ArrayList<String>) currPkt.clone());

        // collect reference
        referencePackets.add(state.snapshot());

        pathCnt++;
    }

    private void dfs(Node node) {
        // exit of the CFG
        if (node == cfg.exitNode) {
            collectPath(state);
            return;
        }

        // LetNode, set value to variable
        if (node instanceof LetNode) {
            state.setVal(((LetNode) node).lval, ((LetNode) node).rval);
            Exp rval = ((LetNode) node).rval;
            if (rval instanceof SymbolicIntExp) {
                currPkt.add(((SymbolicIntExp) rval).name);
            } else if (rval instanceof ListExp) {
                ListExp list = (ListExp) rval;
                for (Exp element: list.elements) {
                    if (element instanceof SymbolicIntExp) {
                        currPkt.add(((SymbolicIntExp) element).name);
                    }
                }
                if (list.length instanceof SymbolicIntExp) {
                    currPkt.add(((SymbolicIntExp) list.length).name);
                }
            }
        }

        // AssumeNode, add constraint
        if (node instanceof AssumeNode) {
            state.addCondition(((AssumeNode) node).condition);
            if (!state.isSAT()) {
                return;
            }
        }

        if (node.succ != null) {
            for (Node succ: node.succ) {
                state.push();
                nodesInPath.push(succ);
                dfs(succ);
                state.pop();
                nodesInPath.pop();
            }
        }
    }

    public void outputNodesInPath() {
        // output all nodes in path to time.log
        // get current time: day-hour-minute-second
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String fileName = now.format(formatter) + ".log";
        try {
            FileWriter fileWriter = new FileWriter(fileName);
            for (Node node: nodesInPath) {
                fileWriter.write(node.msg+"\n");
            }
            fileWriter.close();
            cfg.toDotFileHighlight("path.dot", new ArrayList<>(nodesInPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void outputCase(String path) {
        Assert(this.testPackets.size() == this.testPacketsConditions.size(),
                "SymbolicWalker: testPackets and testPacketsConditions have different size");
        // create path file
        try {
            FileWriter fileWriter = new FileWriter(path);
            // write this.testPackets as json to file with json library
            JsonArray json = new JsonArray();
            Integer idx = 0;
            for (Map<String, BigInteger> pkt : this.testPackets) {
                // Magic
                Integer id = testPacketsConditions.indexOf(testPacketsConditions.get(idx));
                if (id < idx) {
                    idx++;
                    continue;
                }

                ArrayList<String> standardPkt = this.standardPackets.get(idx);
                JsonObject pktJson = new JsonObject();
                for (String key : standardPkt) {
                    if (pkt.containsKey(key)) {
                        pktJson.addProperty(key, decode(Map.entry(key, pkt.get(key))));
                    } else {
                        pktJson.addProperty(key, "ANY");
                    }
                }
                JsonArray conditions = new JsonArray();
                testPacketsConditions.get(idx).forEach(conditions::add);

                JsonObject testMd = new JsonObject();
                testMd.add("conditions", conditions);
                testMd.addProperty("index", id);
                pktJson.add("test_metadata", testMd);
                json.add(pktJson);

                idx ++;
            }
            // pretty print json
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(json, fileWriter);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void outputReference(String path) {
        Assert(this.referencePackets.size() == this.testPackets.size(),
                "SymbolicWalker: referencePackets and testPackets have different size");

        try {
            FileWriter fileWriter = new FileWriter(path);
            JsonArray json = new JsonArray();
            Integer idx = 0;
            for (Map<String, Exp> pkt: this.referencePackets) {
                Map<String, BigInteger> testPkt = this.testPackets.get(idx);
                JsonObject refJson = new JsonObject();
                for (Map.Entry<String, Exp> entry: pkt.entrySet()) {
                    String key = entry.getKey();
                    Exp value = entry.getValue();
                    if (value instanceof ListExp) {
                        ListExp list = (ListExp) value;
                        for (Exp element: list.elements) {
                            Integer localIdx = list.elements.indexOf(element);
                            String localKey = key + "_" + localIdx;
                            if (element instanceof SymbolicIntExp) {
                                SymbolicIntExp symExp = (SymbolicIntExp) element;
                                if (testPkt.containsKey(symExp.name)) {
                                    refJson.addProperty(localKey, decode(Map.entry(localKey, testPkt.get(symExp.name))));
                                } else {
                                    refJson.addProperty(localKey, symExp.name);
                                }
                            } else {
                                Assert (element instanceof IntLiteralExp,
                                        "SymbolicWalker: element in ListExp is not SymbolicIntExp or IntLiteralExp");

                                IntLiteralExp intExp = (IntLiteralExp) element;
                                refJson.addProperty(localKey, decode(Map.entry(localKey, BigInteger.valueOf(intExp.value))));
                            }
                        }

                        // the length of ListExp
                        Exp length = list.length;
                        if (length instanceof SymbolicIntExp) {
                            SymbolicIntExp symExp = (SymbolicIntExp) length;
                            if (testPkt.containsKey(symExp.name)) {
                                refJson.addProperty(key + "_len", decode(Map.entry(symExp.name, testPkt.get(symExp.name))));
                            } else {
                                refJson.addProperty(key + "_len", symExp.name);
                            }
                        } else {
                            Assert (length instanceof IntLiteralExp,
                                    "SymbolicWalker: length in ListExp is not SymbolicIntExp or IntLiteralExp");

                            IntLiteralExp intExp = (IntLiteralExp) length;
                            refJson.addProperty(key + "_len", decode(Map.entry(key + "_len", BigInteger.valueOf(intExp.value))));
                        }
                    } else if (value instanceof SymbolicIntExp) {
                        SymbolicIntExp symExp = (SymbolicIntExp) value;
                        if (testPkt.containsKey(symExp.name)) {
                            refJson.addProperty(key, decode(Map.entry(symExp.name, testPkt.get(symExp.name))));
                        } else {
                            refJson.addProperty(key, symExp.name);
                        }
                    } else {
                        Assert (value instanceof IntLiteralExp,
                                "SymbolicWalker: value in Map is not SymbolicIntExp or IntLiteralExp");

                        IntLiteralExp intExp = (IntLiteralExp) value;
                        refJson.addProperty(key, decode(Map.entry(key, BigInteger.valueOf(intExp.value))));
                    }
                }

                JsonObject testMd = new JsonObject();

                // Magic
                Integer index = testPacketsConditions.indexOf(testPacketsConditions.get(idx));
                testMd.addProperty("index", index);

                refJson.add("test_metadata", testMd);
                json.add(refJson);

                idx++;
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(json, fileWriter);
            fileWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String decode(Map.Entry<String, BigInteger> entry) {
        String key = entry.getKey();
        BigInteger value = entry.getValue();

        if (key.contains("port") || key.endsWith("len")) {
            return value.toString();
        } else if (key.equals("md.type")) {
            switch (value.intValue()) {
                case PktHelper.TypeHTTP:
                    return "HTTP";
                case PktHelper.TypeTLS:
                    return "TLS";
                case PktHelper.TypeTCP:
                    return "TCP";
                case PktHelper.TypeHTTPS:
                    return "HTTPS";
            }
        } else if (key.equals("pkt.http.method")) {
            switch (value.intValue()) {
                case PktHelper.MethodCONNECT:
                    return "CONNECT";
                case PktHelper.MethodDELETE:
                    return "DELETE";
                case PktHelper.MethodGET:
                    return "GET";
                case PktHelper.MethodHEAD:
                    return "HEAD";
                case PktHelper.MethodOPTIONS:
                    return "OPTIONS";
                case PktHelper.MethodPATCH:
                    return "PATCH";
                case PktHelper.MethodPOST:
                    return "POST";
                case PktHelper.MethodPUT:
                    return "PUT";
                case PktHelper.MethodTRACE:
                    return "TRACE";
            }
        } else if (key.equals("pkt.http.type")) {
            switch (value.intValue()) {
                case PktHelper.HTTPRequest:
                    return "request";
                case PktHelper.HTTPResponse:
                    return "response";
            }
        } else if (key.equals("md.proxy")) {
            switch (value.intValue()) {
                case PktHelper.ProxySidecar:
                    return "sidecar";
                case PktHelper.ProxyGateway:
                    return "gateway";
            }
        } else if (key.equals("md.delegate")) {
            switch (value.intValue()) {
                case PktHelper.NoDelegate:
                    return "no_delegate";
                case PktHelper.ToDelegate:
                    return "to_delegate";
                case PktHelper.Delegated:
                    return "delegated";
            }
        } else if (key.equals("pkt.http.header.authority")){
            return StringMap.getStr(key, value.intValue());
        } else if (key.contains("host") || key.contains("authority")) {
            return StringMap.getStr("pkt.host", value.intValue());
        } else if (key.startsWith("pkt.http.uri")) {
            return StringMap.getStr("pkt.http.uri", value.intValue());
        } else if (key.contains("IP")) {
            return value.toString();
        } else {
            return StringMap.getStr(key, value.intValue());
        }

        throw new RuntimeException("can't resolve key " + key + " with value " + value);
    }
    public void generateCases(String input_path, String output_path) {
        dfs(cfg.entryNode);
        String confName = FileHelper.getFileName(input_path);
        confName = confName.substring(0, confName.length() - 5);
        if (confName.startsWith("ml")) {
            confName = confName.substring(3);
        }
        outputCase(output_path + "/case-" + confName + ".json");
        outputReference(output_path + "/ref-" + confName + ".json");
        utils.IO.Info("Total number of paths: " + pathCnt);
    }
}
