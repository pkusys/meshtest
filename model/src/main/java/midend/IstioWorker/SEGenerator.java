package midend.IstioWorker;

import utils.ExpHelper;
import utils.PktHelper;
import frontend.Config;
import frontend.istio.ServiceEntry;
import frontend.istio.WorkloadEntry;
import frontend.istio.component.Host;
import frontend.istio.component.Port;
import midend.Exp.*;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;
import utils.Role;

import java.util.ArrayList;
import java.util.Map;

import static midend.Exp.LogicExp.LogicType.OR;

public class SEGenerator {
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit, Role role) {
        Exp defaultCondition = null;
        for (Config config: list) {
            if (config instanceof ServiceEntry) {
                ServiceEntry se = (ServiceEntry) config;
                String name = se.metadata.get("name");
                Node seTmpEntry = new NullNode(name + "-Entry");
                Node seTmpExit = new NullNode(name + "-Exit");

                if (defaultCondition == null)
                    defaultCondition = generate(se, seTmpEntry, seTmpExit, role);
                else {
                    if (role == Role.GATE) {
                        seTmpEntry = new AssumeNode(new NotExp(defaultCondition));
                    }
                    defaultCondition = new LogicExp(OR, defaultCondition,
                            generate(se, seTmpEntry, seTmpExit, role));
                }

                entry.addSucc(seTmpEntry);
                seTmpExit.addSucc(exit);
            }
        }
    }

    public static Exp generate(ServiceEntry se, Node entry, Node exit, Role role) {
        Node curr = entry;
        ExpHelper helper = new ExpHelper();

        // TODO: IP matching mechanism
        Node hostExit = new NullNode("Host-Exit");
        for (Host h: se.hosts) {
            AssumeNode guard = VSGenerator.HostGuard(h, "md.host");
            helper.add(guard.getCondition(), ExpHelper.OR);
            curr.addSucc(guard);
            guard.addSucc(hostExit);
        }
        curr = hostExit;

        if (!se.ports.isEmpty()) {
            Node portsExit = new NullNode("Ports-Exit");
            for (Port port: se.ports)
                portGenerate(port, curr, portsExit, role);
            curr = portsExit;
        }

        // NOTE: workloadSelector and endpoints are mutually exclusive
        if (role == Role.SERVICE) {
            if (se.workloadSelector != null)
                for (Map.Entry<String, String> e : se.workloadSelector.labels.entrySet()) {
                    curr = VSGenerator.addAction(e.getValue(), "md.dstlabel." + e.getKey(),
                            "md.dstlabel." + e.getKey(), curr);
                    VSGenerator.addLabel("md.dstlabel." + e.getKey());
                }

            if (!se.endpoints.isEmpty()) {
                Node endpointsExit = new NullNode("Endpoints-Exit");
                int count = 0;
                for (WorkloadEntry endpoint : se.endpoints) {
                    String name = "Endpoint-" + count;
                    Node endpointEntry = new NullNode(name + "-Entry");
                    curr.addSucc(endpointEntry);
                    WEGenerator.generate(endpoint, endpointEntry, endpointsExit);
                    count++;
                }
                curr = endpointsExit;
            }
        }

        curr.addSucc(exit);
        return helper.getExp();
    }

    public static void portGenerate(Port port, Node entry, Node exit, Role role) {
        Node curr = entry;

        AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.port"), new IntLiteralExp(port.number)));
        curr.addSucc(guard);
        curr = guard;

        if (port.protocol != null) {
            int protocol;
            switch (port.protocol) {
                case "HTTP":
                    protocol = PktHelper.TypeHTTP;
                    break;
                case "TLS":
                    protocol = PktHelper.TypeTLS;
                    break;
                case "TCP":
                    protocol = PktHelper.TypeTCP;
                    break;
                case "HTTPS":
                    protocol = PktHelper.TypeHTTPS;
                    break;
                default:
                    throw new RuntimeException("Unknown port type: " + port.protocol);
            }
            guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                    new VarExp("md.type"), new IntLiteralExp(protocol)));
            curr.addSucc(guard);
            curr = guard;
        }

        if (role == Role.SERVICE) {
            if (port.targetPort != -1) {
                LetNode action = new LetNode(new VarExp("md.port"),
                        new IntLiteralExp(port.targetPort));
                curr.addSucc(action);
                curr = action;
            }
        }

        curr.addSucc(exit);
    }
}
