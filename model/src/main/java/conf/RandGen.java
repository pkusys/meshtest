package conf;

import frontend.Config;
import frontend.istio.*;
import utils.Rand;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static conf.Util.putWithField;
import static utils.AssertionHelper.Assert;
import static utils.Dump.DumpResources;

public class RandGen {
    private ArrayList<Config> resources;
    private RandMaker maker;
    public RandGen() {
        resources = new ArrayList<>();
        maker = new RandMaker();
    }

    public void clearResources() {
        resources.clear();
    }


    public void extendFromExist() {
        if (resources.isEmpty()) {
            return;
        }
        Config resource = resources.get((int)(Rand.RandDouble() * resources.size()));
        randomExtend(resource);
    }

    public void randomExtend(Config resource) {
        // get valid extension points of resource
        HashMap<String, Object> points = getValidExtPts(resource);
        if (points.isEmpty()) {
            return;
        }
        ArrayList<String> keys = new ArrayList<>(points.keySet());
        String originField = keys.get((int)(Rand.RandDouble() * keys.size()));

        ArrayList<String> interleavingFields = Interleave.getInterleaveFields(originField);
        if (interleavingFields.isEmpty()) {
            return;
        }

        String targetField = interleavingFields.get((int)(Rand.RandDouble() * interleavingFields.size()));

        Object value = points.get(originField);

        // todo: @Tianshuo mutate value with more patterns
        Config newResource = constructWithField(targetField, value);
        resources.add(newResource);
    }




    private Boolean fieldInSameResource(String f1, String f2) {
        String[] parts1 = f1.split("\\.");
        String[] parts2 = f2.split("\\.");
        return parts1[0].equals(parts2[0]);
    }

    // Add two resources proactively from a pair
    public void addProactive(Interleave.InterleavePair pair) {
        Object proactiveVal = Interleave.createExampleField(pair.field1);

        if (fieldInSameResource(pair.field1, pair.field2)) {
            // Randomly choose two fields in the same resource or different resources
            if (Rand.RandDouble() < 0.5) {
                Config resource = constructWithField(pair.field1, proactiveVal);
                putWithField(pair.field2, resource, proactiveVal);
                resources.add(resource);
                addReactive(resource);
                return;
            }
        }

        // if in different resource or chose to add two fields in different resources
        Config resource1 = constructWithField(pair.field1, proactiveVal);
        Config resource2 = constructWithField(pair.field2, proactiveVal);
        resources.add(resource1);
        resources.add(resource2);
        addReactive(resource1);
        addReactive(resource2);
        return;
    }

    public void addReactive(Config resource) {
        // get valid extension points of resource
        HashMap<String, Object> points = getValidExtPts(resource);

        // check whether the points are necessary to extend
        // if necessary, add extension pairs from candidate points
        ArrayList<Interleave.InterleavePair> reactivePairs = new ArrayList<>();
        for (String point: points.keySet()) {
            ArrayList<String> targetPoints = Interleave.getReactivePairs(point);
            if (targetPoints.isEmpty()) {
                continue;
            }
            // if not empty, randomly choose one candidate
            reactivePairs.add(new Interleave.InterleavePair(point, targetPoints.get((int)(Rand.RandDouble() * targetPoints.size())), false));
        }

        HashMap<String, Object> vsPoints = new HashMap<>();
        HashMap<String, Object> drPoints = new HashMap<>();
        HashMap<String, Object> sePoints = new HashMap<>();
        HashMap<String, Object> gwPoints = new HashMap<>();
        HashMap<String, Object> svcPoints = new HashMap<>();
        HashMap<String, Object> podPoints = new HashMap<>();

        for (Interleave.InterleavePair pair: reactivePairs) {
            Object reactiveVal = getWithField(pair.field1, resource);
            String targetField = pair.field2;
            Assert(reactiveVal != null, "Reactive value is null.");

            switch (targetField.split("\\.")[0]) {
                case "vs": vsPoints.put(targetField, reactiveVal); break;
                case "dr": drPoints.put(targetField, reactiveVal); break;
                case "se": sePoints.put(targetField, reactiveVal); break;
                case "gw": gwPoints.put(targetField, reactiveVal); break;
                case "svc": svcPoints.put(targetField, reactiveVal); break;
                case "pod": podPoints.put(targetField, reactiveVal); break;
                default: throw new RuntimeException("Unknown config type: " + targetField.split("\\.")[0]);
            }
        }

        if (!vsPoints.isEmpty()) {
            addReactiveHelper(vsPoints);
        }
        if (!drPoints.isEmpty()) {
            addReactiveHelper(drPoints);
        }
        if (!sePoints.isEmpty()) {
            addReactiveHelper(sePoints);
        }
        if (!gwPoints.isEmpty()) {
            addReactiveHelper(gwPoints);
        }
        if (!svcPoints.isEmpty()) {
            addReactiveHelper(svcPoints);
        }
        if (!podPoints.isEmpty()) {
            addReactiveHelper(podPoints);
        }
    }

