package midend.LinkerdWorker;

import frontend.Config;
import frontend.linkerd.Gateway;
import frontend.linkerd.component.Host;
import frontend.linkerd.component.Listener;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.ArrayList;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

public class GWGenerator {
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        for (Config config: list) {
            if (config instanceof Gateway) {
                Gateway gw = (Gateway) config;
                Node gwEntry = new NullNode(gw.metadata.get("name") + "-Entry");
                Node gwExit = new NullNode(gw.metadata.get("name") + "-Exit");
                generate(gw, gwEntry, gwExit);
                entry.addSucc(gwEntry);
                gwExit.addSucc(exit);
            }
        }
    }
    public static void generate(Gateway gw, Node entry, Node exit) {
        Node curr = entry;
        String name = gw.metadata.get("name");
        curr = assume(curr,
                eq(var("md.gateway"), literal(name, "md.gateway"), true));
        if (!gw.listeners.isEmpty()) {
            for (Listener listener : gw.listeners) {
                ListenerGenerate(listener, curr, exit);
            }
        }
    }

    public static void ListenerGenerate(Listener listener, Node entry, Node exit) {
        Node curr = entry;
        if (listener.hostname != null) {
            curr = assume(curr,
                    eq(var("md.host"), literalList("md.host", listener.hostname.domain, "\\.", false),
                            listener.hostname.type == Host.Type.FQDN));
        }
        if (listener.port != -1) {
            assume(curr,
                    eq(var("md.port"), literal(listener.port), true))
                    .addSucc(exit);
        }
    }
}
