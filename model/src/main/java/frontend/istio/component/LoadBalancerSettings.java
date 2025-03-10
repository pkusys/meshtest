package frontend.istio.component;

import frontend.Config;

/**
 * Load balance policy.
 */
public class LoadBalancerSettings extends Config {
    public static final String UNSPECIFIED = "UNSPECIFIED";
    public static final String RANDOM = "RANDOM";
    public static final String PASSTHROUGH = "PASSTHROUGH";
    public static final String ROUND_ROBIN = "ROUND_ROBIN";
    public static final String LEAST_CONN = "LEAST_CONN";
    public static final String LEAST_REQUEST = "LEAST_REQUEST";

    public String simple;

    public static class ConsistentHashLB extends Config {
        public String httpHeaderName;
        public HTTPCookie httpCookie;
        public Wrapped.BoolValue useSourceIp;
        public String httpQueryParameterName;
        public RingHash ringHash;
        public MagLev maglev;
        public int minimumRingSize = -1;
    }

    public static class HTTPCookie extends Config {
        public String name;
        public String path;
        public String ttl;
    }

    public static class RingHash extends Config {
        public int minimumRingSize = -1;
    }

    public static class MagLev extends Config {
        public int tableSize = -1;
    }

    public ConsistentHashLB consistentHash;
    /**
     * This field will overwrite mesh level config.
     */
    public LocalityLoadBalancerSetting localityLbSetting;
    public String warmupDurationSecs;
}
