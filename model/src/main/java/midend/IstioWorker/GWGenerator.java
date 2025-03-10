package midend.IstioWorker;

import frontend.Config;
import frontend.istio.Gateway;
import frontend.istio.component.Host;
import frontend.istio.component.Server;
import midend.Node.AssumeNode;
import midend.Node.Node;
import midend.Node.NullNode;
import utils.Role;

import java.util.ArrayList;
import java.util.Map;

public class GWGenerator {
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        for (Config config: list) {
            if (config instanceof Gateway) {
                Gateway gw = (Gateway) config;
                String name = gw.metadata.get("name");
                Node gwTmpEntry = new NullNode(name + "-Entry");
                Node gwTmpExit = new NullNode(name + "-Exit");

                generate(gw, gwTmpEntry, gwTmpExit);
                entry.addSucc(gwTmpEntry);
                gwTmpExit.addSucc(exit);
            }
        }
    }

    public static void generate(Gateway gw, Node entry, Node exit) {
        Node curr = entry;

        if (!gw.servers.isEmpty()) {
            Node serversExit = new NullNode("Servers-Exit");
            for (Server server: gw.servers)
                serverGenerate(server, curr, serversExit);
            curr = serversExit;
        }

        for (Map.Entry<String, String> e: gw.selector.entrySet())
            curr = VSGenerator.addAction(e.getValue(), "md.dstlabel." + e.getKey(),
                    "md.dstlabel." + e.getKey(), curr);

        AssumeNode gwGuard = VSGenerator.AssumeStringEq(gw.metadata.get("name"), "md.gateway", "md.gateway");
        curr.addSucc(gwGuard);
        curr = gwGuard;

        curr.addSucc(exit);
    }

    private static void serverGenerate(Server server, Node entry, Node exit) {
        Node curr = entry;

        Node portExit = new NullNode("Port-Exit");
        SEGenerator.portGenerate(server.port, curr, portExit, Role.GATE);
        curr = portExit;

        Node hostExit = new NullNode("Host-Exit");
        for (Host h: server.hosts) {
            AssumeNode guard = VSGenerator.HostGuard(h, "md.host");
            curr.addSucc(guard);
            guard.addSucc(hostExit);
        }
        curr = hostExit;

        curr.addSucc(exit);
    }
}
