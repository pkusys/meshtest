package conf;

import frontend.Config;
import frontend.istio.component.*;
import frontend.istio.*;
import utils.Rand;

import java.util.*;
import java.util.function.Function;

import static utils.AssertionHelper.Assert;

public class RationalGen {
    public ArrayList<Resource> resources;
    public ArrayList<Boolean> visited;  // for DFS
    public RationalMaker maker;
    public Parameters parameters;

    public RationalGen(Parameters parameters) {
        this.resources = new ArrayList<>();
        this.maker = new RationalMaker();
        this.configs = new ArrayList<>();
        this.visited = new ArrayList<>();
        this.parameters = parameters;
    }

    public RationalGen addResource(Resource resource) {
        this.resources.add(resource);
        this.configs.add(createConfig(resource));
        this.visited.add(false);
        extendResource(resource);
        return this;
    }

    public void extendResource(Resource resource) {
        switch (resource.kind) {
            case VIRTUAL_SERVICE: extendVS(resource); break;
            case DESTINATION_RULE: extendDR(resource); break;
            case GATEWAY: extendGW(resource); break;
            case SERVICE_ENTRY: extendSE(resource); break;
            case SERVICE: extendSVC(resource); break;
            case POD: extendPOD(resource); break;
        }
    }

    public ArrayList<Resource> getExactResource(Resource.Kind kind, String host) {
        ArrayList<Resource> result = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource.kind == kind && resource.host.equals(host)) {
                result.add(resource);
            }
        }
        return result;
    }

    public ArrayList<Resource> getResource(Resource.Kind kind, String host) {
        ArrayList<String> hosts = maker.extendHost(host);
        ArrayList<Resource> result = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource.kind == kind && hosts.contains(resource.host)) {
                result.add(resource);
            }
        }
        return result;
    }

    public int resourceIndex(Resource resource) {
        for (int i = 0; i < resources.size(); ++ i) {
            if (resources.get(i).equals(resource)) {
                return i;
            }
        }
        return -1;
    }

    private void extendVS(Resource vs) {
        ArrayList<Resource> NewResources = new ArrayList<>();
        int rand;

        // front
        if (vs.pred.isEmpty()) {
            rand = Rand.RandInt(2);

            ArrayList<Resource> SEs = getResource(Resource.Kind.SERVICE_ENTRY, vs.host);
            if (!SEs.isEmpty()) {
                for (Resource se : SEs) {
                    vs.addParent(se);
                    vs.propagate(se, "frontPort", "backPort");
                }
            } else if (rand == 0) {
                Resource se = new Resource(Resource.Kind.SERVICE_ENTRY, vs.host);
                vs.addParent(se);
                vs.propagate(se, "frontPort", "backPort");
                NewResources.add(se);
            }

            ArrayList<Resource> GWs = getResource(Resource.Kind.GATEWAY, vs.host);
            if (vs.gateway != null) {
                Resource gw = new Resource(Resource.Kind.GATEWAY, vs.host);
                vs.addParent(gw);
                vs.propagate(gw, "gateway", "gateway");
                vs.propagate(gw, "frontPort", "backPort");
                NewResources.add(gw);
            } else if (!GWs.isEmpty()) {
                for (Resource gw : GWs) {
                    vs.addParent(gw);
                    vs.propagate(gw, "frontPort", "backPort");
                }
            } else if (rand == 1) {
                Resource gw = new Resource(Resource.Kind.GATEWAY, vs.host);
                vs.addParent(gw);
                vs.propagate(gw, "gateway", "gateway");
                vs.propagate(gw, "frontPort", "backPort");
                NewResources.add(gw);
            }
        }

        // back
        if (vs.succ.isEmpty()) {
            rand = Rand.RandInt(1, 3);
            ArrayList<String> services = new ArrayList<>();
            if (Rand.RandBool(50) && !parameters.gatewayAPI) {
                services.add(maker.exactHost(maker.getHost()));
                services.addAll(maker.getServices(rand - 1));
            } else {
                services.addAll(maker.getServices(rand));
            }

            for (String service : services) {
                ArrayList<Resource> DRs = getResource(Resource.Kind.DESTINATION_RULE, service);
                if (!DRs.isEmpty()) {
                    for (Resource dr : DRs) {
                        vs.addSuccessor(dr);
                        vs.propagate(dr, "backPort", "backPort");
                        vs.propagate(dr, "subset", "subset");
                    }
                } else {
                    Resource dr = new Resource(Resource.Kind.DESTINATION_RULE, service);
                    vs.addSuccessor(dr);
                    vs.propagate(dr, "backPort", "backPort");
                    vs.propagate(dr, "subset", "subset");
                    NewResources.add(dr);
                }
            }
        }

        // delegate
        if (vs.DELEGATE_ROOT && !parameters.gatewayAPI) {
            Resource delegateVs = new Resource(Resource.Kind.VIRTUAL_SERVICE, null);
            delegateVs.DELEGATE_SUCC = true;
            vs.addSuccessor(delegateVs);
            vs.propagate(delegateVs, "delegate", "delegate");
            vs.propagate(delegateVs, "frontPort", "frontPort");
            vs.propagate(delegateVs, "backPort", "backPort");
            vs.propagate(delegateVs, "subset", "subset");
            NewResources.add(delegateVs);
        }

        // extend
        for (Resource resource : NewResources) {
            addResource(resource);
        }
    }

    private void extendDR(Resource dr) {
        ArrayList<Resource> tempResources = new ArrayList<>();

        // front
        if (dr.pred.isEmpty()) {
            String host = maker.getHost();
            ArrayList<Resource> VSs = getResource(Resource.Kind.VIRTUAL_SERVICE, host);
            if (!VSs.isEmpty()) {
                Resource vs = VSs.get(0);
                dr.addParent(vs);
                vs.propagate(dr, "backPort", "backPort");
                vs.propagate(dr, "label", "label");
            } else {
                Resource vs = new Resource(Resource.Kind.VIRTUAL_SERVICE, host);
                dr.addParent(vs);
                vs.propagate(dr, "backPort", "backPort");
                vs.propagate(dr, "label", "label");
                tempResources.add(vs);
            }
        }

        // back
        if (dr.succ.isEmpty()) {
            if (maker.isService(dr.host)) {
                ArrayList<Resource> SVCs = getResource(Resource.Kind.SERVICE, dr.host);
                if (!SVCs.isEmpty()) {
                    for (Resource svc : SVCs) {
                        dr.addSuccessor(svc);
                        dr.propagate(svc, "backPort", "frontPort");
                        dr.propagate(svc, "label", "label");
                    }
                } else {
                    Resource svc = new Resource(Resource.Kind.SERVICE, dr.host);
                    dr.addSuccessor(svc);
                    dr.propagate(svc, "backPort", "frontPort");
                    dr.propagate(svc, "label", "label");
                    tempResources.add(svc);
                }
            } else {
                ArrayList<Resource> SEs = getExactResource(Resource.Kind.SERVICE_ENTRY, dr.host);
                if (!SEs.isEmpty()) {
                    for (Resource se : SEs) {
                        dr.addSuccessor(se);
                        dr.propagate(se, "backPort", "frontPort");
                        dr.propagate(se, "label", "label");
                    }
                } else {
                    Resource se = new Resource(Resource.Kind.SERVICE_ENTRY, dr.host);
                    dr.addSuccessor(se);
                    dr.propagate(se, "backPort", "frontPort");
                    dr.propagate(se, "label", "label");
                    tempResources.add(se);
                }
            }
        }

        // extend
        for (Resource resource : tempResources) {
            addResource(resource);
        }
    }

    private void extendGW(Resource gw) {
        ArrayList<Resource> tempResources = new ArrayList<>();

        // front
        Assert(gw.pred.isEmpty(), "Gateway should not have parents");

        // back
        if (gw.succ.isEmpty()) {
            ArrayList<Resource> VSs = getResource(Resource.Kind.VIRTUAL_SERVICE, gw.host);
            if (!VSs.isEmpty()) {
                for (Resource vs : VSs) {
                    gw.addSuccessor(vs);
                    gw.propagate(vs, "backPort", "frontPort");
                    gw.propagate(vs, "label", "label");
                }
            } else {
                Resource vs = new Resource(Resource.Kind.VIRTUAL_SERVICE, gw.host);
                gw.addSuccessor(vs);
                gw.propagate(vs, "backPort", "frontPort");
                gw.propagate(vs, "label", "label");
                tempResources.add(vs);
            }
        }

        // extend
        for (Resource resource : tempResources) {
            addResource(resource);
        }
    }

    private void extendSE(Resource se) {
        ArrayList<Resource> tempResources = new ArrayList<>();

        // front

        // back
        if (se.succ.isEmpty() || !maker.isExact(se.host)) {
            String service = maker.getService();
            ArrayList<Resource> PODs = getResource(Resource.Kind.POD, service + "-v1");
            PODs.addAll(getResource(Resource.Kind.POD, service + "-v2"));
            if (!PODs.isEmpty()) {
                for (Resource pod : PODs) {
                    se.addSuccessor(pod);
                    se.propagate(pod, "label", "label");
                }
            } else {
                Resource pod = new Resource(Resource.Kind.POD, service + "-v1");
                se.addSuccessor(pod);
                se.propagate(pod, "label", "label");
                tempResources.add(pod);
                pod = new Resource(Resource.Kind.POD, service + "-v2");
                se.addSuccessor(pod);
                se.propagate(pod, "label", "label");
                tempResources.add(pod);
            }
        }

        // extend
        for (Resource resource : tempResources) {
            addResource(resource);
        }
    }

    private void extendSVC(Resource svc) {
        ArrayList<Resource> tempResources = new ArrayList<>();

        // front

        // back
        if (svc.succ.isEmpty()) {
            ArrayList<Resource> PODs = getResource(Resource.Kind.POD, svc.host + "-v1");
            PODs.addAll(getResource(Resource.Kind.POD, svc.host + "-v2"));
            if (!PODs.isEmpty()) {
                for (Resource pod : PODs) {
                    svc.addSuccessor(pod);
                    svc.propagate(pod, "label", "label");
                }
            } else {
                Resource pod = new Resource(Resource.Kind.POD, svc.host + "-v1");
                svc.addSuccessor(pod);
                svc.propagate(pod, "label", "label");
                tempResources.add(pod);
                pod = new Resource(Resource.Kind.POD, svc.host + "-v2");
                svc.addSuccessor(pod);
                svc.propagate(pod, "label", "label");
                tempResources.add(pod);
            }
        }

        // extend
        for (Resource resource : tempResources) {
            addResource(resource);
        }
    }

    private void extendPOD(Resource pod) {
        ArrayList<Resource> tempResources = new ArrayList<>();
        int rand;

        // front
        if (pod.pred.isEmpty()) {
            rand = Rand.RandInt(2);

            ArrayList<Resource> SVCs = getResource(Resource.Kind.SERVICE, pod.host.substring(0, pod.host.length() - 3));
            if (!SVCs.isEmpty()) {
                for (Resource svc : SVCs) {
                    pod.addParent(svc);
                    pod.propagate(svc, "label", "label");
                }
            } else if (rand == 0) {
                Resource svc = new Resource(Resource.Kind.SERVICE, pod.host.substring(0, pod.host.length() - 3));
                pod.addParent(svc);
                pod.propagate(svc, "label", "label");
                tempResources.add(svc);
            }

            String host = maker.getHost();
            ArrayList<Resource> SEs = getResource(Resource.Kind.SERVICE_ENTRY, host);
            if (rand == 1) {
                if (!SEs.isEmpty()) {
                    for (Resource se : SEs) {
                        pod.addParent(se);
                        pod.propagate(se, "label", "label");
                    }
                } else {
                    Resource se = new Resource(Resource.Kind.SERVICE_ENTRY, host);
                    pod.addParent(se);
                    pod.propagate(se, "label", "label");
                    tempResources.add(se);
                }
            }
        }

        // back

        // extend
        for (Resource resource : tempResources) {
            addResource(resource);
        }
    }

    public void showAll() {
        System.out.println("abstract:");
        for (Resource resource : resources) {
            System.out.println(resource.kind + " " + resource.host);
        }
        System.out.println();
        System.out.println("details:");
        for (Resource resource : resources) {
            System.out.println(resource.kind + " " + resource.host);
            System.out.println("\tparents:");
            for (Resource parent : resource.pred) {
                System.out.println("\t" + parent.kind + " " + parent.host);
            }
            System.out.println("\tsuccessors:");
            for (Resource successor : resource.succ) {
                System.out.println("\t" + successor.kind + " " + successor.host);
            }
            System.out.println("\tannotations:");
            System.out.println("\tgateway: " + resource.gateway);
            System.out.println("\tfrontPort: " + resource.frontPort);
            System.out.println("\tbackPort: " + resource.backPort);
            System.out.println("\tsubset: " + resource.subset);
            System.out.println("\tlabel: " + resource.label);
        }
    }

    public ArrayList<Config> configs;

    private Config createConfig(Resource resource) {
        switch (resource.kind) {
            case VIRTUAL_SERVICE:
                VirtualService vs = new VirtualService();
                if (!resource.DELEGATE_SUCC) {
                    vs.hosts.add(new DomainHost(resource.host));
                }
                return vs;
            case DESTINATION_RULE:
                DestinationRule dr = new DestinationRule();
                dr.host = new DomainHost(resource.host);
                return dr;
            case GATEWAY:
                Gateway gw = new Gateway();
                Server server = new Server();
                server.hosts.add(new DomainHost(resource.host));
                gw.servers.add(server);
                return gw;
            case SERVICE_ENTRY:
                ServiceEntry se = new ServiceEntry();
                se.hosts.add(new DomainHost(resource.host));
                return se;
            case SERVICE:
                Service svc = new Service();
                svc.metadata.put("name", resource.host);
                return svc;
            case POD:
                Pod pod = new Pod();
                pod.metadata.put("name", resource.host);
                return pod;
            default:
                return null;
        }
    }

    public void fill() {
        for (int i = resources.size() - 1 ; i >= 0; -- i) {
            Resource resource = resources.get(i);
            if (resource.kind == Resource.Kind.POD || resource.kind == Resource.Kind.SERVICE || resource.kind == Resource.Kind.GATEWAY) {
                fillConfig(resource);
            }
        }
        for (int i = resources.size() - 1; i >= 0; -- i) {
            Resource resource = resources.get(i);
            if (resource.kind == Resource.Kind.DESTINATION_RULE || resource.kind == Resource.Kind.SERVICE_ENTRY) {
                fillConfig(resource);
            }
        }

        for (Resource resource : resources) {
            if (resource.kind == Resource.Kind.VIRTUAL_SERVICE) {
                fillConfig(resource);
            }
        }
    }

    public void fillConfig(Resource resource) {
        if (visited.get(resourceIndex(resource))) {
            return;
        }

        switch (resource.kind) {
            case VIRTUAL_SERVICE:
                VirtualService vs = (VirtualService) configs.get(resourceIndex(resource));
                fillVS(resource, vs);
                break;
            case DESTINATION_RULE:
                DestinationRule dr = (DestinationRule) configs.get(resourceIndex(resource));
                fillDR(resource, dr);
                break;
            case GATEWAY:
                Gateway gw = (Gateway) configs.get(resourceIndex(resource));
                fillGW(resource, gw);
                break;
            case SERVICE_ENTRY:
                ServiceEntry se = (ServiceEntry) configs.get(resourceIndex(resource));
                fillSE(resource, se);
                break;
            case SERVICE:
                Service svc = (Service) configs.get(resourceIndex(resource));
                fillSVC(resource, svc);
                break;
            case POD:
                Pod pod = (Pod) configs.get(resourceIndex(resource));
                fillPOD(resource, pod);
                break;
        }

        // if there is required field for resource, fill it
        if (resource.requiredField != null) {
            String field = resource.requiredField.getKey();
            Object value = resource.requiredField.getValue();
            Config config = configs.get(resourceIndex(resource));
            Util.putWithField(field, config, value);
        }
        visited.set(resourceIndex(resource), true);
        if (parameters.verbose)
            System.out.println("filled " + resource.kind + " " + resource.host);
    }

    private void fillVS(Resource resource, VirtualService vs) {
        // name
        vs.metadata.put("name", resource.DELEGATE_SUCC ? resource.delegate :maker.getName("vs"));

        ArrayList<String> gateways = new ArrayList<>();
        ArrayList<Integer> frontPorts = new ArrayList<>();
        HTTPMatchRequest rootMatch = null;

        for (Resource res: resource.pred) {
            if (!visited.get(resourceIndex(res))) {
                continue;
            }
            switch (res.kind) {
                case GATEWAY:
                    Gateway gw = (Gateway) configs.get(resourceIndex(res));
                    gateways.add(gw.metadata.get("name"));
                    frontPorts.add(gw.servers.get(0).port.number);
                    break;
                case SERVICE_ENTRY:
                    ServiceEntry se = (ServiceEntry) configs.get(resourceIndex(res));
                    for (Port port : se.ports) {
                        frontPorts.add(port.targetPort == -1 ? port.number : port.targetPort);
                    }
                    break;
                case VIRTUAL_SERVICE:
                    VirtualService delegateRoot = (VirtualService) configs.get(resourceIndex(res));
                    for (HTTPRoute route: delegateRoot.http) {
                        if (route.delegate != null) {
                            rootMatch = route.match.get(0);
                        }
                    }
            }
        }

        if (!gateways.isEmpty()) {
            vs.gateways.addAll(gateways);
            vs.gateways.add("mesh");
        }

        int rand = Rand.RandInt(resource.succ.size());

        for (Resource res: resource.succ) {
            Assert(res.kind == Resource.Kind.DESTINATION_RULE || res.kind == Resource.Kind.VIRTUAL_SERVICE,
                    "VirtualService should only have DestinationRule/VirtualService as successors");

            if (!visited.get(resourceIndex(res)) && res.kind == Resource.Kind.DESTINATION_RULE) {
                continue;
            }

            HTTPRoute route = new HTTPRoute();
            HTTPMatchRequest match = new HTTPMatchRequest();

            if (Rand.RandBool(30) && !parameters.gatewayAPI) {
                if (!resource.DELEGATE_SUCC) {
                    match.port = frontPorts.get(Rand.RandInt(frontPorts.size()));
                } else if (rootMatch != null) {
                    match.port = rootMatch.port;
                }
            }

            if (Rand.RandBool(50)) {
                if (!resource.DELEGATE_SUCC) {
                    HTTPMatchRequest.StringMatch.MatchType type = Rand.RandBool(50) ?
                            HTTPMatchRequest.StringMatch.MatchType.EXACT :
                            HTTPMatchRequest.StringMatch.MatchType.PREFIX;
                    match.uri = new HTTPMatchRequest.StringMatch(type, maker.uris.get(Rand.RandInt(maker.uris.size())));
                } else if (rootMatch != null && rootMatch.uri != null) {
                    if (rootMatch.uri.type == HTTPMatchRequest.StringMatch.MatchType.PREFIX) {
                        match.uri = new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.PREFIX,
                                rootMatch.uri.value + (rootMatch.uri.value.equals("/")? "": "/") + "delegate");
                    }
                }
            }

            if (match.uri == null) {
                int headerNum = Rand.RandInt(3);
                for (int i = 0; i < headerNum; ++i) {
                    String key = maker.getHeaderKey();
                    if (!resource.DELEGATE_SUCC) {
                        String value = maker.getHeaderValue();
                        match.headers.put(key, new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, value));
                    } else if (rootMatch != null && rootMatch.headers.get(key) != null) {
                        match.headers.put(key, rootMatch.headers.get(key));
                    }
                }
            }

            route.match.add(match);

            // delegate: skip the destination
            if (res.kind == Resource.Kind.VIRTUAL_SERVICE) {
                Delegate delegate = new Delegate();
                delegate.name = new DomainHost(resource.delegate);
                route.delegate = delegate;
                vs.http.add(route);
                continue;
            }

            DestinationRule dr = (DestinationRule) configs.get(resourceIndex(res));

            ArrayList<String> subsets = new ArrayList<>();
            ArrayList<Integer> backPorts = new ArrayList<>();

            for (Subset subset: dr.subsets) {
                subsets.add(subset.name);
            }

            for (Resource res1: res.succ) {
                if (!visited.get(resourceIndex(res1))) {
                    continue;
                }
                switch (res1.kind) {
                    case SERVICE:
                        Service svc = (Service) configs.get(resourceIndex(res1));
                        backPorts.addAll(svc.ports.stream().map(port -> port.port).toList());
                        break;
                    case SERVICE_ENTRY:
                        ServiceEntry se = (ServiceEntry) configs.get(resourceIndex(res1));
                        backPorts.addAll(se.ports.stream().map(port -> port.number).toList());
                        break;
                }
            }

            RouteDestination dest = new RouteDestination();
            Destination destination = new Destination();
            destination.host = dr.host;
            destination.subset = subsets.isEmpty() || parameters.gatewayAPI ? null: subsets.get(Rand.RandInt(subsets.size()));
            Wrapped.PortSelector port = new Wrapped.PortSelector();
            port.number = backPorts.get(Rand.RandInt(backPorts.size()));
            destination.port = port;
            dest.destination = destination;

            route.route.add(dest);
            vs.http.add(route);
        }
    }

    private void fillDR(Resource resource, DestinationRule dr) {
        // name
        dr.metadata.put("name", maker.getName("dr"));

        int minSubsetNum = resource.SUBSET_LABEL ? 1 : 0;
        int subsetNum = Rand.RandInt(minSubsetNum, 3);
        ArrayList<String> existingSubsets = new ArrayList<>();
        for (int i = 0; i < subsetNum; ++ i) {
            Subset subset = new Subset();
            subset.name = "v" + (i + 1);
            existingSubsets.add(subset.name);
            subset.labels.put("version", "v" + (i + 1));
            dr.subsets.add(subset);
        }

        if (resource.label != null && resource.SUBSET_LABEL) {
            dr.subsets.get(Rand.RandInt(subsetNum)).labels.put("myLabel", resource.label);
        }

        if (resource.subset != null && !existingSubsets.contains(resource.subset)) {
            Subset subset = new Subset();
            subset.name = resource.subset;
            subset.labels.put("version", resource.subset);
            dr.subsets.add(subset);
        }

        ArrayList<String> services = new ArrayList<>();
        for (Resource res: resource.succ) {
            if (!visited.get(resourceIndex(res))) {
                continue;
            }
            switch (res.kind) {
                case SERVICE:
                    Service svc = (Service) configs.get(resourceIndex(res));
                    services.add(svc.metadata.get("name"));
                    break;
                case SERVICE_ENTRY:
                    ServiceEntry se = (ServiceEntry) configs.get(resourceIndex(res));
                    if (se.workloadSelector != null) {
                        services.add(se.workloadSelector.labels.get("app"));
                    }
                    break;
            }
        }

        // todo: workloadSelector is associated with source labels
    }

    private void fillGW(Resource resource, Gateway gw) {
        // name
        String gwName = resource.gateway != null ? resource.gateway : maker.getName("gw");
        gw.metadata.put("name", gwName);

        Port port = new Port();
        port.number = resource.backPort == null ? maker.getPort() : resource.backPort;
        port.protocol = "HTTP";
        port.name = maker.getName("port");
        // debug note: Gateway should not have targetPort
        gw.servers.get(0).port = port;

//        if (resource.label != null) {
//            gw.selector.put("myLabel", resource.label);
//        }
    }

    private void fillSE(Resource resource, ServiceEntry se) {
        // name
        se.metadata.put("name", maker.getName("se"));

        String app = null;
        for (Resource res: resource.succ) {
            if (!visited.get(resourceIndex(res))) {
                continue;
            }
            if (res.kind == Resource.Kind.POD) {
                Pod pod = (Pod) configs.get(resourceIndex(res));
                String podName = (String) pod.metadata.get("name");
                app = podName.substring(0, podName.length() - 3);
                break;
            }
        }

        // if ServiceEntry is the backend of routing, we should have a workloadSelector
        if (!resource.pred.isEmpty()) {
            app = maker.getService();
        }

        ArrayList<Integer> portNumbers;
        TreeSet<Integer> avoidance = new TreeSet<>();
        if (resource.frontPort != null) {
            avoidance.add(resource.frontPort);
        }
        if (resource.backPort != null) {
            avoidance.add(resource.backPort);
        }
        portNumbers = maker.getPorts(2, avoidance);

        int remainingPorts = 2;
        // debug note: we should make two port for both frontPort and backPort
        if (resource.frontPort != null) {
            remainingPorts -= 1;
            Port port = new Port();
            port.number = resource.frontPort != null ? resource.frontPort : portNumbers.get(remainingPorts);
            port.protocol = "HTTP";
            port.name = maker.getName("port");
            if (app != null) {
                port.targetPort = 9080;
            }
            se.ports.add(port);
        }
        if (resource.backPort != null) {
            remainingPorts -= 1;
            Port port = new Port();
            port.number = resource.backPort == null ? portNumbers.get(remainingPorts) : resource.backPort;
            port.protocol = "HTTP";
            port.name = maker.getName("port");
            se.ports.add(port);
        }

        for (int i = 0; i < remainingPorts; ++ i) {
            Port port = new Port();
            port.number = portNumbers.get(i);
            port.protocol = "HTTP";
            port.name = maker.getName("port");
            if (app != null) {
                port.targetPort = 9080;
            }
            se.ports.add(port);
        }

        if (app != null) {
            WorkloadSelector selector = new WorkloadSelector();
            selector.labels.put("app", app);
            if (Rand.RandBool(10) && !parameters.gatewayAPI) {
                selector.labels.put("version", "v" + Rand.RandInt(1, 3));
            }
            if (resource.label != null) {
                selector.labels.put("myLabel", resource.label);
            }
            se.workloadSelector = selector;
        }

        if (app != null) {
            se.resolution = "STATIC";
        } else {
            se.resolution = "DNS";
        }
    }

    private void fillSVC(Resource resource, Service svc) {
        svc.selector.put("app", resource.host);
        if (resource.label != null) {
            svc.selector.put("myLabel", resource.label);
        }

        int portCnt = 0;

        TreeSet<Integer> avoidance = new TreeSet<>();
        if (resource.frontPort != null) {
            avoidance.add(resource.frontPort);
        }
        ArrayList<Integer> portNumbers = maker.getPorts(2, avoidance);

        int remainingPorts = Rand.RandInt(1, 3);
        if (resource.frontPort != null || resource.backPort != null) {
            remainingPorts -= 1;
            KPort port = new KPort();
            port.port = resource.frontPort != null ? resource.frontPort : portNumbers.get(remainingPorts);
            port.name = "http-" + portCnt;
            portCnt++;
            port.targetPort = resource.backPort != null ? resource.backPort : 9080;
            svc.ports.add(port);
        }
        for (int i = 0; i < remainingPorts; ++ i) {
            KPort port = new KPort();
            port.port = portNumbers.get(i);
            port.name = "http-" + portCnt;
            portCnt++;
            port.targetPort = 9080;
            svc.ports.add(port);
        }
    }

    private void fillPOD(Resource resource, Pod pod) {
        pod.metadata.put("name", resource.host);
        pod.metadata.put("labels", new LinkedHashMap<>(Map.of(
                "app", resource.host.substring(0, resource.host.length() - 3),
                "version", resource.host.substring(resource.host.length() - 2))));
        if (resource.label != null) {
            ((Map<String, String>) pod.metadata.get("labels")).put("myLabel", resource.label);
        }

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
//        LinkedHashMap<String, Object> volumeMountSender = new LinkedHashMap<>();
//        volumeMounts.add(volumeMountSender);
//        volumeMountSender.put("name", "sender");
//        volumeMountSender.put("mountPath", "/app/sender");

        ArrayList<Object> volumes = pod.volumes;
        LinkedHashMap<String, Object> volumeConfig = new LinkedHashMap<>();
        volumes.add(volumeConfig);
        volumeConfig.put("name", "config");
        volumeConfig.put("hostPath", new LinkedHashMap<>(Map.of("path", "/app/config")));
//        LinkedHashMap<String, Object> volumeSender = new LinkedHashMap<>();
//        volumes.add(volumeSender);
//        volumeSender.put("name", "sender");
//        volumeSender.put("hostPath", new LinkedHashMap<>(Map.of("path", "/app/sender")));
    }

    // note that the merge operation will destroy the mapping relation
    // between resources and configs by index
    public void merge() {
        mergeGW();
        mergeDelegateVS();
    }

    private void mergeGW() {
        HashMap<String, Gateway> map = new HashMap<>();
        ArrayList<Config> toRemove = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof Gateway) {
                Gateway gw = (Gateway) config;
                String name = gw.metadata.get("name");
                if (map.get(name) != null) {
                    Gateway target = map.get(name);
                    target.servers.addAll(gw.servers);
                    if (target.selector.get("myLabel") != null && gw.selector.get("myLabel") != null) {
                        Assert(target.selector.get("myLabel").equals(gw.selector.get("myLabel")),
                                "Gateways merged should have the same label");
                    }
                    toRemove.add(gw);
                } else {
                    map.put(name, gw);
                }
            }
        }
        configs.removeAll(toRemove);
    }

    private void mergeDelegateVS() {
        HashMap<String, VirtualService> map = new HashMap<>();
        ArrayList<Config> toRemove = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof VirtualService) {
                VirtualService vs = (VirtualService) config;
                String name = vs.metadata.get("name");
                if (map.get(name) != null) {
                    VirtualService target = map.get(name);
                    target.http.addAll(vs.http);
                    toRemove.add(vs);
                } else {
                    map.put(name, vs);
                }
            }
        }
        configs.removeAll(toRemove);
    }

    public void sort() {
        ArrayList<Config> tempConfigs = new ArrayList<>();
        Function<Class<?>, Void> reverse = (Class<?> cls) -> {
            if (cls == VirtualService.class) {
                for (Config config: configs) {
                    if (config instanceof VirtualService) {
                        tempConfigs.add(config);
                    }
                }
                return null;
            }
            for (int i = configs.size() - 1; i >= 0; -- i) {
                Config config = configs.get(i);
                if (cls.isInstance(config)) {
                    tempConfigs.add(config);
                }
            }
            return null;
        };
        reverse.apply(VirtualService.class);
        reverse.apply(DestinationRule.class);
        reverse.apply(Gateway.class);
        reverse.apply(ServiceEntry.class);
        reverse.apply(Service.class);
        reverse.apply(Pod.class);

        configs = tempConfigs;
    }

    public void clear() {
        resources.clear();
        configs.clear();
        visited.clear();
        maker = new RationalMaker();
    }
}
