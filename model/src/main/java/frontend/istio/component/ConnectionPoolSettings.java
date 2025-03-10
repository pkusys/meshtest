package frontend.istio.component;

import frontend.Config;

/**
 * Control the connection pool for an upstream host.
 */
public class ConnectionPoolSettings extends Config {
    /**
     * Settings to both HTTP and TCP upstreams.
     */
    public static class TCPSettings extends Config {
        public int maxConnections = -1;
        public String connectTimeout;
        public TcpKeepalive tcpKeepalive;
        public String maxConnectionDuration;
    }

    public static class TcpKeepalive extends Config {
        public int probes = -1;
        public String time;
        public String interval;
    }

    public TCPSettings tcp;

    /**
     * Settings to HTTP1.1/HTTP2/GRPC connections.
     */
    public static class HTTPSettings extends Config {
        public static final String DEFAULT = "DEFAULT";
        public static final String DO_NOT_UPGRADE = "DO_NOT_UPGRADE";
        public static final String UPGRADE = "UPGRADE";

        public int http1MaxPendingRequests = -1;
        public int http2MaxRequests = -1;
        public int maxRequestsPerConnection = -1;
        public int maxRetries = -1;
        public String idleTimeout;
        public String h2UpgradePolicy;
        public Wrapped.BoolValue useClientProtocol;

        // NOTE: Here should be a constructor. But since I haven't learned this config enough, I can't solve which fields are used usually.
    }

    public HTTPSettings http;
}
