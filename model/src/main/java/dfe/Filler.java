package dfe;

import frontend.Config;
import frontend.istio.component.*;
import frontend.istio.*;
import utils.Rand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static utils.AssertionHelper.Assert;

/**
 * Filler works based on the skeleton and connection built.
 * It takes care of port and subset semantics and performs like fuzzing.
 */
public class Filler {
    private final ArrayList<Skeleton.Node> nodes;
    private final HashMap<Skeleton.Node, Config> configs;
    private final Util util;

    public Filler(ArrayList<Skeleton.Node> nodes,
                  HashMap<Skeleton.Node, Config> configs, Util util) {
        this.nodes = nodes;
        this.configs = configs;
        this.util = util;
    }


    public void fill() {
        // The fill process may follow specific order based on the underlying system.
        // However, this process can follow casual order, with weak orchestration.
        for (Skeleton.Node node: nodes) {
            if (!node.kind.equals("VS")) {
                switch (node.kind) {
                    case "DR": fillDR(node, (DestinationRule) configs.get(node)); break;
                    case "GW": fillGW(node, (Gateway) configs.get(node)); break;
                    case "SE-G", "SE-S": fillSE(node, (ServiceEntry) configs.get(node)); break;
                    case "SVC": fillSVC(node, (Service) configs.get(node)); break;
                    case "Entry", "Exit": break;
                }
            }
        }

        for (Skeleton.Node node: nodes) {
            if (node.kind.equals("VS")) {
                fillVS(node, (VirtualService) configs.get(node));
            }
        }
    }

    public void fillVS(Skeleton.Node node, VirtualService vs) {
        // Here we assume that such cases like two headers in single match
        // won't trigger bugs in the system.
        HTTPRoute rootHR = null;
        ArrayList<Integer> frontPorts = new ArrayList<>();
        for (Skeleton.Node pred: node.pred) {
            if (pred.kind.equals("GW")) {
                frontPorts.addAll(((Gateway) configs.get(pred)).servers.stream().
                        map(server -> server.port.number).toList());
            } else if (pred.kind.equals("SE-G")) {
                frontPorts.addAll(((ServiceEntry) configs.get(pred)).ports.stream().
                        map(port -> port.number).toList());
            } else if (pred.kind.equals("VS")) {
                for (HTTPRoute hr: ((VirtualService) configs.get(pred)).http) {
                    if (hr.delegate != null &&
                            hr.delegate.name.domain.equals(vs.metadata.get("name"))) {
                        rootHR = hr;
                        break;
                    }
                }
            }
        }

        Assert(node.succ.size() == vs.http.size(),
                "Should create HTTPRoute for every successor of VirtualService.");
        for (int i = 0; i < node.succ.size(); ++ i) {
            Skeleton.Node succ = node.succ.get(i);
            HTTPRoute route = vs.http.get(i);

            // According to current issue, we support multiple match in single route.
            int matchNum = Rand.RandBool(30) ? 2 : 1;
            int memberNum = 1;  // For future extension
            for (int j = 0; j < matchNum; ++ j) {
                HTTPMatchRequest match = new HTTPMatchRequest();
                ArrayList<Util.MemberType> memberTypes = util.getMemberTypes(memberNum);
                for (Util.MemberType type: memberTypes) {
                    if (rootHR != null) {
                        copyMember(match, type, rootHR);
                    } else {
                        buildMember(match, type, frontPorts);
                    }
                }
                route.match.add(match);
            }

            // Since code below is about building the logic of destination.
            // A route with delegate should skip this part.
            if (route.delegate != null) {
                continue;
            }

            ArrayList<String> subsets = new ArrayList<>();
            ArrayList<Integer> backPorts = new ArrayList<>();
            if (succ.kind.equals("DR")) {
                subsets.addAll(((DestinationRule) configs.get(succ)).subsets.stream().
                        map(subset -> subset.name).toList());
                for (Skeleton.Node backend: succ.succ) {
                    if (backend.kind.equals("SVC")) {
                        backPorts.addAll(((Service) configs.get(backend)).ports.stream().
                                map(port -> port.port).toList());
                    } else if (backend.kind.equals("SE-S")) {
                        backPorts.addAll(((ServiceEntry) configs.get(backend)).ports.stream().
                                map(port -> port.number).toList());
                    }
                }
            } else if (succ.kind.equals("SE-S")) {
                backPorts.addAll(((ServiceEntry) configs.get(succ)).ports.stream().
                        map(port -> port.number).toList());
            } else if (succ.kind.equals("SVC")) {
                backPorts.addAll(((Service) configs.get(succ)).ports.stream().
                        map(port -> port.port).toList());
            }

            Assert(!backPorts.isEmpty(),
                    "HTTPRoute must have backends.");

            if (!subsets.isEmpty()) {
                route.route.get(0).destination.subset = subsets.get(Rand.RandInt(0, subsets.size()));
            }
            Wrapped.PortSelector port = new Wrapped.PortSelector();
            port.number = backPorts.get(Rand.RandInt(0, backPorts.size()));
            route.route.get(0).destination.port = port;

            // Sometimes rewrite
            if (Rand.RandBool(20)) {
                HTTPRewrite rewrite = new HTTPRewrite();
                rewrite.authority = new DomainHost(util.getExactHost());
                route.rewrite = rewrite;
            }

            // Sometimes header manipulation
            if (Rand.RandBool(20)) {
                Headers headers = new Headers();
                Headers.HeaderOperations request = new Headers.HeaderOperations();
                int rand = Rand.RandInt(0, 3);
                Map.Entry<String, String> header = Rand.RandBool(70) || route.rewrite != null ?     // If rewrite authority, cannot set authority in header manipulation.
                        util.getOrdinaryHeader() : Map.entry(":authority", util.getExactHost());
                switch (rand) {
                    case 0: request.add.put(header.getKey(), header.getValue()); break;
                    case 1: request.set.put(header.getKey(), header.getValue()); break;
                    case 2: if (!header.getKey().startsWith(":")) {
                        request.remove.add(header.getKey());
                    } break;
                }
                headers.request = request;
                route.headers = headers;
            }
        }
    }

