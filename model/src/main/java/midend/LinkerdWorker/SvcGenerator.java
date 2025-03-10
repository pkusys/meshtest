package midend.LinkerdWorker;

import frontend.Config;
import frontend.linkerd.Service;
import frontend.linkerd.component.Port;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.ArrayList;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

public class SvcGenerator {
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        for (Config config: list) {
            if (config instanceof Service) {
                Service svc = (Service) config;
                Node svcEntry = new NullNode(svc.metadata.get("name") + "-Entry");
                Node svcExit = new NullNode(svc.metadata.get("name") + "-Exit");
                generate(svc, svcEntry, svcExit);
                entry.addSucc(svcEntry);
                svcExit.addSucc(exit);
            }
        }
    }
    public static void generate(Service svc, Node entry, Node exit) {
        Node curr = entry;

        curr = assume(curr,
                eq(var("md.host"), literalList("md.host", svc.metadata.get("name"), "\\.", false),
                        true));

        for (Port port: svc.ports) {
            portGenerate(port, curr, exit);
        }
    }

    public static void portGenerate(Port port, Node entry, Node exit) {
        Node curr = entry;
        curr = assume(curr,
                eq(var("md.port"), literal(port.port), true));
        curr = assume(curr,
                eq(var("md.type"), literal("md.protocol", port.protocol), true));
        curr = let(curr,
                "md.port",
                literal(port.targetPort));
        curr.addSucc(exit);
    }
}
