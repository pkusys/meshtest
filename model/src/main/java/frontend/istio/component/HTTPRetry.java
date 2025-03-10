package frontend.istio.component;

import frontend.Config;

/**
 * Describes the retry policy to use when a HTTP request fails.
 */
public class HTTPRetry extends Config {
    public int attempts = -1;
    public String perTryTimeout;
    /**
     * Conditions under which retry takes place.
     */
    public String retryOn;
    // public BoolValue retryRemoteLocalities;
}
