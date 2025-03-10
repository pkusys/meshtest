package frontend.istio.component;

import frontend.Config;

import java.util.LinkedHashMap;

/**
 * Describes the delegate virtual service.
 */
public class Delegate extends Config {
    public DomainHost name;
    public String namespace;

    @Override
    public LinkedHashMap<String, Object> toYaml() {
        LinkedHashMap<String, Object> yaml = new LinkedHashMap<>();
        String vsName = name.domain.split("\\.")[0];
        yaml.put("name", vsName);
        if (namespace != null) {
            yaml.put("namespace", namespace);
        }
        return yaml;
    }
}
