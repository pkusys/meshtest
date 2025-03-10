package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;

public class HTTPRouteRule extends Config {
    public String name;
    public ArrayList<HTTPRouteMatch> matches = new ArrayList<>();
    public ArrayList<HTTPRouteFilter> filters = new ArrayList<>();
    public ArrayList<HTTPBackendRef> backendRefs = new ArrayList<>();
    public HTTPRouteTimeouts timeouts;
    public HTTPRouteRetry retry;

    public void addMatches(HTTPRouteMatch... matches) {
        this.matches.addAll(java.util.Arrays.asList(matches));
    }

    public void addFilters(HTTPRouteFilter... filters) {
        this.filters.addAll(java.util.Arrays.asList(filters));
    }

    public void addBackendRefs(HTTPBackendRef... backendRefs) {
        this.backendRefs.addAll(java.util.Arrays.asList(backendRefs));
    }
}
