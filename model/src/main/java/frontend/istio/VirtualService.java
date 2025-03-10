package frontend.istio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import frontend.Config;
import frontend.istio.component.DomainHost;
import frontend.istio.component.HTTPRoute;
import frontend.istio.component.TCPRoute;

/**
 * Config affecting traffic routing.
 */
public class VirtualService extends Config {
    public String apiVersion = "networking.istio.io/v1alpha3";
    public String kind = "VirtualService";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    /**
     * Could be short-name, IP address or DNS name with wildcard prefix.
     * But short-name will be resolved with the namespace of the virtual service in k8s.
     * IP address are allowed only for services defined via gateway.
     * This field should be empty for a delegate virtual service.
     */
    public ArrayList<DomainHost> hosts = new ArrayList<>();
    /**
     * When using gateway in other namespace, the namespace should be specified.
     * Reserved value mesh implies all the sidecars in this mesh, and is the default value.
     */
    public ArrayList<String> gateways = new ArrayList<>();
    /**
     * Http, tls and tcp follow the first matching an incoming request.
     */
    public ArrayList<HTTPRoute> http = new ArrayList<>();
    public ArrayList<TCPRoute> tls = new ArrayList<>();
    /**
     * Since TCPRoute is like TLSRoute, so we choose to use TLSRoute instead.
     */
    public ArrayList<TCPRoute> tcp = new ArrayList<>();
    /**
     * This field implies which namespace the virtual service is applied to, with applying in all namespaces as default.
     * Value . implies the namespace of the virtual service, while value * implies all namespaces.
     */
    public ArrayList<String> exportTo = new ArrayList<>();

    public void addHosts(List<DomainHost> hosts) {
        this.hosts.addAll(hosts);
    }

    public void addGateways(List<String> gateways) {
        this.gateways.addAll(gateways);
    }

    public void addHttp(List<HTTPRoute> http) {
        this.http.addAll(http);
    }

    public void addTls(List<TCPRoute> tls) {
        this.tls.addAll(tls);
    }

    public void addTcp(List<TCPRoute> tcp) {
        this.tcp.addAll(tcp);
    }

    public void addExportTo(List<String> exportTo) {
        this.exportTo.addAll(exportTo);
    }
}
