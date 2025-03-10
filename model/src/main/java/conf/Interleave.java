package conf;

import frontend.Config;
import frontend.istio.component.HTTPMatchRequest;

import frontend.istio.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static utils.AssertionHelper.Assert;

/*
 * Configurations in the same list will be interleaved
 * */
public class Interleave {
    // Equivalence classes of interleaving fields
    public static HashMap<String, ArrayList<String>> InterleaveEC = new HashMap<>();
    // Points to be interleaved
    public static HashMap<String, ArrayList<String>> ExtendPoint = new HashMap<>();
    // ReactiveExt to make traffic go through the proxy
    public static HashMap<String, ArrayList<String>> ReactiveExt = new HashMap<>();

    static {
        // ReactiveExt to make traffic go through the proxy
        // If the field exists in resource, the corresponding field in ReactiveExt will be added to the resource
        ReactiveExt.put("vs.hosts.domain", new ArrayList<>(List.of("se.hosts.domain", "gw.servers.hosts.domain")));
        ReactiveExt.put("vs.http.route.destination.host.domain", new ArrayList<>(List.of("dr.host.domain")));
        ReactiveExt.put("vs.http.delegate.name", new ArrayList<>(List.of("vs.metadata.name")));
        ReactiveExt.put("vs.tcp.route.destination.host.domain", new ArrayList<>(List.of("dr.host.domain")));
        ReactiveExt.put("dr.host.domain", new ArrayList<>(List.of("se.hosts.domain", "svc.metadata.name")));
        ReactiveExt.put("vs.http.match.port", new ArrayList<>(List.of("se.ports.number", "gw.servers.port.number")));
        ReactiveExt.put("vs.http.route.destination.port.number", new ArrayList<>(List.of("se.ports.number", "svc.ports.port")));
        ReactiveExt.put("vs.tcp.match.port", new ArrayList<>(List.of("se.ports.number", "gw.servers.port.number")));
        ReactiveExt.put("vs.tcp.route.destination.port.number", new ArrayList<>(List.of("se.ports.number", "svc.ports.port")));
        ReactiveExt.put("vs.gateways", new ArrayList<>(List.of("gw.metadata.name")));
        ReactiveExt.put("vs.http.match.gateways", new ArrayList<>(List.of("gw.metadata.name")));
        ReactiveExt.put("vs.tcp.match.gateways", new ArrayList<>(List.of("gw.metadata.name")));
        ReactiveExt.put("vs.http.route.destination.subset", new ArrayList<>(List.of("dr.subsets.name")));
        ReactiveExt.put("vs.tcp.route.destination.subset", new ArrayList<>(List.of("dr.subsets.name")));
        ReactiveExt.put("dr.subsets.labels", new ArrayList<>(List.of("svc.selector")));
        ReactiveExt.put("dr.workloadSelector.matchLabels", new ArrayList<>(List.of("svc.selector")));
        ReactiveExt.put("se.workloadSelector.labels", new ArrayList<>(List.of("svc.selector")));
        ReactiveExt.put("gw.selector", new ArrayList<>(List.of("svc.selector")));
        ReactiveExt.put("svc.selector", new ArrayList<>(List.of("pod.metadata.labels")));

        // Equivalence classes of interleaving fields
        ArrayList<String> Host = new ArrayList<>();
        ArrayList<String> Port = new ArrayList<>();
        ArrayList<String> Uri = new ArrayList<>();
        ArrayList<String> Scheme = new ArrayList<>();
        ArrayList<String> Method = new ArrayList<>();
        ArrayList<String> Header = new ArrayList<>();
        ArrayList<String> HeaderKey = new ArrayList<>();
        ArrayList<String> Gateway = new ArrayList<>();
        ArrayList<String> SourceLabel = new ArrayList<>();
        ArrayList<String> DestinationLabel = new ArrayList<>();
        ArrayList<String> SubsetLabel = new ArrayList<>();

        InterleaveEC.put("Host", Host);
        InterleaveEC.put("Port", Port);
        InterleaveEC.put("Uri", Uri);
        InterleaveEC.put("Scheme", Scheme);
        InterleaveEC.put("Method", Method);
        InterleaveEC.put("Header", Header);
        InterleaveEC.put("HeaderKey", HeaderKey);
        InterleaveEC.put("Gateway", Gateway);
        InterleaveEC.put("SourceLabel", SourceLabel);
        InterleaveEC.put("DestinationLabel", DestinationLabel);
        InterleaveEC.put("SubsetLabel", SubsetLabel);

        // Points to be interleaved
        ArrayList<String> VSPoints = new ArrayList<>();
        ArrayList<String> DRPoints = new ArrayList<>();
        ArrayList<String> SEPoints = new ArrayList<>();
        ArrayList<String> GWPoints = new ArrayList<>();
        ArrayList<String> SvcPoints = new ArrayList<>();
        ArrayList<String> PodPoints = new ArrayList<>();

        ExtendPoint.put("vs", VSPoints);
        ExtendPoint.put("dr", DRPoints);
        ExtendPoint.put("se", SEPoints);
        ExtendPoint.put("gw", GWPoints);
        ExtendPoint.put("svc", SvcPoints);
        ExtendPoint.put("pod", PodPoints);


        // Host: String
//        Host.add("vs.metadata.name");
        Host.add("vs.hosts.domain");
        Host.add("vs.http.match.authority.value");
        Host.add("vs.http.match.headers.:authority.value");
        Host.add("vs.http.match.headers.authority.value");
        Host.add("vs.http.match.headers.host.value");
        Host.add("vs.http.match.withoutHeaders.:authority.value");
        Host.add("vs.http.match.withoutHeaders.authority.value");
        Host.add("vs.http.match.withoutHeaders.host.value");
        Host.add("vs.http.route.destination.host.domain");
        Host.add("vs.http.route.headers.request.set.:authority");
        Host.add("vs.http.route.headers.request.set.authority");
        Host.add("vs.http.route.headers.request.set.host");
        Host.add("vs.http.route.headers.request.add.:authority");
        Host.add("vs.http.route.headers.request.add.authority");
        Host.add("vs.http.route.headers.request.add.host");
        Host.add("vs.http.delegate.name.domain");
        Host.add("vs.http.rewrite.authority.domain");
        Host.add("vs.http.headers.request.set.:authority");
        Host.add("vs.http.headers.request.set.authority");
        Host.add("vs.http.headers.request.add.:authority");
        Host.add("vs.http.headers.request.add.authority");
        Host.add("vs.tcp.route.destination.host.domain");
        Host.add("dr.host.domain");
        Host.add("se.hosts.domain");
        Host.add("gw.servers.hosts.domain");
        Host.add("svc.metadata.name");
//        Host.add("pod.metadata.name");

        // Port: Integer/int
        Port.add("vs.http.match.port");
        Port.add("vs.http.route.destination.port.number");
        Port.add("vs.tcp.match.port");
        Port.add("vs.tcp.route.destination.port.number");
        Port.add("se.ports.number");
        Port.add("se.ports.targetPort");
        Port.add("gw.servers.port.number");
        Port.add("svc.ports.port");
        Port.add("svc.ports.targetPort");

        // Uri: String
        Uri.add("vs.http.match.uri.value");
        Uri.add("vs.http.match.headers.uri.value");
        Uri.add("vs.http.match.withoutHeaders.uri.value");
        Uri.add("vs.http.rewrite.uri");

        // Scheme: StringMatch
        Scheme.add("vs.http.match.scheme");
        Scheme.add("vs.http.match.headers.:scheme");
        Scheme.add("vs.http.match.headers.scheme");
        Scheme.add("vs.http.match.withoutHeaders.:scheme");
        Scheme.add("vs.http.match.withoutHeaders.scheme");

        // Method: StringMatch
        Method.add("vs.http.match.method");
        Method.add("vs.http.match.headers.method");
        Method.add("vs.http.match.withoutHeaders.method");

        // Header: <String, String>
        // Maybe we can deal with it specially
        Header.add("vs.http.match.headers"); // <String, StringMatch>
        Header.add("vs.http.match.withoutHeaders"); // <String, StringMatch>
        Header.add("vs.http.route.headers.request.set"); // <String, String>
        Header.add("vs.http.route.headers.request.add"); // <String, String>
        Header.add("vs.http.headers.request.set"); // <String, String>
        Header.add("vs.http.headers.request.add"); // <String, String>


        // Header key: String
        HeaderKey.add("vs.http.match.headers._k");
        HeaderKey.add("vs.http.match.withoutHeaders._k");
        HeaderKey.add("vs.http.route.headers.request.set._k");
        HeaderKey.add("vs.http.route.headers.request.add._k");
        HeaderKey.add("vs.http.route.headers.request.remove");
        HeaderKey.add("vs.http.headers.request.set._k");
        HeaderKey.add("vs.http.headers.request.add._k");
        HeaderKey.add("vs.http.headers.request.remove");

        // Gateway: String
        Gateway.add("vs.gateways");
        Gateway.add("vs.http.match.gateways");
        Gateway.add("vs.tcp.match.gateways");

        // SourceLabel: <String, String>
        SourceLabel.add("vs.http.match.sourceLabels");
        SourceLabel.add("vs.tcp.match.sourceLabels");

        // Destination: String
        SubsetLabel.add("vs.http.route.destination.subset");
        SubsetLabel.add("vs.tcp.route.destination.subset");
        SubsetLabel.add("dr.subsets.name");

        // DestinationLabel: <String, String>
        DestinationLabel.add("dr.subsets.labels"); // <String, String>
        DestinationLabel.add("dr.workloadSelector.matchLabels"); // <String, String>
        DestinationLabel.add("se.workloadSelector.labels"); // <String, String>
        DestinationLabel.add("gw.selector"); // <String, String>
        DestinationLabel.add("svc.selector"); // <String, String>
        DestinationLabel.add("pod.metadata.labels"); // <String, String>

        for (ArrayList<String> list: InterleaveEC.values()) {
            for (String field: list) {
                String[] parts = field.split("\\.");
                assert parts.length >= 2;
                String resource = parts[0];
                if (ExtendPoint.containsKey(resource)) {
                    ExtendPoint.get(resource).add(field);
                } else {
                    System.out.println("[Warning] Field " + field + " is not extendable.");
                }
            }
        }
    }

