package frontend.istio;

import frontend.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enables specifying the properties of a single non-k8s workload such a VM or a bare metal services that can be referred to by service entries.
 * It must be used with service entry.
 */
public class WorkloadEntry extends Config {
    public String apiVersion = "networking.istio.io/v1alpha3";
    public String kind = "WorkloadEntry";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public String address;
    public LinkedHashMap<String, Integer> ports = new LinkedHashMap<>();
    public LinkedHashMap<String, String> labels = new LinkedHashMap<>();
    public String network;
    public String locality;
    /**
     * Load balancing weight.
     */
    public int weight = -1;
    public String serviceAccount;

    public void addPorts(Map.Entry<String, Integer>... ports) {
        for (Map.Entry<String, Integer> port : ports) {
            this.ports.put(port.getKey(), port.getValue());
        }
    }

    public void addLabels(Map.Entry<String, String>... labels) {
        for (Map.Entry<String, String> label : labels) {
            this.labels.put(label.getKey(), label.getValue());
        }
    }
}
