package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TLS connection match attributes.
 */
public class L4MatchAttributes extends Config {
    /**
     * SNI to match on.
     */
    public ArrayList<DomainHost> sniHosts = new ArrayList<>();
    /**
     * This field can be subnet or just IP address.
     */
    public ArrayList<IPHost> destinationSubnets = new ArrayList<>();
    /**
     * When the service only expose single port or label ports with the protocol it supported, this field can be omitted.
     */
    public int port = -1;
    public LinkedHashMap<String, String> sourceLabels = new LinkedHashMap<>();
    public ArrayList<String> gateways = new ArrayList<>();
    public String sourceNamespace;

    public void addSniHosts(DomainHost... hosts) {
        sniHosts.addAll(Arrays.asList(hosts));
    }

    public void addDestinationSubnets(IPHost... hosts) {
        destinationSubnets.addAll(Arrays.asList(hosts));
    }

    public void addSourceLabels(Map.Entry<String, String>... entries) {
        for (Map.Entry<String, String> entry : entries) {
            sourceLabels.put(entry.getKey(), entry.getValue());
        }
    }

    public void addGateways(String... gateways) {
        this.gateways.addAll(Arrays.asList(gateways));
    }
}
