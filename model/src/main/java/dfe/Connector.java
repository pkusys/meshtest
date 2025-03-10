package dfe;

import frontend.Config;
import frontend.istio.component.*;
import frontend.istio.*;
import utils.Rand;

import java.util.ArrayList;
import java.util.HashMap;

import static utils.AssertionHelper.Assert;

/**
 * Connector builds the actual connections between resources, filling
 * some crucial fields in the config under the guidance of the skeleton.
 * Input:
 * <ul>
 *     <li>The skeleton of the service mesh configuration.</li>
 * </ul>
 * The implementation of connector is based on specific service mesh, while
 * the principles are universal.
 */
public class Connector {
    // Connector considers fields including host, gateway.
    // Port and subset are of simple semantic, so Filler will consider them.
    // Label makes weak effect now, so Filler take this responsibility.
    private final ArrayList<Skeleton.Node> nodes;
    private final HashMap<Skeleton.Node, Config> configs;
    private final Util util;

    public Connector(ArrayList<Skeleton.Node> nodes, Util util) {
        // Util should be created outside for sharing in the whole process.
        this.nodes = nodes;
        this.util = util;
        this.configs = createConfigs(nodes);
    }

    public HashMap<Skeleton.Node, Config> getConfigs() {
        return configs;
    }

    private HashMap<Skeleton.Node, Config> createConfigs(ArrayList<Skeleton.Node> nodes) {
        HashMap<Skeleton.Node, Config> configs = new HashMap<>();
        for (Skeleton.Node node: nodes) {
            switch (node.kind) {
                case "VS":
                    VirtualService vs = new VirtualService();
                    vs.metadata.put("name", util.getName("vs"));
                    configs.put(node, vs);
                    break;
                case "DR":
                    DestinationRule dr = new DestinationRule();
                    dr.metadata.put("name", util.getName("dr"));
                    configs.put(node, dr);
                    break;
                case "GW":
                    Gateway gw = new Gateway();
                    gw.metadata.put("name", util.getName("gw"));
                    configs.put(node, gw);
                    break;
                case "SE-G", "SE-S":
                    ServiceEntry se = new ServiceEntry();
                    se.metadata.put("name", util.getName("se"));
                    configs.put(node, se);
                    break;
                case "SVC":
                    Service svc = new Service();
                    svc.metadata.put("name", util.getService());
                    configs.put(node, svc);
                    break;
                case "Entry", "Exit": break;
            }
        }
        return configs;
    }

    public void connect() {
        for (int i = nodes.size() - 1; i >= 0; -- i) {
            Skeleton.Node node = nodes.get(i);
            switch (node.kind) {
                case "VS": connectVS(node); break;
                case "DR": connectDR(node); break;
                case "GW": connectGW(node); break;
                case "SE-G", "SE-S": connectSE(node); break;
                case "SVC": connectSVC(node); break;
                case "Entry", "Exit": break;
            }
        }
    }

    private void connectVS(Skeleton.Node node) {
        VirtualService vs = (VirtualService) configs.get(node);

        boolean delegate = false;
        for (Skeleton.Node n: node.pred) {
            if (n.kind.equals("VS")) {
                delegate = true;
                break;
            }
        }

        if (!delegate) {
            String host = util.getHost();
            vs.hosts.add(new DomainHost(host));
            // Put the VirtualService to the sidecar
            vs.gateways.add("mesh");
        }

        // Here we don't merge destination for Route.
        for (Skeleton.Node n: node.succ) {
            HTTPRoute route = new HTTPRoute();
            if (n.kind.equals("VS")) {
                VirtualService delegateVs = (VirtualService) configs.get(n);
                Delegate del = new Delegate();
                del.name = new DomainHost(delegateVs.metadata.get("name"));
                route.delegate = del;
            } else if (n.kind.equals("DR")) {
                DestinationRule dr = (DestinationRule) configs.get(n);
                RouteDestination rd = new RouteDestination();
                Destination dest = new Destination();
                dest.host = dr.host;
                rd.destination = dest;
                route.route.add(rd);
            } else if (n.kind.equals("SE-S")) {
                ServiceEntry se = (ServiceEntry) configs.get(n);
                // Here we don't assume that the ServiceEntry only has one host.
                // But only use the first host as the destination.
                RouteDestination rd = new RouteDestination();
                Destination dest = new Destination();
                dest.host = se.hosts.get(0);
                rd.destination = dest;
                route.route.add(rd);
            } else if (n.kind.equals("SVC")) {
                Service svc = (Service) configs.get(n);
                RouteDestination rd = new RouteDestination();
                Destination dest = new Destination();
                dest.host = new DomainHost(svc.metadata.get("name")
                        + ".default.svc.cluster.local");
                rd.destination = dest;
                route.route.add(rd);
            }
            vs.http.add(route);
        }
    }

