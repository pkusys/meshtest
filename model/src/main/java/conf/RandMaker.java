package conf;

import frontend.Config;
import frontend.istio.component.*;
import frontend.istio.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import utils.Rand;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RandMaker {
    // todo: @Tianshuo
    // 1. add more fields
    // 2. make the fields valid based on spec
    // 3. value should be more heuristic
    private static Integer vsCnt = 0;

    private final ArrayList<String> Gateway = new ArrayList<>(List.of("mesh", "gateway1", "gateway2"));
    public VirtualService makeVirtualService() {
        VirtualService vs = new VirtualService();

        // add metadata
        vsCnt++;
        vs.metadata.put("name", "vs-" + vsCnt);

        // add host, random number of domain host
        vs.addHosts(makeHosts("domain", (int)(Rand.RandDouble() * 2) + 1)
                .stream().map(host -> (DomainHost) host).collect(Collectors.toList()));

        // add gateway
        int gwNum = (int)(Rand.RandDouble() * 3);
        if (gwNum > 0) {
            vs.addGateways(List.of(Gateway.get((int)(Rand.RandDouble() * Gateway.size()))));
        }

        // add http route
        int httpNum = (int)(Rand.RandDouble() * 3);
        if (httpNum == 0) {
            httpNum = (int)(Rand.RandDouble() * 2);
        }
        vs.addHttp(IntStream.range(0, httpNum).mapToObj(i -> makeHTTPRoute()).collect(Collectors.toList()));

        int tcpNum = (int)(Rand.RandDouble() * 2);
        if (tcpNum > 0) {
             vs.addTcp(IntStream.range(0, tcpNum).mapToObj(i -> makeTCPRoute()).collect(Collectors.toList()));
        }

        return vs;
    }

    private static Integer drCnt = 0;
    public DestinationRule makeDestinationRule() {
        DestinationRule dr = new DestinationRule();
        // add metadata
        drCnt++;
        dr.metadata.put("name", "dr-" + drCnt);

        // add domain host
        dr.host = (DomainHost) makeHosts("domain", 1).get(0);
        // add subsets
        int subsetNum = (int)(Rand.RandDouble() * 3);
        dr.addSubsets(IntStream.range(0, subsetNum).mapToObj(i -> makeSubset()).collect(Collectors.toList()));

        return dr;
    }

    private static Integer seCnt = 0;
    public ServiceEntry makeServiceEntry() {
        ServiceEntry se = new ServiceEntry();
        // add metadata
        seCnt++;
        se.metadata.put("name", "se-" + seCnt);

        double rand = Rand.RandDouble();
        if (rand < 0.4) { // host only
            int hostNum = (int)(Rand.RandDouble() * 2) + 1;
            se.addHosts(makeHosts("domain", hostNum).stream().map(host -> (DomainHost) host).collect(Collectors.toList()));
        } else if (rand < 0.7) { // address only
            int hostNum = (int)(Rand.RandDouble() * 2) + 1;
            se.addAddresses(makeHosts("ip", hostNum).stream().map(host -> (IPHost) host).collect(Collectors.toList()));
        } else {
            int hostNum = (int)(Rand.RandDouble() * 2) + 1;
            se.addHosts(makeHosts("domain", hostNum).stream().map(host -> (DomainHost) host).collect(Collectors.toList()));
            hostNum = (int)(Rand.RandDouble() * 2) + 1;
            se.addAddresses(makeHosts("ip", hostNum).stream().map(host -> (IPHost) host).collect(Collectors.toList()));
        }

        // add ports
        int portNum = (int)(Rand.RandDouble() * 3);
        se.addPorts(IntStream.range(0, portNum).mapToObj(i -> makePort()).collect(Collectors.toList()));

        // add selector
        if (Rand.RandDouble() < 0.1) {
            Map<String, String> labels = makeLabels();
            se.workloadSelector = new WorkloadSelector();
            se.workloadSelector.labels.putAll(labels);
        }

        return se;
    }

    static Integer gwCnt = 0;
    public frontend.istio.Gateway makeGateway() {
        Gateway gw = new Gateway();
        gwCnt++;
        gw.metadata.put("name", "gw-" + gwCnt);
        // add server
        int svNum = (int)(Rand.RandDouble() * 2) + 1;
        gw.addServers(IntStream.range(0, svNum).mapToObj(i -> makeGwServer()).collect(Collectors.toList()));

        // add selector
        if (Rand.RandDouble() < 0.1) {
            Map<String, String> labels = makeLabels();
            gw.selector.putAll(labels);
        }

        return gw;
    }

    static Integer svcCnt = 0;
    private final ArrayList<String> appNames = new ArrayList<>(List.of("app1", "app2", "app3", "app4"));
    public Service makeService() {
        Service svc = new Service();
        svcCnt++;
        svc.metadata.put("name", "svc-" + svcCnt);
        svc.ports.addAll(IntStream.range(0, (int)(Rand.RandDouble() * 3)).mapToObj(i -> makeK8sPort()).toList());
        svc.selector.put("app", appNames.get((int)(Rand.RandDouble() * appNames.size())));
        return svc;
    }

    static Integer podCnt = 0;
    public Pod makePod() {
        Pod pod = new Pod();
        // load Pod template from template/pod.yaml
        try {
            // model should be root dir of the project
            InputStream fis = new FileInputStream("template/pod.yaml");
            Yaml yaml = new Yaml();
            Map<String, Object> yamlConf = yaml.load(fis);
            pod.fromYaml(yamlConf);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // add name
        podCnt++;
        pod.metadata.put("name", "pod-" + podCnt);

        // add label
        LinkedHashMap<String, String> labelsMap = new LinkedHashMap();
        labelsMap.put("app", appNames.get((int)(Rand.RandDouble() * appNames.size())));
        labelsMap.put("version", Subsets.get((int)(Rand.RandDouble() * Subsets.size())));
        pod.metadata.put("labels", labelsMap);
        return pod;
    }

    private KPort makeK8sPort() {
        KPort kport = new KPort();
        kport.port = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));
        kport.targetPort = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));

        return kport;
    }

    private Server makeGwServer() {
        Server sv = new Server();
        sv.addHosts(makeGwServerHosts());
        sv.port = makePort();
        return sv;
    }

    private ArrayList<DomainHost> makeGwServerHosts() {
        ArrayList<DomainHost> hosts = new ArrayList<>();
        int hostNum = (int)(Rand.RandDouble() * 2);
        final ArrayList<String> DomainHostList = new ArrayList<>(List.of(
                "*", "productpage.default.svc.cluster.local", "www.bookinfo.com", "1.1.1.1", "test"));
        for (int i = 0; i < hostNum; i++) {
            String hostname = DomainHostList.get((int)(Rand.RandDouble() * DomainHostList.size()));
            // if contains, generate a new one
            String finalHostname = hostname;
            while (hosts.stream().anyMatch(host -> Objects.equals(host.domain, finalHostname))) {
                hostname = DomainHostList.get((int)(Rand.RandDouble() * DomainHostList.size()));
            }
            // it is okay if the host name is duplicated, just want to make it less likely
            hosts.add(new DomainHost(hostname));
        }

        hosts.addAll(makeHosts("domain", hostNum).stream().map(host -> (DomainHost) host).toList());
        return hosts;
    }

    private final ArrayList<String> IPHostList = new ArrayList<>(List.of("10.244.1.1", "10.244.2.2", "162.105.89.11"));
    private final ArrayList<String> DomainHostList = new ArrayList<>(List.of(
            "productpage.default.svc.cluster.local", "www.baidu.com", "www.bookinfo.com"));
    private ArrayList<Host> makeHosts(String type, Integer num) {
        ArrayList<Host> res = new ArrayList<>();
        if (type.equalsIgnoreCase("ip")) {
            for (int i = 0; i < num; i++) {
                res.add(new IPHost(IPHostList.get((int)(Rand.RandDouble() * IPHostList.size()))));
            }
        } else if (type.equalsIgnoreCase("domain")) {
            for (int i = 0; i < num; i++) {
                res.add(new DomainHost(DomainHostList.get((int)(Rand.RandDouble() * DomainHostList.size()))));
            }
        } else if (type.equalsIgnoreCase("any")) {
            for (int i = 0; i < num; i++) {
                if (Rand.RandDouble() < 0.5) {
                    res.add(new IPHost(IPHostList.get((int)(Rand.RandDouble() * IPHostList.size()))));
                } else {
                    res.add(new DomainHost(DomainHostList.get((int)(Rand.RandDouble() * DomainHostList.size()))));
                }
            }
        } else {
            throw new RuntimeException("Unknown host type: " + type);
        }
        return res;
    }

    private HTTPRoute makeHTTPRoute() {
        HTTPRoute route = new HTTPRoute();
        // add match
        route.addMatch(makeHTTPMatch());
        // add route
        route.addRoute(makeHTTPRouteDestination());
        // add rewrite
        if (Rand.RandDouble() < 0.3) {
            route.rewrite = makeRewrite();
        }
        // add headers
        if (Rand.RandDouble() < 0.3) {
            route.headers = makeHeaders();
        }
        return route;
    }

    private String makeURI() {
        final ArrayList<String> URIList = new ArrayList<>(List.of("", "/login", "/logout", "/productpage"));
        return URIList.get((int)(Rand.RandDouble() * URIList.size()));
    }


    private final ArrayList<String> HTTPHeaderKeys = new ArrayList<>(List.of("header-0", "header-1", "method",
            "authority", "uri", "scheme"));
    private final ArrayList<String> HTTPHeaderValues = new ArrayList<>(List.of("", "value-0", "value-1"));
    private static int HTTPMatchCnt = 0;
    private HTTPMatchRequest makeHTTPMatch() {
        HTTPMatchRequest match = new HTTPMatchRequest();
        HTTPMatchCnt++;
        match.name = "match-" + HTTPMatchCnt;

        // randomly select if port
        if (Rand.RandDouble() < 0.5) {
            match.port = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));
        }

        // randomly select if uri
        if (Rand.RandDouble() < 0.5) {
            HTTPMatchRequest.StringMatch.MatchType type = Rand.RandDouble() < 0.5 ?
                    HTTPMatchRequest.StringMatch.MatchType.EXACT : HTTPMatchRequest.StringMatch.MatchType.PREFIX;
            match.uri = new HTTPMatchRequest.StringMatch(type, makeURI());
        }

        // randomly select if headers
        if (Rand.RandDouble() < 0.5) {
            match.headers = new LinkedHashMap<>();
            int headerNum = (int)(Rand.RandDouble() * 2);
            for (int i = 0; i < headerNum; i++) {
                String key = HTTPHeaderKeys.get((int)(Rand.RandDouble() * HTTPHeaderKeys.size()));
                String value = HTTPHeaderValues.get((int)(Rand.RandDouble() * HTTPHeaderValues.size()));
                match.headers.put(key, new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, value));
            }
        }

        return match;
    }

    final private ArrayList<String> Subsets = new ArrayList<>(List.of("v1", "v2"));
    private RouteDestination makeHTTPRouteDestination() {
        RouteDestination route = new RouteDestination();
        // add destination
        route.destination = new Destination();
        route.destination.host = (DomainHost) makeHosts("domain", 1).get(0);
        // add subset
        if (Rand.RandDouble() < 0.3) {
            route.destination.subset = Subsets.get((int)(Rand.RandDouble() * Subsets.size()));
        }
        return route;
    }

    private Subset makeSubset() {
        Subset subset = new Subset();
        subset.name = Subsets.get((int)(Rand.RandDouble() * Subsets.size()));
        subset.labels = new LinkedHashMap<>();
        subset.labels.put("version", subset.name);
        return subset;
    }

    private final ArrayList<String> DelegateVSList = new ArrayList<>(List.of("delegate-vs1", "delegate-vs2"));

    private Delegate makeDelegate() {
        Delegate delegate = new Delegate();
        delegate.name = new DomainHost(DelegateVSList.get((int)(Rand.RandDouble() * DelegateVSList.size())));
        return delegate;
    }

    private HTTPRewrite makeRewrite() {
        HTTPRewrite rewrite = new HTTPRewrite();

        if (Rand.RandDouble() < 0.5) {
            rewrite.uri = makeURI();
        }

        if (Rand.RandDouble() < 0.5) {
            rewrite.authority = (DomainHost) makeHosts("domain", 1).get(0);
        }

        return rewrite;
    }

    private Headers makeHeaders() {
        Headers headers = new Headers();
        headers.request = new Headers.HeaderOperations();

        int headerNum = (int)(Rand.RandDouble() * 3);

        if (headerNum == 0) {
            return null;
        }

        for (int i = 0; i < headerNum; i++) {
            String key = HTTPHeaderKeys.get((int)(Rand.RandDouble() * HTTPHeaderKeys.size()));
            String value = HTTPHeaderValues.get((int)(Rand.RandDouble() * HTTPHeaderValues.size()));
            double rand = Rand.RandDouble();
            if (rand < 0.3) {
                headers.request.add.put(key, value);
            } else if (rand < 0.7) {
                headers.request.set.put(key, value);
            } else {
                headers.request.remove.add(key);
            }
        }

        return headers;
    }

    // only support label app and version
    private Map<String, String> makeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        double rand = Rand.RandDouble();
        if (rand < 0.4) {
            // only app
            labels.put("app", appNames.get((int)(Rand.RandDouble() * appNames.size())));
        } else if (rand < 0.6) {
            // only version
            labels.put("version", Subsets.get((int)(Rand.RandDouble() * Subsets.size())));
        } else {
            // both
            labels.put("app", appNames.get((int)(Rand.RandDouble() * appNames.size())));
            labels.put("version", Subsets.get((int)(Rand.RandDouble() * Subsets.size())));
        }

        return labels;
    }

    private TCPRoute makeTCPRoute() {
        TCPRoute route = new TCPRoute();
        // add match
        route.addMatch(makeTCPMatch());
        // add route
        route.addRoute(makeTCPRouteDestination());
        return route;
    }

    private L4MatchAttributes makeTCPMatch() {
        L4MatchAttributes match = new L4MatchAttributes();
        // randomly select if port
        if (Rand.RandDouble() < 0.5) {
            match.port = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));
        }
        return match;
    }

    private RouteDestination makeTCPRouteDestination() {
        RouteDestination route = new RouteDestination();
        // add destination
        route.destination = new Destination();
        route.destination.host = (DomainHost) makeHosts("domain", 1).get(0);
        return route;
    }

    final ArrayList<Integer> portNumbers = new ArrayList<>(List.of(10000, 10001, 10002, 10003, 10004, 10005));
    static Integer portCnt = 0;
    private Port makePort() {
        Port port = new Port();

        // metadata
        portCnt++;
        port.name = "port-" + portCnt;

        // number
        port.number = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));

        // protocol
        double rand = Rand.RandDouble();
        if (rand < 0.4) {
            port.protocol = "HTTP";
        } else if (rand < 0.8) {
            port.protocol = "TCP";
        } else {
            port.protocol = "TLS";
        }

        // target port
        if (Rand.RandDouble() < 0.5) {
            port.targetPort = portNumbers.get((int)(Rand.RandDouble() * portNumbers.size()));
        }

        return port;
    }

    public static void main(String[] args) {
        RandMaker maker = new RandMaker();
        VirtualService vs = maker.makeVirtualService();
        DestinationRule dr = maker.makeDestinationRule();
        ServiceEntry se = maker.makeServiceEntry();
        Gateway gw = maker.makeGateway();
        Service svc = maker.makeService();
        Pod pod = maker.makePod();

        ArrayList<Object> conf = new ArrayList<>();
        Config.isResource = true;
        conf.add(vs.toYaml());
        conf.add(dr.toYaml());
        conf.add(se.toYaml());
        conf.add(gw.toYaml());
        conf.add(svc.toYaml());
        conf.add(pod.toYaml());

        // dump yaml to file
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml prettyYaml = new Yaml(options);


        try (FileWriter writer = new FileWriter("example.yaml")) {
            // dump to file with dumpAll
            prettyYaml.dumpAll(conf.iterator(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
