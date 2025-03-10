package conf;

import utils.Rand;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Abstract configuration
// For reactive interleaving, there are four kind of fields matters:
// 1. host
// 2. port
// 3. gateway
// 4. label
public class Resource {
    public enum Kind {
        VIRTUAL_SERVICE,
        DESTINATION_RULE,
        GATEWAY,
        SERVICE_ENTRY,
        SERVICE,
        POD,
    }
    public Kind kind;
    public String host; // service should be in short form

    public String gateway;
    public Integer frontPort;
    public Integer backPort;
    public String subset;
    public String label;
    public String delegate;

    // annotations
    public boolean DELEGATE_ROOT = false;
    public boolean DELEGATE_SUCC = false;
    public boolean SUBSET_LABEL = false;

    public ArrayList<Resource> pred; // predecessors
    public ArrayList<Resource> succ; // successors
    public Map.Entry<String , Object> requiredField;

    public Resource(Kind kind, String key) {
        this.kind = kind;
        this.host = key;
        this.pred = new ArrayList<>();
        this.succ = new ArrayList<>();
    }

    public String toString() {
        return kind.toString() + " " + host;
    }

    public Resource addParent(Resource parent) {
        this.pred.add(parent);
        parent.succ.add(this);
        return this;
    }

    public Resource addSuccessor(Resource successor) {
        this.succ.add(successor);
        successor.pred.add(this);
        return this;
    }