    // get the list of fields that should be interleaved with the given field
    public static ArrayList<String> getInterleaveFields(String field) {
        ArrayList<String> result = new ArrayList<>();
        for (ArrayList<String> list: InterleaveEC.values()) {
            if (list.contains(field)) {
                result.addAll(list);
            }
        }
        return result;
    }

    public static ArrayList<String> getExtPts(String key) {
        if (key == null || !ExtendPoint.containsKey(key)) {
            return new ArrayList<>();
        }
        return ExtendPoint.get(key);
    }

    public static class InterleavePair {
        public String field1;
        public String field2;

        public InterleavePair(String f1, String f2) {
            if (f1.compareTo(f2) < 0) {
                field1 = f1;
                field2 = f2;
            } else {
                field1 = f2;
                field2 = f1;
            }
        }

        public InterleavePair(String f1, String f2, boolean sort) {
            if (sort) {
                if (f1.compareTo(f2) < 0) {
                    field1 = f1;
                    field2 = f2;
                } else {
                    field1 = f2;
                    field2 = f1;
                }
            } else {
                field1 = f1;
                field2 = f2;
            }
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InterleavePair iPair = (InterleavePair) o;
            return field1.equals(iPair.field1) && field2.equals(iPair.field2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field1, field2);
        }
    }


