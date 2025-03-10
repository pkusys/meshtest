package frontend.istio.component;

import frontend.Config;

/**
 * Defines the destination of traffic that matches a route.
 */
public class Destination extends Config {
    /**
     * Include service defined by service entry.
     */
    public DomainHost host;
    public String subset;
    public Wrapped.PortSelector port;
}
