package frontend.istio;

import frontend.Config;
import frontend.istio.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Defines policies that apply to traffic intended for a service after routing has occurred.
 */
public class DestinationRule extends Config {
    public String apiVersion = "networking.istio.io/v1alpha3";
    public String kind = "DestinationRule";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    /**
     * The name of a service.
     * For service defined by service entry, it will be looked up from the hosts declared.
     */
    public DomainHost host;
    /**
     * Load balancing policy, connection pool sizes, outlier detection, etc.
     */
    public TrafficPolicy trafficPolicy;
    public ArrayList<Subset> subsets = new ArrayList<>();
    public ArrayList<String> exportTo = new ArrayList<>();
    /**
     * Criteria used to select the specific set of pods/VMs that should be applied to.
     */
    public WorkloadSelector workloadSelector;

    public void addSubsets(List<Subset> subsets) {
        this.subsets.addAll(subsets);
    }

    public void addExportTo(String... exportTo) {
        this.exportTo.addAll(Arrays.asList(exportTo));
    }
}