    public void propagate(Resource target, String originField, String targetField) {
        Field originFieldObj;
        Field targetFieldObj;

        try {
            originFieldObj = this.getClass().getField(originField);
            targetFieldObj = target.getClass().getField(targetField);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found");
        }

        try {
            if (originFieldObj.get(this) != null) {
                targetFieldObj.set(target, originFieldObj.get(this));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Field not accessible");
        }
    }

    private static final ArrayList<String> hostFields = new ArrayList<>(List.of(
            "vs.hosts.domain", "dr.host.domain", "gw.servers.hosts.domain", "se.hosts.domain",
            "svc.metadata.name", "pod.metadata.name"));
    private static final ArrayList<String> gatewayFields = new ArrayList<>(List.of(
            "vs.gateways", "vs.http.match.gateways", "vs.tcp.match.gateways"));
    private static final ArrayList<String> frontPortFields = new ArrayList<>(List.of(
            "vs.http.match.port", "vs.tcp.match.port", "se.ports.number", "svc.ports.port"));
    private static final ArrayList<String> backPortFields = new ArrayList<>(List.of(
            "vs.http.route.destination.port.number", "vs.tcp.route.destination.port.number",
            "se.ports.targetPort", "gw.servers.port.number", "svc.ports.targetPort"));
    private static final ArrayList<String> targetPortFields = new ArrayList<>(List.of(
            "se.ports.targetPort", "svc.ports.targetPort"));
    private static final ArrayList<String> subsetFields = new ArrayList<>(List.of(
            "vs.http.route.destination.subset", "dr.subsets.name", "vs.tcp.route.destination.subset"));
    private static final ArrayList<String> labelFields = new ArrayList<>(List.of(
            "dr.subsets.labels", "dr.workloadSelector.matchLabels", "se.workloadSelector.labels",
            "gw.selector", "svc.selector", "pod.metadata.labels"));
    private static final ArrayList<String> delegateFields = new ArrayList<>(List.of(
            "vs.http.delegate.name.domain"));

    public Resource randomResource(RationalMaker maker) {
        ArrayList<Resource.Kind> kinds = new ArrayList<>(List.of(
                Resource.Kind.VIRTUAL_SERVICE, Resource.Kind.DESTINATION_RULE, Resource.Kind.GATEWAY,
                Resource.Kind.SERVICE_ENTRY, Resource.Kind.SERVICE, Resource.Kind.POD));
        Function<Resource.Kind, String> getHost = (kind) -> {
            switch (kind) {
                case VIRTUAL_SERVICE, SERVICE_ENTRY, GATEWAY:
                    return maker.getHost();
                case DESTINATION_RULE:
                    return maker.exactHost(maker.getHost());
                case SERVICE:
                    return maker.getService();
                case POD:
                    return maker.getService() + "-v" + Rand.RandInt(1, 3);
                default: return null;
            }
        };
        Resource.Kind kind = kinds.get(Rand.RandInt(kinds.size()));
        String host = getHost.apply(kind);
        return new Resource(kind, host);
    }

    public static ArrayList<Resource> createProactiveResources(Interleave.InterleavePair pair, RationalMaker maker, int number) {
        ArrayList<String> fields = new ArrayList<>();
        ArrayList<Resource> result = new ArrayList<>();

        // util functions in this method
        Function<String, Kind> getKind = (field) -> {
            String key = field.split("\\.")[0];
            switch (key) {
                case "vs": return Resource.Kind.VIRTUAL_SERVICE;
                case "dr": return Resource.Kind.DESTINATION_RULE;
                case "gw": return Resource.Kind.GATEWAY;
                case "se": return Resource.Kind.SERVICE_ENTRY;
                case "svc": return Resource.Kind.SERVICE;
                case "pod": return Resource.Kind.POD;
                default: return null;
            }
        };
        Function<String, String> getHost = (field) -> {
            String key = field.split("\\.")[0];
            switch (key) {
                case "vs", "gw", "se": return maker.getHost();
                case "dr": return maker.exactHost(maker.getHost());
                case "svc": return maker.getService();
                case "pod": return maker.getService() + "-v" + Rand.RandInt(1, 3);
                default: return null;
            }
        };
        Function<String, Boolean> isClusterHost = (field) -> {
            String key = field.split("\\.")[0];
            return key.equals("svc") || key.equals("pod");
        };

        fields.add(pair.field1);
        fields.add(pair.field2);
        if (number > 2) {
            for (int i = 2; i < number; i++) {
                fields.add(Rand.RandBool(50)? pair.field1: pair.field2);
            }
        }

        ArrayList<String> portFields = new ArrayList<>();
        portFields.addAll(frontPortFields);
        portFields.addAll(backPortFields);

        if (hostFields.contains(pair.field1) || hostFields.contains(pair.field2)) {
            String host;
            if (isClusterHost.apply(pair.field1) || isClusterHost.apply(pair.field2)) {
                host = maker.getService();
                if (pair.field1.equals("pod.metadata.name") || pair.field2.equals("pod.metadata.name")) {
                    host += "-v" + Rand.RandInt(1, 3);
                }
            } else {
                host = maker.getHost();
            }
            for (String field: fields) {
                Resource res;
                if (hostFields.contains(field)) {
                    Kind kind = getKind.apply(field);
                    if (kind == Kind.DESTINATION_RULE) {
                        res = new Resource(kind, maker.exactHost(host));
                    } else {
                        res = new Resource(kind, host);
                    }
                } else if (delegateFields.contains(field)) {
                    res = new Resource(getKind.apply(field), getHost.apply(field));
                    res.DELEGATE_ROOT = true;
                    res.delegate = maker.exactHost(host);
                } else {
                    res = new Resource(getKind.apply(field), getHost.apply(field));
                    res.requiredField = Map.entry(field, maker.exactHost(host));
                }
                result.add(res);
            }
        } else if (gatewayFields.contains(pair.field1) || gatewayFields.contains(pair.field2)) {
            String gateway = maker.getGateway();
            for (String field: fields) {
                Resource res = new Resource(getKind.apply(field), getHost.apply(field));
                res.gateway = gateway;
                result.add(res);
            }
        } else if (portFields.contains(pair.field1) || portFields.contains(pair.field2)) {
            Integer port = maker.getPort();
            if (targetPortFields.contains(pair.field1) || targetPortFields.contains(pair.field2)) {
                port = 9080;
            }
            for (String field: fields) {
                Resource res;
                if (frontPortFields.contains(field)) {
                    res = new Resource(getKind.apply(field), getHost.apply(field));
                    res.frontPort = port;
                } else {
                    res = new Resource(getKind.apply(field), getHost.apply(field));
                    res.backPort = port;
                }
                result.add(res);
            }
        } else if (subsetFields.contains(pair.field1) || subsetFields.contains(pair.field2)) {
            String subset = "v" + Rand.RandInt(1, 3);
            for (String field: fields) {
                Resource res = new Resource(getKind.apply(field), getHost.apply(field));
                res.subset = subset;
                result.add(res);
            }
        } else if (labelFields.contains(pair.field1) || labelFields.contains(pair.field2)) {
            String label = maker.getLabel();
            for (String field: fields) {
                Resource res = new Resource(getKind.apply(field), getHost.apply(field));
                res.label = label;
                if (field.equals("dr.subsets.labels")) res.SUBSET_LABEL = true;
                result.add(res);
            }
        } else if (delegateFields.contains(pair.field1) || delegateFields.contains(pair.field2)) {
            String delegate = maker.getDelegate();
            for (String field: fields) {
                Resource res = new Resource(getKind.apply(field), getHost.apply(field));
                res.delegate = delegate;
                if (field.equals("vs.http.delegate.name.domain")) res.DELEGATE_ROOT = true;
                if (!delegateFields.contains(field)) res.requiredField = Map.entry(field, delegate);
                result.add(res);
            }
        } else {
            for (String field: fields) {
                Resource res = new Resource(getKind.apply(field), getHost.apply(field));
                result.add(res);
                Object value = Interleave.createExampleField(field);
                res.requiredField = Map.entry(field, value);
            }
        }

        return result;
    }
}