    private void connectDR(Skeleton.Node node) {
        Assert(node.succ.size() == 1,
                "DestinationRule can make effect on one exact host at most.");

        DestinationRule dr = (DestinationRule) configs.get(node);
        Skeleton.Node n = node.succ.get(0);
        if (n.kind.equals("SE-S")) {
            ServiceEntry se = (ServiceEntry) configs.get(n);
            // Here we don't assume that the ServiceEntry only has one host.
            // But deploy the DestinationRule on the first single host.
            dr.host = se.hosts.get(0);
        } else if (n.kind.equals("SVC")) {
            Service svc = (Service) configs.get(n);
            dr.host = new DomainHost(svc.metadata.get("name")
                    + ".default.svc.cluster.local");
        }
    }

    private void connectGW(Skeleton.Node node) {
        Gateway gw = (Gateway) configs.get(node);
        String gwName = gw.metadata.get("name");
        for (Skeleton.Node n: node.succ) {
            if (n.kind.equals("VS")) {
                VirtualService vs = (VirtualService) configs.get(n);
                vs.gateways.add(gwName);
                // Here we don't assume that the VirtualService only has one host.
                // But only use the first host as the server.
                String hostname = vs.hosts.get(0).domain;
                Server server = new Server();
                if (Rand.RandBool(50)) {
                    server.hosts.add(new DomainHost(util.randomChangeHost(hostname)));
                } else {
                    server.hosts.add(new DomainHost(hostname));
                }
                gw.servers.add(server);
            }
        }
    }

    private void connectSE(Skeleton.Node node) {
        ServiceEntry se = (ServiceEntry) configs.get(node);
        if (node.kind.equals("SE-G")) {
            ArrayList<String> hosts = new ArrayList<>();

            for (Skeleton.Node n: node.succ) {
                if (n.kind.equals("VS")) {
                    VirtualService vs = (VirtualService) configs.get(n);
                    String host = Rand.RandBool(50) ?
                            util.randomChangeHost(vs.hosts.get(0).domain) : vs.hosts.get(0).domain;
                    if (!hosts.contains(host)) {
                        hosts.add(host);
                    }
                } else if (n.kind.equals("SVC")) {
                    Service svc = (Service) configs.get(n);
                    String host = svc.metadata.get("name")
                            + ".default.svc.cluster.local";
                    if (!hosts.contains(host)) {
                        hosts.add(host);
                    }
                }
            }

            se.hosts.addAll(hosts.stream().map(DomainHost::new).toList());
        } else {
            // To avoid most cases that SE with same host but different workloadSelector.
            // However, large host pool may cause less interleaving, I am not sure whether
            // it will affect the bugs much.
            String host = util.getSequentialHost();
            se.hosts.add(new DomainHost(host));
        }
    }

    private void connectSVC(Skeleton.Node node) {
        // Service is the base infrastructure of the cluster.
        // And it doesn't need to connect to other resources.
    }
}
