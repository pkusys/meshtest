package frontend.linkerd.component;

import frontend.Config;

import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayInfrastructure extends Config {
    public LinkedHashMap<String, String> labels = new LinkedHashMap<>();
    public LinkedHashMap<String, String> annotations = new LinkedHashMap<>();
    public Object parametersRef;

    public void addLabels(Map.Entry<String, String> ...entry) {
        for (Map.Entry<String, String> e : entry) {
            labels.put(e.getKey(), e.getValue());
        }
    }

    public void addAnnotations(Map.Entry<String, String> ...entry) {
        for (Map.Entry<String, String> e : entry) {
            annotations.put(e.getKey(), e.getValue());
        }
    }
}
