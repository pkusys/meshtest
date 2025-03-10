package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Load balancing policy, connection pool sizes, outlier detection, etc.
 */
public class TrafficPolicy extends Config {
    public LoadBalancerSettings loadBalancer;
    /**
     * Control the volume of connections to an upstream service.
     */
    public ConnectionPoolSettings connectionPool;
    /**
     * Control eviction of unhealthy hosts from the load balancing pool.
     */
    public OutlierDetection outlierDetection;
    public ClientTLSSettings tls;

    public static class PortTrafficPolicy extends Config {
        public Wrapped.PortSelector port;
        public LoadBalancerSettings loadBalancer;
        public ConnectionPoolSettings connectionPool;
        public OutlierDetection outlierDetection;
        public ClientTLSSettings tls;
    }
    public ArrayList<PortTrafficPolicy> portLevelSettings = new ArrayList<>();

    public static class TunnelSettings extends Config {
        public String protocol;
        public String targetHost;
        public int targetPort = -1;
    }

    /**
     * Configuration of tunneling TCP over other transport or application layers.
     */
    public TunnelSettings tunnel;

    public void addPortTrafficPolicy(PortTrafficPolicy... portTrafficPolicies) {
        portLevelSettings.addAll(Arrays.asList(portTrafficPolicies));
    }
}