    public static ArrayList<InterleavePair> getProactivePairs(Integer num, String specific) {
        HashSet<String> interleaveFields = new HashSet<>();

        if (specific != null) {
            interleaveFields.addAll(InterleaveEC.get(specific));
        } else {
            for (String _k : InterleaveEC.keySet()) {
                interleaveFields.addAll(InterleaveEC.get(_k));
            }
        }

        HashSet<InterleavePair> interleavePairs = new HashSet<>();
        for (String field1: interleaveFields) {
            ArrayList<String> interleaveFields2 = getInterleaveFields(field1);
            for (String field2: interleaveFields2) {
                interleavePairs.add(new InterleavePair(field1, field2));
            }
        }

        ArrayList<InterleavePair> pairs = new ArrayList<>(interleavePairs);


        if (num == -1) {
            return pairs;
        } else {
            return new ArrayList<>(pairs.subList(0, num));
        }
    }


    public static Object createExampleField(String field) {
        String type = null;
        for (String t: InterleaveEC.keySet()) {
            if (InterleaveEC.get(t).contains(field)) {
                type = t;
                break;
            }
        }
        Assert(type != null, "Cannot find type for field " + field);
        switch (type) {
            case "Host":
                return "example.com";
            case "Port":
                return Integer.valueOf(10000);
            case "Uri":
                return "/example";
            case "Scheme":
                return new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, "http");
            case "Method":
                return new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, "GET");
            case "Header":
                LinkedHashMap<String, String> header = new LinkedHashMap<>();
                header.put("app", "example-app");
                return header.entrySet().toArray()[0];
            case "HeaderKey":
                return "app";
            case "Gateway":
                return "example-gateway";
            case "SourceLabel":
                LinkedHashMap<String, String> srcLabel = new LinkedHashMap<>();
                srcLabel.put("version", "v0");
                return srcLabel.entrySet().toArray()[0];
            case "Destination":
                return "example-destination";
            case "DestinationLabel":
                LinkedHashMap<String, String> dstLabel = new LinkedHashMap<>();
                dstLabel.put("version", "v1");
                return dstLabel.entrySet().toArray()[0];
            case "SubsetLabel":
                return "v2";
            default:
                throw new RuntimeException("Unknown type " + type);
        }
    }

    public static Object createSubstituteField(String field) {
        String type = null;
        for (String t: InterleaveEC.keySet()) {
            if (InterleaveEC.get(t).contains(field)) {
                type = t;
                break;
            }
        }
        Assert(type != null, "Cannot find type for field " + field);
        switch (type) {
            case "Host":
                return "www.substitute.com";
            case "Port":
                return Integer.valueOf(20000);
            case "Uri":
                return "/substitute";
            case "Scheme":
                return new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, "http");
            case "Method":
                return new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, "GET");
            case "Header":
                LinkedHashMap<String, String> header = new LinkedHashMap<>();
                header.put("app", "substitute-app");
                return header.entrySet().toArray()[0];
            case "HeaderKey":
                throw new RuntimeException("Cannot create substitute for HeaderKey");
            case "Gateway":
                return "substitute-gateway";
            case "SourceLabel":
                LinkedHashMap<String, String> srcLabel = new LinkedHashMap<>();
                srcLabel.put("version", "v0");
                return srcLabel.entrySet().toArray()[0];
            case "Destination":
                return "substitute.default.svc.cluster.local";
            case "DestinationLabel":
                LinkedHashMap<String, String> dstLabel = new LinkedHashMap<>();
                dstLabel.put("version", "v1");
                return dstLabel.entrySet().toArray()[0];
            case "SubsetLabel":
                return "testversion";
            default:
                throw new RuntimeException("Unknown type " + type);
        }
    }

    public static ArrayList<String> getReactivePairs(String field) {
        if (ReactiveExt.containsKey(field)) {
            return ReactiveExt.get(field);
        } else {
            return new ArrayList<>();
        }
    }

    public void putWithField(String field, Config resource, Object value) {
        String[] parts = field.split("\\.");
        switch (parts[0]) {
            case "vs": Assert(resource instanceof VirtualService, "Resource is not VirtualService."); break;
            case "dr": Assert(resource instanceof DestinationRule, "Resource is not DestinationRule."); break;
            case "se": Assert(resource instanceof ServiceEntry, "Resource is not ServiceEntry."); break;
            case "gw": Assert(resource instanceof Gateway, "Resource is not Gateway."); break;
            case "svc": Assert(resource instanceof Service, "Resource is not Service."); break;
            case "pod": Assert(resource instanceof Pod, "Resource is not Pod."); break;
            default: throw new RuntimeException("Unknown config type: " + parts[0]);
        }


        // curr should be an Object because it can be a list or a map
        Object curr = resource;
        for (int i = 1; i < parts.length; i++) {
            String key = parts[i];

            // special case for metadata
            if (key.equals("metadata")) {
                key = parts[i + 1];
                try {
                    Field f = curr.getClass().getField("metadata");
                    if (key.equals("name")) {
                        assert value instanceof String;
                        ((LinkedHashMap)f.get(curr)).put("name", value);
                    } else {
                        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                        assert value instanceof Map.Entry<?,?>;
                        Map.Entry<?,?> entry = (Map.Entry<?,?>)value;
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        assert k.getClass() == String.class;
                        assert v.getClass() == String.class;
                        labels.put((String)k, (String)v);
                        ((LinkedHashMap)f.get(curr)).put("labels", labels);
                    }
                } catch (Exception ignored) {
                    System.out.println("Error on " + field);
                }
                break;
            }

            // last dimension: add into list, map or directly set the value
            if (i == parts.length - 1) {
                try {
                    if (curr instanceof Map currMap) {
                        if (key.equals("_k")) {
                            Assert(!currMap.isEmpty(), "Map is empty.");
                            // replace key of element 0 to value
                            Object k = currMap.keySet().toArray()[0];
                            currMap.put(value, currMap.get(k));
                            currMap.remove(k);
                        }
                        ((LinkedHashMap)curr).put(key, value);
                    } else {
                        Field f = curr.getClass().getField(key);
                        Class<?> t = f.getType();
                        if (t == ArrayList.class) {
                            Type elemT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                            Assert(value.getClass() == elemT, "Value is not " + elemT.getTypeName() + ".");
                            ((ArrayList) f.get(curr)).add(value);
                        } else if (t == LinkedHashMap.class) {
                            Type valueT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[1];
                            Assert(value instanceof Map.Entry<?, ?>, "Value is not Map.Entry.");
                            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                            Object k = entry.getKey();
                            Object v = entry.getValue();
                            assert k.getClass() == String.class;
                            assert v.getClass() == valueT;
                            ((LinkedHashMap) f.get(curr)).put(k, v);
                        } else {
                            // if value is Integer or int
                            if (value.getClass() == Integer.class || value.getClass() == int.class) {
                                Assert(t == Integer.class || t == int.class, "Value is not " + t.getTypeName() + ".");
                            } else {
                                Assert(value.getClass() == t, "Value is not " + t.getTypeName() + ".");
                            }
                            f.set(curr, value);
                        }
                    }
                } catch (Exception ignored) {
                    throw new RuntimeException("Error on " + field);
                }
            }
            // create a new Config and set it as the current one
            else {
                try {
                    if (curr instanceof Map currMap) {
                        Assert(!currMap.isEmpty(), "Map is empty");
                        if (!currMap.containsKey(key)) {
                            Object k = currMap.keySet().toArray()[0];
                            currMap.put(key, currMap.get(k));
                            currMap.remove(k);
                        }
                        curr = currMap.get(key);
                    } else {
                        Field f = curr.getClass().getField(key);
                        Class<?> t = f.getType();
                        if (t == ArrayList.class) {
                            Type elemT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                            Assert(f.get(curr) != null, "ArrayList is empty.");
                            // note: currently we only support multiple construction on ArrayList
                            if (((ArrayList) f.get(curr)).isEmpty()) {
                                Object v = Class.forName(elemT.getTypeName()).getConstructor().newInstance();
                                ((ArrayList) f.get(curr)).add(v);
                            }
                            curr = ((ArrayList) f.get(curr)).get(0);
                        } else if (t == LinkedHashMap.class || t == Map.class || t == HashMap.class) {
                            Type keyT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                            Type valueT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[1];
                            Object v = t.getConstructor().newInstance();
                            if (f.get(curr) == null || ((Map) f.get(curr)).isEmpty()) {
                                f.set(curr, v);
                            }
                            Assert(f.get(curr) != null, "HashMap is empty.");
                            Assert(v instanceof Map, "Inconsistent type in " + field);
                            ((Map) v).put(Class.forName(keyT.getTypeName()).getConstructor().newInstance(),
                                    Class.forName(valueT.getTypeName()).getConstructor().newInstance());
                            curr = f.get(curr);
                        } else {
                            // note: if t is a map, it will be constructed
                            if (f.get(curr) == null) {
                                Object v = t.getConstructor().newInstance();
                                f.set(curr, v);
                            }
                            curr = f.get(curr);
                        }
                    }
                } catch (Exception ignored) {
                    throw new RuntimeException("Error on " + field);
                }
            }
        }
    }
}