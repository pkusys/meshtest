package midend.IstioWorker;

import frontend.Config;
import frontend.istio.component.DomainHost;
import frontend.istio.*;
import midend.Worker.Generator;
import utils.*;
import midend.Exp.*;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

import static utils.ExpHelper.OR;

public class IstioGenerator extends Generator {

    public void fromYaml(String inputPath) throws IOException {
        Yaml yaml = new Yaml();

        InputStream fis = new FileInputStream(inputPath);
        Iterable<Object> yamlConf = yaml.loadAll(fis);

        for(Object obj : yamlConf) {
            assert obj instanceof Map;
            Map<String, Object> map = (Map<String, Object>) obj;
            String kind = (String) map.get("kind");
            switch (kind) {
                case "VirtualService":
                    VirtualService vs = new VirtualService();
                    Config.isResource = true;
                    vs.fromYaml(map);
                    this.addResource(vs);
                    break;
                case "DestinationRule":
                    DestinationRule dr = new DestinationRule();
                    Config.isResource = true;
                    dr.fromYaml(map);
                    this.addResource(dr);
                    break;
                case "Gateway":
                    Gateway gw = new Gateway();
                    Config.isResource = true;
                    gw.fromYaml(map);
                    this.addResource(gw);
                    break;
                case "Service":
                    Service svc = new Service();
                    Config.isResource = true;
                    svc.fromYaml(map);
                    this.addResource(svc);
                    break;
                case "ServiceEntry":
                    ServiceEntry se = new ServiceEntry();
                    Config.isResource = true;
                    se.fromYaml(map);
                    this.addResource(se);
                    break;
                case "Pod":
                    Pod pod = new Pod();
                    Config.isResource = true;
                    pod.fromYaml(map);
                    this.addResource(pod);
                    break;
                default: break;
            }
        }
        fis.close();
    }

    private void initCFG(){
        entryNode = new NullNode("CFG-Entry");
        exitNode = new NullNode("CFG-Exit");
        Node curr = entryNode;

        Node vsEntry = new NullNode("VS-Entry");
        Node vsExit = new NullNode("VS-Exit");
        VSGenerator.generateAll(resources, vsEntry, vsExit);

        Node drEntry = new NullNode("DR-Entry");
        Node drExit = new NullNode("DR-Exit");
        DRGenerator.generateAll(resources, drEntry, drExit);

        Node seEntry = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxySidecar)));
        Node seExit = new NullNode("SE-Exit");
        SEGenerator.generateAll(resources, seEntry, seExit, Role.GATE);

        AssumeNode svcHosts = VSGenerator.HostGuard(new DomainHost("*.default.svc.cluster.local"), "md.host");

        // port 80 is default for HTTP
        AssumeNode typeHTTP = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTP)));
        AssumeNode port80 = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.port"), new IntLiteralExp(80)));

        // Used for testing easily
