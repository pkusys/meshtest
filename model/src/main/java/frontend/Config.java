package frontend;

import frontend.istio.component.DomainHost;
import frontend.istio.component.Host;
import frontend.istio.component.IPHost;
import frontend.istio.component.Wrapped;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static utils.AssertionHelper.Assert;

/**
 * Base class for all configs.
 * Configs are corresponding to yaml files.
 */
public class Config {
    public static boolean isResource = true;

    /**
     * before calling, you should set isResource true
     */
    public Object toYaml() {
        boolean isResource = Config.isResource;
        Config.isResource = false;

        LinkedHashMap<String, Object> conf = new LinkedHashMap<>();

        Class<?> clazz = this.getClass();
        Field[] fields = clazz.getDeclaredFields();
//        StringBuilder yaml = new StringBuilder("{");

        if (isResource) {
            conf.put("apiVersion", "NA");
            conf.put("kind", "NA");
            conf.put("metadata", "NA");
            conf.put("spec", new LinkedHashMap<>());
        }
        for (Field field : fields) {
            // skip static fields
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            // skip apiVersion, kind, metadata except resource
            HashSet<String> headers = new HashSet<>(Arrays.asList("apiVersion", "kind", "metadata"));
            LinkedHashMap<String, Object> fieldYaml = (LinkedHashMap<String, Object>)fieldToYaml(field);

            if (!isResource) {
                if (headers.contains(field.getName())) continue;
                conf.putAll(fieldYaml);
            } else {
                if (headers.contains(field.getName())) {
                    conf.putAll(fieldYaml);
                } else {
                    Assert(conf.containsKey("spec"), "The field " + field.getName() + " is not in spec");
                    Assert(conf.get("spec") instanceof LinkedHashMap<?,?>, "spec field should be a map");
                    ((LinkedHashMap)conf.get("spec")).putAll(fieldYaml);
                }
            }
        }

        if (conf.containsKey("spec") && ((LinkedHashMap)conf.get("spec")).isEmpty()) {
            conf.remove("spec");
        }
        Config.isResource = isResource;
        return conf;
    }

