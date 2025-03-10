package frontend.linkerd;

import frontend.Config;
import frontend.linkerd.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class Gateway extends Config {
    public String apiVersion = "gateway.networking.k8s.io/v1";
    public String kind = "Gateway";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public ArrayList<Address> addresses = new ArrayList<>();
    public GatewayBackendTLS backendTLS;
    public String gatewayClassName;
    public GatewayInfrastructure infrastructure;
    public ArrayList<Listener> listeners = new ArrayList<>();

    public void addAddresses(Address... addresses) {
        this.addresses.addAll(Arrays.asList(addresses));
    }

    public void addListeners(Listener... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
    }
}
