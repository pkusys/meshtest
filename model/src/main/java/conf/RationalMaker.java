package conf;

import utils.Rand;

import java.util.*;

public class RationalMaker {
    public ArrayList<String> hosts = new ArrayList<>(List.of(
            "*", "*.bookinfo.com", "*.example.com", "www.bookinfo.com", "www.example.com"));
    public ArrayList<String> services = new ArrayList<>(List.of(
            "productpage", "ratings"));

    public String getHost() {
        return hosts.get(Rand.RandInt(1, hosts.size()));
    }

    public String getService() {
        return services.get(Rand.RandInt(services.size()));
    }

    public ArrayList<String> getServices(int number) {
        BitSet bitSet = new BitSet(services.size());
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < number; ++ i) {
            int index = Rand.RandInt(services.size());
            while (bitSet.get(index)) {
                index = Rand.RandInt(services.size());
            }
            bitSet.set(index);
            result.add(services.get(index));
        }
        return result;
    }

    public ArrayList<String> extendHost(String host) {
        ArrayList<String> result = new ArrayList<>();
        if (isService(host)) {
            result.add("*");
            result.add(host);
            result.add(host + ".default.svc.cluster.local");
        } else {
            if (host.equals("*")) {
                result.addAll(hosts);
            } else {
                result.add("*");
                String suffix = host.substring(host.indexOf(".") + 1);
                result.add("*." + suffix);
                result.add("www." + suffix);
            }
        }
        return result;
    }

    public String exactHost(String host) {
        if (host.equals("*")) {
            return exactHost(getHost());
        }
        if (isService(host)) {
            return host;
        } else {
            return "www." + host.substring(host.indexOf(".") + 1);
        }
    }

    public boolean isService(String host) {
        return !host.contains(".") && !host.equals("*");
    }

    public boolean isExact(String host) {
        return !host.contains("*");
    }

    public HashMap<String, Integer> resourceCounter = new HashMap<>(Map.of(
            "vs", 0, "dr", 0, "gw", 0,
            "se", 0, "svc", 0, "pod", 0,
            "port", 0));

    public String getName(String resource) {
        int index = resourceCounter.get(resource);
        resourceCounter.put(resource, index + 1);
        return resource + "-" + index;
    }

    public ArrayList<String> uris = new ArrayList<>(List.of(
            "/", "/productpage", "/ratings", "/details"));

    public String getUri() {
        return uris.get(Rand.RandInt(uris.size()));
    }

    public ArrayList<String> headerKeys = new ArrayList<>(List.of(
            "header1", "header2", "header3"));

    public String getHeaderKey() {
        return headerKeys.get(Rand.RandInt(headerKeys.size()));
    }

    public ArrayList<String> headerValues = new ArrayList<>(List.of(
            "value1", "value2", "value3"));

    public String getHeaderValue() {
        return headerValues.get(Rand.RandInt(headerValues.size()));
    }

    public ArrayList<Integer> ports = new ArrayList<>(List.of(
            80, 8080, 9080, 9090));

    public int getPort() {
        return ports.get(Rand.RandInt(ports.size()));
    }

    public ArrayList<Integer> getPorts(int number, Set<Integer> avoidance) {
        TreeSet<Integer> set = new TreeSet<>();
        if (avoidance != null) {
            set.addAll(avoidance);
        }
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < number; ++ i) {
            int index = Rand.RandInt(ports.size());
            int port = ports.get(index);
            while (set.contains(port)) {
                index = Rand.RandInt(ports.size());
                port = ports.get(index);
            }
            set.add(port);
            result.add(port);
        }
        return result;
    }

    public ArrayList<String> gateways = new ArrayList<>(List.of(
            "gateway", "gateway-1", "gateway-2"));

    public String getGateway() {
        return gateways.get(Rand.RandInt(gateways.size()));
    }

    public ArrayList<String> labels = new ArrayList<>(List.of(
            "Alice", "Bob", "Trudy"));

    public String getLabel() {
        return labels.get(Rand.RandInt(labels.size()));
    }

    public ArrayList<String> delegates = new ArrayList<>(List.of(
            "delegate", "delegate-1", "delegate-2"));

    public String getDelegate() {
        return delegates.get(Rand.RandInt(delegates.size()));
    }
}