    private void addReactiveHelper(HashMap<String, Object> points) {
        HashMap<String, Object> hostPoints = new HashMap<>();
        HashMap<String, Object> otherPoints = new HashMap<>();
        for (Map.Entry<String, Object> entry: points.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (field.contains("host") || field.contains("name")) {
                hostPoints.put(field, value);
            } else {
                otherPoints.put(field, value);
            }
        }

        if (hostPoints.isEmpty()) {
            Config resource = constructWithFields(otherPoints);
            resources.add(resource);
            addReactive(resource);
        } else {
            for (Map.Entry<String, Object> entry: hostPoints.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                otherPoints.put(field, value);
                Config resource = constructWithFields(otherPoints);
                resources.add(resource);
                addReactive(resource);
                otherPoints.remove(field);
            }
        }
    }

    public Config constructWithField(String field, Object value) {
        String[] parts = field.split("\\.");
        Config result;
        RandMaker maker = new RandMaker();

        switch (parts[0]) {
            case "vs": result = maker.makeVirtualService(); break;
            case "dr": result = maker.makeDestinationRule(); break;
            case "se": result = maker.makeServiceEntry(); break;
            case "gw": result = maker.makeGateway(); break;
            case "svc": result = maker.makeService(); break;
            case "pod": result = maker.makePod(); break;
            default: throw new RuntimeException("Unknown config type: " + parts[0]);
        }

        // name should be in short form? for example, Service name should be
        // productpage instead of productpage.default.svc.cluster.local
        if (field.contains("name")) {
            Assert(value.getClass() == String.class, "Value is not String.");
            value = ((String)value).split("\\.")[0];
        }
        putWithField(field, result, value);

        return result;
    }

    // Return valid extension points of resource
    // RetVal: Map<field, value>
    public HashMap<String, Object> getValidExtPts(Config resource) {
        String key;
        if (resource instanceof VirtualService) {
            key = "vs";
        } else if (resource instanceof DestinationRule) {
            key = "dr";
        } else if (resource instanceof ServiceEntry) {
            key = "se";
        } else if (resource instanceof Gateway) {
            key = "gw";
        } else if (resource instanceof Service) {
            key = "svc";
        } else if (resource instanceof Pod) {
            key = "pod";
        } else {
            System.out.println("[Warning] resource is not extendable.");
            return new HashMap<>();
        }
        ArrayList<String> basicPoints = Interleave.getExtPts(key);
        HashMap<String, Object> res = new HashMap<>();
        for (String point: basicPoints) {
            Object obj = getWithField(point, resource);
            if (obj != null) {
                res.put(point, obj);
            }
        }
        return res;
    }

