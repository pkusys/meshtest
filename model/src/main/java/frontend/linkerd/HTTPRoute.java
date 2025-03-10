package frontend.linkerd;

import frontend.Config;
import frontend.linkerd.component.HTTPRouteRule;
import frontend.linkerd.component.Host;
import frontend.linkerd.component.Object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class HTTPRoute extends Config {
    public String apiVersion = "gateway.networking.k8s.io/v1";
    public String kind = "HTTPRoute";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public ArrayList<Object> parentRefs = new ArrayList<>();
    public ArrayList<Host> hostnames = new ArrayList<>();
    public ArrayList<HTTPRouteRule> rules = new ArrayList<>();

    public void addParentRefs(Object... parentRefs) {
        this.parentRefs.addAll(Arrays.asList(parentRefs));
    }

    public void addHostnames(Host... hostnames) {
        this.hostnames.addAll(Arrays.asList(hostnames));
    }

    public void addRules(HTTPRouteRule... rules) {
        this.rules.addAll(Arrays.asList(rules));
    }
}
