package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Describes match conditions and actions for routing unterminated TLS traffic(TLS/HTTPS).
 */
public class TCPRoute extends Config {
    /**
     * AND within a single block, OR between blocks.
     */
    public ArrayList<L4MatchAttributes> match = new ArrayList<>();

    /**
     * This field should be of type RouteDestination.
     * But we use HTTPRouteDestination instead, which headers should always be null.
     */
    public ArrayList<RouteDestination> route = new ArrayList<>();

    public void addMatch(L4MatchAttributes... matchAttributes) {
        match.addAll(Arrays.asList(matchAttributes));
    }

    public void addRoute(RouteDestination... routeDestinations) {
        route.addAll(Arrays.asList(routeDestinations));
    }
}