//        seEntry.addSucc(svcHosts);
//        svcHosts.addSucc(seExit);
        seEntry.addSucc(typeHTTP);
        typeHTTP.addSucc(port80);
        port80.addSucc(seExit);

        Node gwEntry = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxyGateway)));
        Node gwExit = new NullNode("GW-Exit");
        GWGenerator.generateAll(resources, gwEntry, gwExit);

        Node svcEntry = new NullNode("Svc-Entry");
        Node svcExit = new NullNode("Svc-Exit");
        SvcGenerator.generateAll(resources, svcEntry, svcExit);

        Node mdInitExit = new NullNode("MDInit-Exit");
        initMd(curr, mdInitExit);
        curr = mdInitExit;

        Node typeDispatchExit = new NullNode("TypeDispatch-Exit");
        initHTTP(curr, typeDispatchExit);
        initTLS(curr, typeDispatchExit);
        initTCP(curr, typeDispatchExit);
        curr = typeDispatchExit;

        curr.addSucc(gwEntry);
        curr.addSucc(seEntry);

        gwExit.addSucc(vsEntry);
        seExit.addSucc(vsEntry);
        
        curr = vsExit;

        AssumeNode delegate = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.delegate"), new IntLiteralExp(PktHelper.ToDelegate)));
        AssumeNode notDelegate = new AssumeNode(new NotExp(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.delegate"), new IntLiteralExp(PktHelper.ToDelegate))));
        LetNode setDelegated = new LetNode(new VarExp("md.delegate"),
                new IntLiteralExp(PktHelper.Delegated));

        curr.addSucc(delegate);
        curr.addSucc(notDelegate);
        delegate.addSucc(setDelegated);
        setDelegated.addSucc(vsEntry);
        curr = notDelegate;

        curr.addSucc(drEntry);
        curr = drExit;

        curr.addSucc(svcEntry);
        curr = svcExit;

        curr.addSucc(exitNode);

        // remove the static variables in the future
        VSGenerator.fieldSet.clear();
        VSGenerator.dstLabels.clear();
    }

    // initialize important metadata.
    private void initMd(Node entry, Node exit) {
        Node curr = entry;

        curr = addInitSymbolic("md.type", "md.type", curr);
        curr = addVarEnumerate(PktHelper.TypeList, "md.type", curr);

        curr = addInitSymbolic("md.proxy", "md.proxy", curr);
        curr = addVarEnumerate(PktHelper.ProxyList, "md.proxy", curr);
        NullNode proxyExit = new NullNode("Proxy-Exit");
        // proxy sidecar
        AssumeNode assumeSidecar = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxySidecar)));
        curr.addSucc(assumeSidecar);
        assumeSidecar.addSucc(proxyExit);
        // proxy gateway
        AssumeNode assumeGateway = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxyGateway)));
        curr.addSucc(assumeGateway);
        assumeGateway.addSucc(proxyExit);
        curr = proxyExit;

        curr = addInitSymbolic("md.srcns", "md.srcns", curr);

        curr = addInitSymbolic("md.gateway", "md.gateway", curr);

        LetNode setDelegate = new LetNode(new VarExp("md.delegate"), new IntLiteralExp(PktHelper.NoDelegate));
        curr.addSucc(setDelegate);
        curr = setDelegate;

        LetNode setSubset = new LetNode(new VarExp("md.subset"), new IntLiteralExp(StringMap.NONE));
        curr.addSucc(setSubset);
        curr = setSubset;

        curr = let(curr, "md.weight", literal(100));

        for (String varName: VSGenerator.fieldSet) {
            if (varName.startsWith("md"))
                curr = addInitSymbolic(varName, varName, curr);
        }

        for (String label: VSGenerator.dstLabels) {
            LetNode setLabel = new LetNode(new VarExp(label), new IntLiteralExp(StringMap.NONE));
            curr.addSucc(setLabel);
            curr = setLabel;
        }

        curr.addSucc(exit);
    }


    /*
    * Add HTTP parser into the CFG
    * 1. Add symbolic variables
    * 2. In the CFG, set relevant metadata (eg. md.host)
    * */
    private void initHTTP(Node entry, Node exit) {
        Node curr = entry;

        Node guardExit = new NullNode("Guard-Exit");
        AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTP)));
        curr.addSucc(guard);
        guard.addSucc(guardExit);
        guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTPS)));
        curr.addSucc(guard);
        guard.addSucc(guardExit);
        curr = guardExit;

        curr = addInitSymbolicList("pkt.host", "pkt.host", curr);
        curr = addInitSymbolic("pkt.port", "pkt.port", curr);
        curr = addInitSymbolic("pkt.dstIP", "pkt.dstIP", curr);
        curr = addInitSymbolic("pkt.http.type", "pkt.http.type", curr);
        curr = addVarEnumerate(PktHelper.HTTPTypeList, "pkt.http.type", curr);
        curr = addInitSymbolicList("pkt.http.uri", "pkt.http.uri", curr);
        curr = addInitSymbolic("pkt.http.scheme", "pkt.http.scheme", curr);
        curr = addInitSymbolic("pkt.http.method", "pkt.http.method", curr);
        curr = addVarEnumerate(PktHelper.MethodList, "pkt.http.method", curr);

        AssumeNode assumeType = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT, new VarExp("pkt.http.type"),
                new IntLiteralExp(PktHelper.HTTPRequest)));
        curr.addSucc(assumeType);
        curr = assumeType;

        for (String varName: VSGenerator.fieldSet) {
            if (varName.startsWith("pkt.http")) {
                curr = addInitSymbolic(varName, varName, curr);
            }
        }

        LetNode setMdHost = new LetNode(new VarExp("md.host"), new VarExp("pkt.host"));
        curr.addSucc(setMdHost);
        curr = setMdHost;

        LetNode setMdPort = new LetNode(new VarExp("md.port"), new VarExp("pkt.port"));
        curr.addSucc(setMdPort);
        curr = setMdPort;

        LetNode setMdDstIP = new LetNode(new VarExp("md.dstIP"), new VarExp("pkt.dstIP"));
        curr.addSucc(setMdDstIP);
        curr = setMdDstIP;

        // Note: do not set md.htype as HTypeDomain, because the host of HTTP(S) can be IP address

        curr.addSucc(exit);
    }

    private void initTLS(Node entry, Node exit) {
        Node curr = entry;

        AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeTLS)));
        curr.addSucc(guard);
        curr = guard;

        curr = addInitSymbolicList("pkt.host", "pkt.host", curr);
        curr = addInitSymbolic("pkt.port", "pkt.port", curr);
        curr = addInitSymbolic("pkt.dstIP", "pkt.dstIP", curr);


        LetNode setMdHost = new LetNode(new VarExp("md.host"), new VarExp("pkt.host"));
        curr.addSucc(setMdHost);
        curr = setMdHost;

        LetNode setMdPort = new LetNode(new VarExp("md.port"), new VarExp("pkt.port"));
        curr.addSucc(setMdPort);
        curr = setMdPort;

        LetNode setMdDstIP = new LetNode(new VarExp("md.dstIP"), new VarExp("pkt.dstIP"));
        curr.addSucc(setMdDstIP);
        curr = setMdDstIP;

        curr.addSucc(exit);
    }

    private void initTCP(Node entry, Node exit) {
        Node curr = entry;

        AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeTCP)));
        curr.addSucc(guard);
        curr = guard;

        curr = addInitSymbolicList("pkt.host", "pkt.host", curr);
        curr = addInitSymbolic("pkt.port", "pkt.port", curr);
        curr = addInitSymbolic("pkt.dstIP", "pkt.dstIP", curr);


        LetNode setMdHost = new LetNode(new VarExp("md.host"), new VarExp("pkt.host"));
        curr.addSucc(setMdHost);
        curr = setMdHost;

        LetNode setMdPort = new LetNode(new VarExp("md.port"), new VarExp("pkt.port"));
        curr.addSucc(setMdPort);
        curr = setMdPort;

        LetNode setMdDstIP = new LetNode(new VarExp("md.dstIP"), new VarExp("pkt.dstIP"));
        curr.addSucc(setMdDstIP);
        curr = setMdDstIP;

        curr.addSucc(exit);
    }

    public void generateCFG() {
        initCFG();
        graph.addNode(entryNode);
    }

    // do not add inherent constraints on null field
    private Node addInitSymbolic(String varName, String symName, Node curr) {
        Exp exp = new SymbolicIntExp(symName, 32);
        LetNode node = new LetNode(new VarExp(varName), exp);
        curr.addSucc(node);
        return node;
    }


    private Node addInitSymbolicList(String listName, String symListName, Node curr) {
        Exp listLen = new SymbolicIntExp(symListName + "_len", 32);

        ArrayList<Exp> elements = new ArrayList<>();
        for (int i = 0; i < ListExp.MAXLEN; i++) {
            elements.add(new SymbolicIntExp(symListName + "_" + i, 32));
        }
        ListExp initExp = new ListExp(elements, listLen);
        LetNode node = new LetNode(new VarExp(listName), initExp);
        curr.addSucc(node);

        AssumeNode assume = new AssumeNode(intInherent(listLen, 0, ListExp.MAXLEN));
        node.addSucc(assume);
        return assume;
    }

    private Node addVarEnumerate(ArrayList<Integer> values, String varName, Node curr) {
        ExpHelper helper = new ExpHelper();
        for (Integer value: values) {
            helper.add(new MatchExp(MatchExp.MatchType.EXACT, new VarExp(varName), new IntLiteralExp(value)), OR);
        }
        AssumeNode assume = new AssumeNode(helper.getExp());
        curr.addSucc(assume);
        return assume;
    }

    private Exp intInherent(Exp target, int min, int max) {
        Exp ge = new CompareExp(CompareExp.CompareType.GE, target, new IntLiteralExp(min));
        Exp lt = new CompareExp(CompareExp.CompareType.LT, target, new IntLiteralExp(max));
        return new LogicExp(LogicExp.LogicType.AND, ge, lt);
    }
}
