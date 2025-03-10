package frontend.istio.component;

import frontend.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A subset of endpoints of a service.
 */
public class Subset extends Config {
    public String name;
    /**
     * Applies a filter over the endpoints of a service.
     */
    public LinkedHashMap<String, String> labels = new LinkedHashMap<>();
    public TrafficPolicy trafficPolicy;

    public void addLabels(Map.Entry<String, String>... labels) {
        for (Map.Entry<String, String> label : labels) {
            this.labels.put(label.getKey(), label.getValue());
        }
    }
}
