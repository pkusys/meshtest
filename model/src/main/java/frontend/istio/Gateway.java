package frontend.istio;

import frontend.Config;
import frontend.istio.component.Server;

import java.util.*;

/**
 * Describes a load balancer operating at the edge of the mesh receiving incoming or outgoing HTTP/TCP connections.
 */
public class Gateway extends Config {
    public String apiVersion = "networking.istio.io/v1alpha3";
    public String kind = "Gateway";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public ArrayList<Server> servers = new ArrayList<>();
    public LinkedHashMap<String, String> selector = new LinkedHashMap<>();

    public void addServers(List<Server> servers) {
        this.servers.addAll(servers);
    }

    public void addSelector(Map.Entry<String, String>... selector) {
        this.selector.putAll(Arrays.stream(selector).collect(LinkedHashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), Map::putAll));
    }
}
