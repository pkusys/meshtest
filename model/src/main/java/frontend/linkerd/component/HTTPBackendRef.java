package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

public class HTTPBackendRef extends Config {
    public Object backendRef;
    public Integer weight = -1;
    public ArrayList<HTTPRouteFilter> filters = new ArrayList<>();

    public void addFilters(HTTPRouteFilter ...filters) {
        this.filters.addAll(Arrays.asList(filters));
    }
}
