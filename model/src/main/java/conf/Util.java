package conf;

import frontend.istio.component.HTTPMatchRequest;
import frontend.istio.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static utils.AssertionHelper.Assert;

public class Util {
    public static Object putWithField(String field, Object resource, Object value) {
        // debug note: headers and withoutHeaders in match are in type of <String, StringMatch>
        if (field.equals("vs.http.match.headers") || field.equals("vs.http.match.withoutHeaders")) {
            Assert(value instanceof Map.Entry<?,?>, "Value should be in type of <String, String>.");
            HashMap<String, HTTPMatchRequest.StringMatch> header = new HashMap<>();
            Map.Entry<?,?> entry = (Map.Entry<?,?>)value;
            header.put((String)entry.getKey(),
                    new HTTPMatchRequest.StringMatch(HTTPMatchRequest.StringMatch.MatchType.EXACT, (String)entry.getValue()));
            value = header.entrySet().toArray()[0];
        }

        String[] parts = field.split("\\.");
        String key = parts[0];

        // for first dimension
        Boolean lv0 = true;
        switch (key) {
            case "vs": Assert(resource instanceof VirtualService, "Resource is not VirtualService."); break;
            case "dr": Assert(resource instanceof DestinationRule, "Resource is not DestinationRule."); break;
            case "se": Assert(resource instanceof ServiceEntry, "Resource is not ServiceEntry."); break;
            case "gw": Assert(resource instanceof Gateway, "Resource is not Gateway."); break;
            case "svc": Assert(resource instanceof Service, "Resource is not Service."); break;
            case "pod": Assert(resource instanceof Pod, "Resource is not Pod."); break;
            default: lv0 = false; break;
        }
        if (lv0) return putWithField(field.substring(key.length() + 1), resource, value);

        // for metadata
        if (key.equals("metadata")) {
            key = parts[1];
            try {
                Field f = resource.getClass().getField("metadata");
                if (key.equals("name")) {
                    assert value instanceof String;
                    ((LinkedHashMap)f.get(resource)).put("name", value);
                } else {
                    LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                    assert value instanceof Map.Entry<?,?>;
                    Map.Entry<?,?> entry = (Map.Entry<?,?>)value;
                    Object k = entry.getKey();
                    Object v = entry.getValue();
                    assert k.getClass() == String.class;
                    assert v.getClass() == String.class;
                    labels.put((String)k, (String)v);
                    ((LinkedHashMap)f.get(resource)).put("labels", labels);
                }
            } catch (Exception ignored) {
                System.out.println("Error on " + field);
            }
            return resource;
        }

        // last dimension
        if (parts.length == 1) {
            try {
                if (resource instanceof Map currMap) {
                    if (key.equals("_k")) {
                        Assert(!currMap.isEmpty(), "Map is empty.");
                        // replace _placeholder
                        currMap.put(value, currMap.get("_placeholder"));
                        currMap.remove("_placeholder");
                    } else {
                        currMap.put(key, value);
                    }
                } else {
                    Field f = resource.getClass().getField(key);
                    Class<?> t = f.getType();
                    if (t == ArrayList.class) {
                        Type elemT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                        Assert(value.getClass() == elemT, "Value is not " + elemT.getTypeName() + ".");
                        ((ArrayList) f.get(resource)).add(value);
                    } else if (t == LinkedHashMap.class) { // for Map.Entry
                        Type valueT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[1];
                        Assert(value instanceof Map.Entry<?, ?>, "Value is not Map.Entry.");
                        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        assert k.getClass() == String.class;
                        assert v.getClass() == valueT;
                        ((LinkedHashMap) f.get(resource)).put(k, v);
                    } else {
                        // if value is Integer or int
                        if (value.getClass() == Integer.class || value.getClass() == int.class) {
                            Assert(t == Integer.class || t == int.class, "Value is not " + t.getTypeName() + ".");
                        } else {
                            Assert(value.getClass() == t, "Value is not " + t.getTypeName() + ".");
                        }
                        f.set(resource, value);
                    }
                }
            } catch (Exception ignored) {
                throw new RuntimeException("Error on " + field);
            }
            return resource;
        }

        // default: recursion
        try {
            if (resource instanceof Map currMap) {
                Assert(!currMap.isEmpty(), "Map is empty");
                Object val = currMap.get("_placeholder");
                Assert(val != null, "_placeholder does not exist.");
                val = putWithField(field.substring(key.length() + 1), val, value);
                currMap.put(key, val);
                return resource;
            } else {
                Field f = resource.getClass().getField(key);
                Class<?> t = f.getType();
                if (t == ArrayList.class) {
                    Type elemT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                    Assert(f.get(resource) != null, "ArrayList is empty.");
                    // note: currently we only support multiple construction on ArrayList
                    if (((ArrayList) f.get(resource)).isEmpty()) {
                        Object v = Class.forName(elemT.getTypeName()).getConstructor().newInstance();
                        ((ArrayList) f.get(resource)).add(v);
                    }
                    return putWithField(field.substring(key.length() + 1), ((ArrayList) f.get(resource)).get(0), value);
                } else if (t == LinkedHashMap.class) {
                    Type keyT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                    Type valueT = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[1];
                    Assert(keyT == String.class, "Key is not String.");

                    Object map = t.getConstructor().newInstance(); // new LinkedHashMap()
                    if (f.get(resource) == null) {
                        f.set(resource, map);
                    }
                    map = f.get(resource);
                    Assert(f.get(resource) != null, "HashMap is null.");
                    Assert(map instanceof Map, "Inconsistent type in " + field);

                    // _placeholder indicates the type of value
                    Object phVal = Class.forName(valueT.getTypeName()).getConstructor().newInstance();
                    ((LinkedHashMap) map).put("_placeholder", phVal);

                    putWithField(field.substring(key.length() + 1), map, value);

                    // remove _placeholder
                    ((LinkedHashMap) map).remove("_placeholder");

                    return map;
                } else if (t == Map.class || t == HashMap.class) {
                    Assert(false, "Unsupported type: " + t.getTypeName() + ".");
                } else {
                    // note: if t is a map, it will be constructed
                    if (f.get(resource) == null) {
                        Object v = t.getConstructor().newInstance();
                        f.set(resource, v);
                    }
                    return putWithField(field.substring(key.length() + 1), f.get(resource), value);
                }
            }
        } catch (Exception ignored) {
            throw new RuntimeException("Error on " + field);
        }

        return null;
    }


}
