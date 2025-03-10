package dfe;

import frontend.Config;
import frontend.istio.component.DomainHost;
import frontend.istio.component.HTTPRoute;
import frontend.istio.component.Server;
import frontend.istio.*;
import utils.Rand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static utils.AssertionHelper.Assert;

/**
 * Utility functions in DFE.Its namespace is managed
 * within an instance, instead of static way. Includes:
 * <ul>
 *     <li>Generate values for required fields.</li>
 * </ul>
 */
public class Util {
    /**
     * Allocator for specified elements.
     * @param <E> Type of elements.
     */
    private static class Allocator<E> {
        private final boolean isRandom;
        private final ArrayList<E> elements;
        private Integer index;

        @SafeVarargs
        public Allocator(boolean isRandom, E... elements) {
            this.isRandom = isRandom;
            this.elements = new ArrayList<>();
            Collections.addAll(this.elements, elements);
            this.index = 0;
        }

        public E next() {
            if (isRandom) {
                return elements.get(Rand.RandInt(elements.size()));
            } else {
                Assert(index < elements.size(), "Allocator out of bound.");
                return elements.get(index++);
            }
        }

        public int size() {
            return elements.size();
        }
    }

    private static class Counter {
        private final HashMap<String, Integer> counters;

        public Counter() {
            this.counters = new HashMap<>();
        }

        public String next(String key) {
            if (!counters.containsKey(key)) {
                counters.put(key, 0);
            }
            Integer count = counters.get(key);
            counters.put(key, count + 1);
            return key + "-" + count;
        }
    }

    // allocators and counter
    private final Allocator<String> hostSeeds = new Allocator<>(
            true,
            "example", "bookinfo"
    );
    private final Allocator<String> serviceNames = new Allocator<>(
            false,
            "productpage", "ratings", "details", "reviews", "httpbin"
    );
    private final Allocator<Integer> ports = new Allocator<>(
            true,
            80, 443, 8080, 9080, 9090
    );
    private final Allocator<String> apps = new Allocator<>(
            true,
            "details", "reviews", "httpbin"
    );
    private final Counter counter = new Counter();

    // generate functions
    public String getHost() {
        String hostSeed = hostSeeds.next();
        String prefix = Rand.RandBool(50) ? "www" : "*";
        return prefix + "." + hostSeed + ".com";
    }

    public String getExactHost() {
        return "www." + hostSeeds.next() + ".com";
    }

    public boolean isExact(String host) {
        return !host.contains("*");
    }

    public String randomChangeHost(String host) {
        if (Rand.RandBool(50)) {
            return host.replace("*", "www");
        } else {
            return host.replace("www", "*");
        }
    }

    public String getService() {
        return serviceNames.next();
    }

    public String getApp() {
        return apps.next();
    }

    public Integer getPort() {
        return ports.next();
    }

    // This method allocates ports that don't conflict.
    public ArrayList<Integer> getPorts(int num) {
        Assert(num > 0 && num < ports.size(),
                "Invalid number of ports to allocate.");
        ArrayList<Integer> result = new ArrayList<>();
        while (result.size() < num) {
            Integer port = ports.next();
            if (!result.contains(port)) {
                result.add(port);
            }
        }
        return result;
    }

    public Integer getPodPort() {
        return 9080;
    }

    public String getName(String key) {
        return counter.next(key);
    }

    // Not sure about the existence of method and scheme.
    public enum MemberType {
        URI_EXACT,
        URI_PREFIX,
        AUTHORITY,
        PORT,
        HEADER,
        WITHOUT_HEADER,
    }

    public ArrayList<MemberType> getMemberTypes(int memberNum) {
        Assert(memberNum > 0 && memberNum < MemberType.values().length,
                "Invalid number of member types to allocate.");
        ArrayList<MemberType> result = new ArrayList<>();
        while (result.size() < memberNum) {
            MemberType memberType = MemberType.values()[Rand.RandInt(MemberType.values().length)];
            if (!result.contains(memberType)) {
                result.add(memberType);
            }
        }
        return result;
    }

