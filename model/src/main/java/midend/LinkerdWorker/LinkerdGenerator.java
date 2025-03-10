package midend.LinkerdWorker;

import frontend.Config;
import frontend.linkerd.Gateway;
import frontend.linkerd.HTTPRoute;
import frontend.linkerd.Service;
import midend.Node.Node;
import midend.Node.NullNode;
import midend.Worker.Generator;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

public class LinkerdGenerator extends Generator {
    @Override
    public void fromYaml(String inputPath) throws IOException {
        Yaml yaml = new Yaml();

        InputStream fis = new FileInputStream(inputPath);
        Iterable<Object> yamlConf = yaml.loadAll(fis);

        for(Object obj : yamlConf) {
            assert obj instanceof Map;
            Map<String, Object> map = (Map<String, Object>) obj;
            String kind = (String) map.get("kind");
            switch (kind) {
                case "Gateway":
                    Gateway gw = new Gateway();
                    Config.isResource = true;
                    gw.fromYaml(map);
                    addResource(gw);
                    break;
                case "HTTPRoute":
                    HTTPRoute hr = new HTTPRoute();
                    Config.isResource = true;
                    hr.fromYaml(map);
                    addResource(hr);
                    break;
                case "Service":
                    Service svc = new Service();
                    Config.isResource = true;
                    svc.fromYaml(map);
                    addResource(svc);
                    break;
                default: break;
            }
        }
        fis.close();
    }

    @Override
    public void generateCFG() {
        initCFG();
        graph.addNode(entryNode);
    }

    public void initCFG() {
        entryNode = new NullNode("CFG-Entry");
        exitNode = new NullNode("CFG-Exit");
        Node curr = entryNode;

        Node mdExit = new NullNode("md-Exit");
        initMd(curr, mdExit);
        curr = mdExit;

        Node initExit = new NullNode("init-Exit");
        initHTTP(curr, initExit);
        intiTLS(curr, initExit);
        initTCP(curr, initExit);
        curr = initExit;

        Node entranceExit = new NullNode("entrance-Exit");
        GWGenerator.generateAll(resources,
                assume(curr,
                        eq(var("md.proxy"), literal("md.proxy", "gateway"), true)),
                entranceExit);
        assume(curr,
                eq(var("md.proxy"), literal("md.proxy", "sidecar"), true))
                .addSucc(entranceExit);
        curr = entranceExit;

        Node routeExit = new NullNode("route-Exit");
        HRGenerator.generateAll(resources, curr, routeExit);
        curr = routeExit;

        SvcGenerator.generateAll(resources, curr, exitNode);
    }

    public void initMd(Node entry, Node exit) {
        Node curr = entry;
        Node tmp;
        curr = let(curr,
                "md.type",
                sym("md.type"));

        curr = let(curr,
                "md.proxy",
                sym("md.proxy"));
        tmp = new NullNode("Temp");
        assume(curr,
                eq(var("md.proxy"), literal("md.proxy", "gateway"), true))
                .addSucc(tmp);
        assume(curr,
                eq(var("md.proxy"), literal("md.proxy", "sidecar"), true))
                .addSucc(tmp);
        curr = tmp;
        curr = let(curr,
                "md.srcns",
                sym("md.srcns"));
        curr = let(curr,
                "md.gateway",
                sym("md.gateway"));
        curr = let(curr,
                "md.weight",
                literal(100));
        curr.addSucc(exit);
    }

    public void initHTTP(Node entry, Node exit) {
        Node curr = entry;
        Node tmp;

        tmp = new NullNode("Temp");
        assume(curr,
                eq(var("md.type"), literal("md.type", "HTTP"), true))
                .addSucc(tmp);
        assume(curr,
                eq(var("md.type"), literal("md.type", "HTTPS"), true))
                .addSucc(tmp);
        curr = tmp;

        curr = let(curr,
                "pkt.host",
                symList("pkt.host"));
        curr = let(curr,
                "pkt.port",
                sym("pkt.port"));
        curr = let(curr,
                "pkt.uri",
                sym("pkt.uri"));
        curr = let(curr,
                "pkt.method",
                sym("pkt.method"));
        curr = let(curr,
                "pkt.scheme",
                sym("pkt.scheme"));
        for (String header: HRGenerator.headerSet) {
            curr = let(curr,
                    "pkt.header." + header,
                    symList("pkt.header." + header));
        }
        curr = let(curr,
                "md.host",
                var("pkt.host"));
        curr = let(curr,
                "md.port",
                var("pkt.port"));
        curr.addSucc(exit);
    }

    public void intiTLS(Node entry, Node exit) {
        Node curr = entry;
        curr = assume(curr,
                eq(var("md.type"), literal("md.type", "TLS"), true));
        curr = let(curr,
                "pkt.host",
                symList("pkt.host"));
        curr = let(curr,
                "pkt.port",
                sym("pkt.port"));
        curr = let(curr,
                "md.host",
                var("pkt.host"));
        curr = let(curr,
                "md.port",
                var("pkt.port"));
        curr.addSucc(exit);
    }

    public void initTCP(Node entry, Node exit) {
        Node curr = entry;
        curr = assume(curr,
                eq(var("md.type"), literal("md.type", "TCP"), true));
        curr = let(curr,
                "pkt.host",
                symList("pkt.host"));
        curr = let(curr,
                "pkt.port",
                sym("pkt.port"));
        curr = let(curr,
                "md.host",
                var("pkt.host"));
        curr = let(curr,
                "md.port",
                var("pkt.port"));
        curr.addSucc(exit);
    }
}