    // Build a new member based on the type, for HTTPMatch.
    private void buildMember(HTTPMatchRequest match, Util.MemberType type, ArrayList<Integer> frontPorts) {
        switch (type) {
            case URI_EXACT:
                match.uri = new HTTPMatchRequest.StringMatch() {{
                    type = MatchType.EXACT;
                    value = util.getUri();
                }};
                break;
            case URI_PREFIX:
                match.uri = new HTTPMatchRequest.StringMatch() {{
                    type = MatchType.PREFIX;
                    value = util.getUri();
                }};
                break;
            case PORT:
                if (!frontPorts.isEmpty()) {
                    match.port = frontPorts.get(Rand.RandInt(0, frontPorts.size()));
                }
                break;
            case AUTHORITY:
                match.authority = new HTTPMatchRequest.StringMatch() {{
                    type = MatchType.EXACT;
                    value = util.getExactHost();
                }};
                break;
            case HEADER:
                Map.Entry<String, String> header = util.getHeader();
                // Header value can be empty
                match.headers.put(header.getKey(), new HTTPMatchRequest.StringMatch() {{
                    type = MatchType.EXACT;
                    value = header.getValue();
                }});
                break;
            case WITHOUT_HEADER:
                Map.Entry<String, String> withoutHeader = util.getHeader();
                match.withoutHeaders.put(withoutHeader.getKey(), new HTTPMatchRequest.StringMatch() {{
                    type = MatchType.EXACT;
                    value = withoutHeader.getValue();
                }});
                break;
        }
    }

    // Copy member from the root HTTPRoute.
    // If the root doesn't have this member, build a new one.
    private void copyMember(HTTPMatchRequest match, Util.MemberType type, HTTPRoute hr) {
        boolean copy = false;
        for (HTTPMatchRequest m: hr.match) {
            switch (type) {
                case URI_EXACT, URI_PREFIX:
                    if (m.uri != null) {
                        if (m.uri.type == HTTPMatchRequest.StringMatch.MatchType.EXACT) {
                            match.uri = m.uri;
                        } else if (m.uri.type == HTTPMatchRequest.StringMatch.MatchType.PREFIX) {
                            match.uri = new HTTPMatchRequest.StringMatch() {{
                                type = MatchType.PREFIX;
                                value = m.uri.value + util.getUri();
                            }};
                        }
                        copy = true;
                    }
                    break;
                case PORT:
                    if (m.port != -1) {
                        match.port = m.port;
                        copy = true;
                    }
                    break;
                case AUTHORITY:
                    if (m.authority != null) {
                        match.authority = m.authority;
                        copy = true;
                    }
                    break;
                case HEADER:
                    if (!m.headers.isEmpty()) {
                        match.headers = m.headers;
                        copy = true;
                    }
                    break;
                case WITHOUT_HEADER:
                    if (!m.withoutHeaders.isEmpty()) {
                        match.withoutHeaders = m.withoutHeaders;
                        copy = true;
                    }
                    break;
            }
            if (copy) {
                break;
            }
        }

        if (!copy) {
            buildMember(match, type, new ArrayList<>());
        }
    }

    public void fillDR(Skeleton.Node node, DestinationRule dr) {
        int subsetNum = Rand.RandInt(1, 3);
        for (int i = 0; i < subsetNum; ++ i) {
            String version = "v" + (i + 1);
            Subset subset = new Subset();
            subset.name = version;
            subset.labels.put("version", version);
            dr.subsets.add(subset);
        }
    }

    public void fillGW(Skeleton.Node node, Gateway gw) {
        Assert(!gw.servers.isEmpty(),
                "Gateway must have at least one server before filling.");

        ArrayList<Integer> ports = util.getPorts(gw.servers.size());
        for (int i = 0; i < gw.servers.size(); ++ i) {
            Server server = gw.servers.get(i);
            Integer p = ports.get(i);
            Port port = new Port();
            port.number = p;
            port.name = util.getName("port");
            port.protocol = "HTTP";
            server.port = port;
        }
    }

