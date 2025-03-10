package midend.IstioWorker;

import utils.PktHelper;
import frontend.Config;
import frontend.istio.WorkloadEntry;
import frontend.istio.component.DomainHost;
import frontend.istio.component.IPHost;
import midend.Exp.IntLiteralExp;
import midend.Exp.MatchExp;
import midend.Exp.VarExp;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.ArrayList;
import java.util.Map;

public class WEGenerator {
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        for (Config config: list) {
            if (config instanceof WorkloadEntry) {
                WorkloadEntry we = (WorkloadEntry) config;
                String name = we.metadata.get("name");
                Node weTmpEntry = new NullNode(name + "-Entry");
                Node weTmpExit = new NullNode(name + "-Exit");

                entry.addSucc(weTmpEntry);
                generate(we, weTmpEntry, weTmpExit);
                weTmpExit.addSucc(exit);
            }
        }
    }

    public static void generate(WorkloadEntry we, Node entry, Node exit) {
        Node curr = entry;

        // first match the labels
        for (Map.Entry<String, String> e: we.labels.entrySet()) {
            AssumeNode guard = VSGenerator.AssumeStringEq(e.getValue(),
                    "md.dstlabel." + e.getKey(), "md.dstlabel." + e.getKey());
            curr.addSucc(guard);
            curr = guard;
        }

        LetNode setAddr;
        if (isIPHost(we.address)) {
            setAddr = VSGenerator.HostAction(new IPHost(we.address), "md.dstIP");
        } else {
            setAddr = VSGenerator.HostAction(new DomainHost(we.address), "md.host");
        }
        curr.addSucc(setAddr);
        curr = setAddr;

        if (!we.ports.isEmpty()) {
            Node portsExit = new NullNode("Ports-Exit");
            for (Map.Entry<String, Integer> e: we.ports.entrySet()) {
                int type;
                switch (e.getKey()) {
                    case "HTTP":
                        type = PktHelper.TypeHTTP;
                        break;
                    case "TLS":
                        type = PktHelper.TypeTLS;
                        break;
                    case "TCP":
                        type = PktHelper.TypeTCP;
                        break;
                    case "HTTPS":
                        type = PktHelper.TypeHTTPS;
                        break;
                    default:
                        throw new RuntimeException("Unknown port type: " + e.getKey());
                }
                AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                        new VarExp("md.type"), new IntLiteralExp(type)));
                LetNode setPort = new LetNode(new VarExp("md.port"), new IntLiteralExp(e.getValue()));
                curr.addSucc(guard);
                guard.addSucc(setPort);
                setPort.addSucc(portsExit);
            }
            curr = portsExit;
        }

        curr.addSucc(exit);
    }

    private static boolean isIPHost(String address) {
        return address.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
}