    private Object fieldToYaml(Field field) {
        try {
            LinkedHashMap<String, Object> result = new LinkedHashMap();
            Object value = field.get(this);
            if (value == null)
                return result;

            String key = field.getName();

            // we use -1 as default value for int
            if (value instanceof Integer && !value.equals(-1))
                result.put(key, value);
            else if (value instanceof Double || value instanceof String)
                result.put(key, value);
            else if (value instanceof Config) {
                result.put(key, ((Config) value).toYaml());
            } else if (value instanceof ArrayList) {
                ArrayList<Object> list = (ArrayList<Object>) value;
                if (list.isEmpty())
                    return result;
                ArrayList<Object> yamlList = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Config)
                        yamlList.add(((Config) o).toYaml());
                    else
                        yamlList.add(o);
                }
                result.put(key, yamlList);
            } else if (value instanceof LinkedHashMap<?,?>) {
                LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) value;
                if (map.isEmpty())
                    return result;
                LinkedHashMap<String, Object> yamlMap = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : map.entrySet()) {

                    if (entry.getValue() instanceof Config)
                        yamlMap.put(entry.getKey(), ((Config) entry.getValue()).toYaml());
                    else
                        yamlMap.put(entry.getKey(), entry.getValue());
                }
                result.put(key, yamlMap);
            }
            return result;
        } catch (IllegalAccessException ignored) {
            System.out.println("Can't resolve " + field.getName() + " in " + this.getClass().getName());
            return new LinkedHashMap<>();
        }
    }

    /**
     * before calling, you should set isResource true
     */
    public void fromYaml(Map<String, Object> yaml) {
        try {
            Class<?> clazz = this.getClass();
            LinkedHashMap<String, Object> spec = (LinkedHashMap<String, Object>) yaml;  // get specification

            // if the config is resource, we need to deal with metadata
            boolean isResource = Config.isResource;
            if (isResource) {
                Config.isResource = false;
                Field field = clazz.getDeclaredField("metadata");
                field.setAccessible(true);

                LinkedHashMap<String, Object> metadata = (LinkedHashMap<String, Object>) yaml.get("metadata");
                LinkedHashMap<String, Object> target = (LinkedHashMap<String, Object>) field.get(this);
                assert target != null;

                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof String)
                        target.put(key, value);
                    else if (value instanceof LinkedHashMap<?,?>) {
                        LinkedHashMap<String, String> map = (LinkedHashMap<String, String>) value;
                        target.put(key, map);
                    }
                }

                spec = (LinkedHashMap<String, Object>) yaml.get("spec");
            }

            // deal with spec
            for (Map.Entry<String, Object> entry: spec.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();

                Field field;
                try {
                    field = clazz.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    System.out.println("The field " + name + " is not existing in frontend model");
                    continue;
                }
                field.setAccessible(true);
                Type targetType = field.getType();

                // the names of all the fields of type Host or ArrayList<Host> contain "host"
                if (field.getType() == DomainHost.class ||
                        field.getType() == IPHost.class ||
                        (field.getType() == ArrayList.class &&
                        (name.contains("host") ||
                         name.contains("Host") ||
                         name.contains("Subnet") ||
                         name.contains("address") ||
                         name.contains("authority")))) {
                    // special handler for host
                    if (value instanceof String) {
                        if (targetType.equals(DomainHost.class)) {
                            if (clazz.getName().endsWith("HTTPRewrite") && name.equals("authority")) {
                                field.set(this, new DomainHost((String) value, DomainHost.DomainKind.FQDN));
                            } else {
                                field.set(this, new DomainHost((String) value));
                            }
                        } else {
                            field.set(this, new IPHost((String) value));
                        }
                    } else if (value instanceof ArrayList<?>) {
                        Type elemType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        ArrayList<String> hosts = (ArrayList<String>) value;
                        ArrayList<Host> target = (ArrayList<Host>) field.get(this);
                        assert target != null;

                        for (String host : hosts) {
                            if (elemType.equals(DomainHost.class)) {
                                target.add(new DomainHost(host));
                            } else {
                                target.add(new IPHost(host));
                            }
                        }
                    }
                    continue;
                }

                if (value instanceof Integer || value instanceof Double || value instanceof String) {
                    // base field type
                    field.set(this, value);
                }
                else if (value instanceof Boolean) {
                    // in my config, all the boolean fields are wrapped
                    field.set(this, new Wrapped.BoolValue((Boolean) value));
                }
                else if (value instanceof ArrayList<?>) {
                    // list field type
                    Type elemType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    ArrayList<Object> list = (ArrayList<Object>) value;
                    ArrayList<Object> target = (ArrayList<Object>) field.get(this);
                    assert target != null;

                    for (Object o: list) {
                        if (elemType.equals(String.class) || elemType.equals(int.class))
                            target.add(o);
                        else {
                            Config config = (Config) Class.forName(elemType.getTypeName()).newInstance();
                            config.fromYaml((LinkedHashMap<String, Object>) o);
                            target.add(config);
                        }
                    }
                }
                else if (value instanceof LinkedHashMap<?,?>) {
                    // map field type;
                    if (targetType.getTypeName().startsWith("frontend")) {
                        // if the target type is config, we just construct it with the map
                        Config config = (Config) Class.forName(targetType.getTypeName()).newInstance();
                        config.fromYaml((LinkedHashMap<String, Object>) value);
                        field.set(this, config);
                    } else {
                        Type valueType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1];
                        LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) value;
                        LinkedHashMap<String, Object> target = (LinkedHashMap<String, Object>) field.get(this);
                        assert target != null;

                        for (Map.Entry<String, Object> e : map.entrySet()) {
                            if (valueType.equals(String.class) || valueType.equals(Integer.class))
                                target.put(e.getKey(), e.getValue());
                            else if (valueType.getTypeName().startsWith("frontend")) {
                                Config config = (Config) Class.forName(valueType.getTypeName()).newInstance();
                                config.fromYaml((LinkedHashMap<String, Object>) e.getValue());
                                target.put(e.getKey(), config);
                            }
                        }
                    }
                }
            }
            Config.isResource = isResource;
        } catch (Exception e) {
//            System.out.println("Can't resolve " + this.getClass().getName() + ", " + e);
        }
    }
}
