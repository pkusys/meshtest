package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Describes match conditions and actions for routing HTTP/1.1, HTTP2 and gRPC traffic.
 */
public class HTTPRoute extends Config {
    public String name;
    /**
     * AND within a single block, OR between blocks.
     */
    public ArrayList<HTTPMatchRequest> match = new ArrayList<>();
    /**
     * Forward the http traffic while every route has a weight.
     */
    public ArrayList<RouteDestination> route = new ArrayList<>();
    public HTTPRedirect redirect;
    /**
     * Only used when route and redirect are empty.
     */
    public HTTPDirectResponse directResponse;
    /**
     * Only used when route and redirect are empty.
     * Only one level delegation is supported.
     * The match should be strict subset of the root.
     */
    public Delegate delegate;
    /**
     * Rewrite URI or headers. It will be performed before forwarding.
     */
    public HTTPRewrite rewrite;
    public String timeout;
    public HTTPRetry retries;
    /**
     * Only used when timeout and retries are empty.
     */
    public HTTPFaultInjection fault;
    /**
     * Mirror traffic to another destination to realize statistical analysis.
     */
    public Destination mirror;
    public Wrapped.Percent mirrorPercentage;
    public CorsPolicy corsPolicy;
    public Headers headers;

    public void addMatch(HTTPMatchRequest... match) {
        this.match.addAll(Arrays.asList(match));
    }

    public void addRoute(RouteDestination... route) {
        this.route.addAll(Arrays.asList(route));
    }
}
