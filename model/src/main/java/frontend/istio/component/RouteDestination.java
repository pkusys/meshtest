package frontend.istio.component;

import frontend.Config;

/**
 * Associated a rule with one or more service versions.
 */
public class RouteDestination extends Config {
    public Destination destination;
    public int weight = -1;
    public Headers headers;
}
