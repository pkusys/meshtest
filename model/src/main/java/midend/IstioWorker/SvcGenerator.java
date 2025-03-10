package midend.IstioWorker;

import utils.PktHelper;
import utils.Role;
import utils.StringMap;
import frontend.Config;
import frontend.istio.Pod;
import frontend.istio.Service;
import frontend.istio.component.DomainHost;
import frontend.istio.component.KPort;
import midend.Exp.*;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.*;

import static midend.Exp.LogicExp.LogicType.OR;

public class SvcGenerator {

    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        Exp defaultCondition = null;
        Node podEntry = new NullNode("Pod-Entry");

        for (Config config: list) {
            if (config instanceof Service) {
                Service svc = (Service) config;
                String name = svc.metadata.get("name");
                Node svcTmpEntry = new NullNode(name + "-Entry");
                Node svcTmpExit = new NullNode(name + "-Exit");

                if (defaultCondition != null) {
                    defaultCondition = new LogicExp(OR, defaultCondition, generate(svc, svcTmpEntry, svcTmpExit));
                } else {
                    defaultCondition = generate(svc, svcTmpEntry, svcTmpExit);
                }

                entry.addSucc(svcTmpEntry);
                svcTmpExit.addSucc(podEntry);
            }
        }

        if (defaultCondition == null) {
            defaultCondition = new FalseExp();
        }

//        AssumeNode defaultGuard = new AssumeNode(new NotExp(defaultCondition));
//        entry.addSucc(defaultGuard);
//        defaultGuard.addSucc(podEntry);

        // SE in Service role
        SEGenerator.generateAll(list, entry, podEntry, Role.SERVICE);

        for (Config config: list) {
            if (config instanceof Pod) {
                Pod pod = (Pod) config;
                podGenerate(pod, podEntry, exit);
            }
        }
    }

    public static Exp generate(Service svc, Node entry, Node exit) {
        Node curr = entry;

        DomainHost host = new DomainHost(svc.metadata.get("name"));
        AssumeNode guard = VSGenerator.HostGuard(host, "md.host");
        curr.addSucc(guard);
        curr = guard;

        // Here I don't give the default route if no port matches
        if (!svc.ports.isEmpty()) {
            Node portsExit = new NullNode("Ports-Exit");
            for (KPort kPort: svc.ports)
                kPortGenerate(kPort, curr, portsExit);
            curr = portsExit;
        }

        for (Map.Entry<String, String> e: svc.selector.entrySet()) {
            curr = VSGenerator.addAction(e.getValue(), "md.dstlabel." + e.getKey(),
                    "md.dstlabel." + e.getKey(), curr);
            VSGenerator.addLabel("md.dstlabel." + e.getKey());
        }

        curr.addSucc(exit);
        return guard.getCondition();
    }

    public static void kPortGenerate(KPort kPort, Node entry, Node exit) {
        Node curr = entry;

        AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                new VarExp("md.port"), new IntLiteralExp(kPort.port)));
        curr.addSucc(guard);
        curr = guard;

        if (kPort.protocol != null) {
            int protocol;
            switch (kPort.protocol) {
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
                    throw new RuntimeException("Unknown port type: " + kPort.protocol);
            }
            guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                    new VarExp("md.type"), new IntLiteralExp(protocol)));
            curr.addSucc(guard);
            curr = guard;
        }

        if (kPort.targetPort != -1) {
            LetNode action = new LetNode(new VarExp("md.port"),
                    new IntLiteralExp(kPort.targetPort));
            curr.addSucc(action);
            curr = action;
        }

        curr.addSucc(exit);
    }

    private static void podGenerate(Pod pod, Node entry, Node exit) {
        Node curr = entry;
        LinkedHashMap<String, String> podLabels = pod.metadata.get("labels") != null ?
                (LinkedHashMap<String, String>) pod.metadata.get("labels") : new LinkedHashMap<>();

        for (String label: VSGenerator.dstLabels) {
            String key = label.substring(12);
            if (key.equals("app")) {
                String value = podLabels.get("app");
                Exp cond = new MatchExp(MatchExp.MatchType.EXACT,
                        new VarExp(label), new IntLiteralExp(StringMap.get(label, value)));
                AssumeNode guard = new AssumeNode(cond);
                curr.addSucc(guard);
                curr = guard;
            }
            if (podLabels.containsKey(key)) {
                String value = podLabels.get(key);
                Exp exactCond = new MatchExp(MatchExp.MatchType.EXACT,
                        new VarExp(label), new IntLiteralExp(StringMap.get(label, value)));
                AssumeNode exactGuard = new AssumeNode(exactCond);
                Exp noneCond = new MatchExp(MatchExp.MatchType.EXACT,
                        new VarExp(label), new IntLiteralExp(StringMap.NONE));
                AssumeNode noneGuard = new AssumeNode(noneCond);
                curr.addSucc(exactGuard);
                curr.addSucc(noneGuard);

                curr = new NullNode(key + "-Exit");
                exactGuard.addSucc(curr);
                noneGuard.addSucc(curr);
            } else {
                AssumeNode guard = new AssumeNode(new MatchExp(MatchExp.MatchType.EXACT,
                        new VarExp(label), new IntLiteralExp(StringMap.NONE)));
                curr.addSucc(guard);
                curr = guard;
            }
        }

        LetNode setPod = VSGenerator.HostAction(new DomainHost((String) pod.metadata.get("name")), "md.host");
        curr.addSucc(setPod);
        curr = setPod;

        curr.addSucc(exit);
    }
}
