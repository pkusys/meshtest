package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the properties of the proxy on a given load balancer port.
 */
public class Server extends Config {
    /**
     * Proxy should listen for incoming connections on this port.
     */
    public Port port;
    /**
     * This field can be IP or Unix Domain Socket (UDS) path.
     */
    public IPHost bind;
    public ArrayList<DomainHost> hosts = new ArrayList<>();
    public ServerTLSSettings tls;
    public String name;

    public void addHosts(List<DomainHost> hosts) {
        this.hosts.addAll(hosts);
    }
}