    // We assume that single ServiceEntry only plays one role here.
    // However, it can play different roles in fact and we can implement it by
    // merging SE.
    public void fillSE(Skeleton.Node node, ServiceEntry se) {
        int portNum = Rand.RandInt(1, 3);
        ArrayList<Integer> ports = util.getPorts(portNum);
        for (Integer p: ports) {
            Port port = new Port();
            port.number = p;
            port.name = util.getName("port");
            port.protocol = "HTTP";
            port.targetPort = util.getPodPort();
            se.ports.add(port);
        }

        if (node.kind.equals("SE-G")) {
            boolean wildcardHost = false;
            for (DomainHost host: se.hosts) {
                if (host.kind == DomainHost.DomainKind.WILDCARD) {
                    wildcardHost = true;
                    break;
                }
            }

            // ServiceEntry with resolution DNS should have exact hosts.
            if (!wildcardHost) {
                se.resolution = ServiceEntry.DNS;
            } else {
                se.resolution = ServiceEntry.STATIC;
                se.workloadSelector = new WorkloadSelector();
                se.workloadSelector.labels.put("app", util.getApp());
            }
        } else {
            se.resolution = ServiceEntry.STATIC;
            se.workloadSelector = new WorkloadSelector();
            se.workloadSelector.labels.put("app", util.getApp());
        }
    }

    public void fillSVC(Skeleton.Node node, Service svc) {
        svc.selector.put("app", svc.metadata.get("name"));

        int portNum = Rand.RandInt(1, 3);
        ArrayList<Integer> ports = util.getPorts(portNum);
        for (Integer p: ports) {
            KPort port = new KPort();
            port.port = p;
            port.name = util.getName("port");
            port.targetPort = util.getPodPort();
            svc.ports.add(port);
        }
    }

    // Sort and return the configs.
    public ArrayList<Config> sort() {
        ArrayList<Config> result = new ArrayList<>();
        ArrayList<Config> temp = new ArrayList<>(configs.values());
        ArrayList<VirtualService> vsList = new ArrayList<>();
        ArrayList<DestinationRule> drList = new ArrayList<>();
        ArrayList<Gateway> gwList = new ArrayList<>();
        ArrayList<ServiceEntry> seList = new ArrayList<>();
        ArrayList<Service> svcList = new ArrayList<>();

        for (Config config: temp) {
            if (config instanceof VirtualService vs) {
                vsList.add(vs);
            } else if (config instanceof DestinationRule dr) {
                drList.add(dr);
            } else if (config instanceof Gateway gw) {
                gwList.add(gw);
            } else if (config instanceof ServiceEntry se) {
                seList.add(se);
            } else if (config instanceof Service svc) {
                svcList.add(svc);
            }
        }

        // VirtualService with exact host has higher priority.
        for (VirtualService vs: vsList) {
            if (!vs.hosts.isEmpty() && util.isExact(vs.hosts.get(0).domain)) {
                result.add(vs);
            }
        }
        for (VirtualService vs: vsList) {
            if (!result.contains(vs)) {
                result.add(vs);
            }
        }
        result.addAll(seList);
        result.addAll(gwList);
        result.addAll(drList);
        result.addAll(svcList);

        appendPods(result);

        return result;
    }

    private void appendPods(ArrayList<Config> configs) {
        ArrayList<String> appList = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof ServiceEntry se) {
                if (se.workloadSelector != null) {
                    String app = se.workloadSelector.labels.get("app");
                    if (!appList.contains(app)) {
                        appList.add(app);
                    }
                }
            } else if (config instanceof Service svc) {
                String app = svc.metadata.get("name");
                if (!appList.contains(app)) {
                    appList.add(app);
                }
            }
        }

        Assert(!appList.isEmpty(),
                "There must be backend Pods under configuration.");
        int versionNum = 2;
        for (String app: appList) {
            for (int i = 0; i < versionNum; ++ i) {
                String version = "v" + (i + 1);
                Pod pod = new Pod();
                pod.metadata.put("name", app + "-" + version);
                LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                labels.put("app", app);
                labels.put("version", version);
                pod.metadata.put("labels", labels);

                ArrayList<Object> containers = pod.containers;
                LinkedHashMap<String, Object> container = new LinkedHashMap<>();
                containers.add(container);
                container.put("name", "receiver");
                container.put("image", "localhost:15000/receiver");
                container.put("imagePullPolicy", "Always");
                ArrayList<Object> volumeMounts = new ArrayList<>();
                container.put("volumeMounts", volumeMounts);
                LinkedHashMap<String, Object> volumeMountConfig = new LinkedHashMap<>();
                volumeMounts.add(volumeMountConfig);
                volumeMountConfig.put("name", "config");
                volumeMountConfig.put("mountPath", "/app/config");

                ArrayList<Object> volumes = pod.volumes;
                LinkedHashMap<String, Object> volumeConfig = new LinkedHashMap<>();
                volumes.add(volumeConfig);
                volumeConfig.put("name", "config");
                volumeConfig.put("hostPath", new LinkedHashMap<>(Map.of("path", "/app/config")));

                configs.add(pod);
            }
        }
    }
}