    private Allocator<String> uris = new Allocator<>(
            true,
            "/", "/productpage", "/ratings", "/details"
    );

    private Allocator<Map.Entry<String, String>> ordinaryHeaders = new Allocator<>(
            true,
            Map.entry("header1", "value1"),
            Map.entry("header2", "value2"),
            Map.entry("header3", "value3")
    );

    private Allocator<String> pseudoHeaders = new Allocator<>(
            true,
            ":path", ":scheme", ":method", ":authority"
    );

    public String getUri() {
        return uris.next();
    }

    public Map.Entry<String, String> getHeader() {
        if (Rand.RandBool(80)) {
            return ordinaryHeaders.next();
        } else {
            String key = pseudoHeaders.next();
            String value = switch (key) {
                case ":path" -> getUri();
                case ":scheme" -> "http";
                case ":method" -> "GET";
                case ":authority" -> getExactHost();
                default -> "";
            };
            return Map.entry(key, value);
        }
    }

    public Map.Entry<String, String> getOrdinaryHeader() {
        return ordinaryHeaders.next();
    }

    public void outputCase(ArrayList<Skeleton.Node> nodes, ArrayList<Config> configs) {
        for (int i = 0; i < nodes.size(); ++ i) {
            Skeleton.Node node = nodes.get(i);
            System.out.print("[" + i + "]");
            System.out.print("\tkind: " + node.kind);
            System.out.print("\tpred:");
            for (Skeleton.Node pred : node.pred) {
                System.out.print(" " + pred.kind);
            }
            System.out.print("\tsucc:");
            for (Skeleton.Node succ : node.succ) {
                System.out.print(" " + succ.kind);
            }
            System.out.println();
        }
        System.out.println();

        if (configs != null) {
            for (Config config : configs) {
                if (config instanceof VirtualService vs) {
                    System.out.println("VirtualService: " + vs.metadata.get("name"));
                    System.out.println("hosts:");
                    for (DomainHost host: vs.hosts) {
                        System.out.println("\t" + host.domain);
                    }
                    System.out.println("gateways:");
                    for (String gw: vs.gateways) {
                        System.out.println("\t" + gw);
                    }
                    System.out.println("destinations:");
                    for (HTTPRoute route: vs.http) {
                        if (route.delegate != null) {
                            System.out.println("\t" + route.delegate.name.domain);
                        } else {
                            System.out.println("\t" + route.route.get(0).destination.host.domain);
                        }
                    }
                } else if (config instanceof DestinationRule dr) {
                    System.out.println("DestinationRule: " + dr.metadata.get("name"));
                    System.out.println("host: " + dr.host.domain);
                } else if (config instanceof Gateway gw) {
                    System.out.println("Gateway: " + gw.metadata.get("name"));
                    System.out.println("servers:");
                    for (Server server: gw.servers) {
                        System.out.println("\t" + server.hosts.get(0).domain);
                    }
                } else if (config instanceof ServiceEntry se) {
                    System.out.println("ServiceEntry: " + se.metadata.get("name"));
                    System.out.println("hosts:");
                    for (DomainHost host: se.hosts) {
                        System.out.println("\t" + host.domain);
                    }
                } else if (config instanceof Service svc) {
                    System.out.println("Service: " + svc.metadata.get("name"));
                }
                System.out.println();
            }
        }
    }

    private Allocator<String> sequentialHostSeeds = new Allocator<>(
            false,
            "example", "bookinfo", "httpbin", "google", "cloud", "native"
    );

    // This method can promise that the hosts allocated from it
    // won't conflict with each other. But it cannot avoid conflicts
    // with hosts allocated by other methods.
    public String getSequentialHost() {
        return "www." + sequentialHostSeeds.next() + ".com";
    }
}
