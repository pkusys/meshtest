package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalityLoadBalancerSetting extends Config {
    public static class Distribute extends Config {
        public String from;
        public LinkedHashMap<String, Integer> to = new LinkedHashMap<>();

        @Override
        public LinkedHashMap<String, Object> toYaml() {
            LinkedHashMap<String, Object> yaml = new LinkedHashMap<>();
            yaml.put("from", from);
            LinkedHashMap<String, Object> toYaml = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : to.entrySet()) {
                toYaml.put(entry.getKey(), entry.getValue());
            }
            yaml.put("to", toYaml);
            return yaml;
        }
    }

    /**
     * Describes how load balance across different zones.
     */
    public ArrayList<Distribute> distribute = new ArrayList<>();

    public static class Failover extends Config {
        public String from;
        public String to;
    }

    /**
     * Describes when pod gets unhealthy, how traffic is distributed.
     */
    public ArrayList<Failover> failover = new ArrayList<>();
    public ArrayList<String> failoverPriority = new ArrayList<>();
    /**
     * This field will overwrite mesh level config.
     */
    public Wrapped.BoolValue enabled;

    public void addDistribute(Distribute... distribute) {
        this.distribute.addAll(Arrays.asList(distribute));
    }

    public void addFailover(Failover... failover) {
        this.failover.addAll(Arrays.asList(failover));
    }

    public void addFailoverPriority(String... failoverPriority) {
        this.failoverPriority.addAll(Arrays.asList(failoverPriority));
    }
}