    // get the value of the given field from resource
    public Object getWithField(String field, Object resource) {
        if (resource == null) {
            return null;
        }
        String[] parts = field.split("\\.");

        // last dimension
        if (parts.length == 1) {
            try {
                if (resource instanceof Map) {
                    Object res;
                    if (field.equals("_k")) {
                        if (((Map<?, ?>) resource).isEmpty()) {
                            return null;
                        }
                        else {
                            res = ((Map<?,?>)resource).keySet().toArray()[0];
                        }
                    } else {
                        res = ((Map<?, ?>) resource).get(field);
                    }

                    // if res is a list or map, return the first element
                    // if res is integer, check its validity
                    if (res instanceof List<?> resList) {
                        if (resList.isEmpty()) {
                            return null;
                        } else {
                            return resList.get(0);
                        }
                    } else if (res instanceof Map<?, ?> resMap) {
                        if (resMap.isEmpty()) {
                            return null;
                        } else {
                            return resMap.entrySet().toArray()[0];
                        }
                    } else if (res instanceof Integer resInt) {
                        if (resInt == -1) {
                            return null;
                        } else {
                            return res;
                        }
                    } else {
                        return res;
                    }
                }
                Field f = resource.getClass().getField(field);
                Object res = f.get(resource);

                if (res instanceof List<?> resList) {
                    if (resList.isEmpty()) {
                        return null;
                    } else {
                        return resList.get(0);
                    }
                } else if (res instanceof Map<?, ?> resMap) {
                    if (resMap.isEmpty()) {
                        return null;
                    } else {
                        return resMap.entrySet().toArray()[0];
                    }
                } else if (res instanceof Integer resInt) {
                    if (resInt == -1) {
                        return null;
                    } else {
                        return res;
                    }
                } else {
                    return res;
                }
            } catch (Exception ignored) {
                throw new RuntimeException("Field " + field + " does not exist.");
            }
        }

        String key = parts[0];
        Boolean lv0 = true;
        switch (key) {
            case "vs": {
                if (!(resource instanceof VirtualService)) {
                    return null;
                }
                break;
            }
            case "dr": {
                if (!(resource instanceof DestinationRule)) {
                    return null;
                }
                break;
            }
            case "se": {
                if (!(resource instanceof ServiceEntry)) {
                    return null;
                }
                break;
            }
            case "gw": {
                if (!(resource instanceof Gateway)) {
                    return null;
                }
                break;
            }
            case "svc": {
                if (!(resource instanceof Service)) {
                    return null;
                }
                break;
            }
            case "pod": {
                if (!(resource instanceof Pod)) {
                    return null;
                }
                break;
            }
            default: lv0 = false;
        }
        if (lv0) {
            return getWithField(field.substring(key.length() + 1), resource);
        }

        // recursion
        try {
            if (resource instanceof Map) {
                if (key.equals("_k")) {
                    if (((Map<?, ?>) resource).isEmpty()) {
                        return null;
                    }
                    else {
                        return getWithField(field.substring(key.length() + 1),
                                ((Map<?,?>)resource).keySet().toArray()[0]);
                    }
                } else {
                    if (!((Map<?, ?>) resource).containsKey(key)) {
                        return null;
                    } else {
                        return getWithField(field.substring(key.length() + 1), ((Map<?,?>)resource).get(key));
                    }
                }
            }
            Field f = resource.getClass().getField(key);
            Object fCurr = f.get(resource);
            if (fCurr instanceof List<?> fcurrList) {
                if (fcurrList.isEmpty()) {
                    return null;
                } else {
                    List<Object> res = fcurrList.stream().filter(Objects::nonNull)
                            .map(x -> getWithField(field.substring(key.length() + 1), x))
                            .filter(Objects::nonNull).toList();
                    if (res.isEmpty()) {
                        return null;
                    } else {
                        return res.get(0);
                    }
                }
            }
            else {
                return getWithField(field.substring(key.length() + 1), fCurr);
            }
        } catch (Exception ignored) {
            throw new RuntimeException("Error on " + field);
        }
    }

    // note: we assume that the fields are in the same resource
    public Config constructWithFields(Map<String, Object> fields) {
        String key = fields.keySet().toArray()[0].toString().split("\\.")[0];
        Config result;
        RandMaker maker = new RandMaker();

        switch (key) {
            case "vs": result = maker.makeVirtualService(); break;
            case "dr": result = maker.makeDestinationRule(); break;
            case "se": result = maker.makeServiceEntry(); break;
            case "gw": result = maker.makeGateway(); break;
            case "svc": result = maker.makeService(); break;
            case "pod": result = maker.makePod(); break;
            default: throw new RuntimeException("Unknown config type: " + key);
        }

        for (Map.Entry<String, Object> entry: fields.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            // name should be in short form?
            if (field.contains("name")) {
                Assert(value.getClass() == String.class, "Value is not String.");
                value = ((String)value).split("\\.")[0];
            }

            putWithField(field, result, value);
        }

        return result;
    }

    public void generate() {
        long seed = System.currentTimeMillis() % 262144;
        // set current time as seed

        Rand.setSeed(seed);
        System.out.println("Seed: " + seed);

        ArrayList<Interleave.InterleavePair> proactivePairs = Interleave.getProactivePairs(-1, null);
        Interleave.InterleavePair pair = proactivePairs.get((int) (Rand.RandDouble() * proactivePairs.size()));
        this.addProactive(pair);
        // dump to date-time.yaml
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd_HH-mm-ss");
        String formattedNow = now.format(formatter);

        DumpResources(this.resources, "example-" + formattedNow + "-" + seed + ".yaml");
        seed = Rand.RandInt(0, 262144);
    }

}
