package frontend.istio;

import frontend.Config;
import frontend.istio.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Enables adding additional entries into Istio's internal service registry.
 */
public class ServiceEntry extends Config {
    public String apiVersion = "networking.istio.io/v1alpha3";
    public String kind = "ServiceEntry";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    /**
     * The hosts associated with the service entry.
     */
    public ArrayList<DomainHost> hosts = new ArrayList<>();
    /**
     * Virtual IP addresses, which is used for accessing the service in the mesh.
     * The default address class is Host.
     */
    public ArrayList<IPHost> addresses = new ArrayList<>();
    public ArrayList<Port> ports = new ArrayList<>();

    public static final String MESH_EXTERNAL = "MESH_EXTERNAL";
    public static final String MESH_INTERNAL = "MESH_INTERNAL";

    public String location;

    public static final String NONE = "NONE";
    public static final String  STATIC = "STATIC";
    public static final String DNS = "DNS";
    public static final String DNS_ROUND_ROBIN = "DNS_ROUND_ROBIN";

    /**
     * Determines how the proxy will resolve the IP addresses of the network endpoints associated with this service.
     */
    public String resolution;
    public ArrayList<WorkloadEntry> endpoints = new ArrayList<>();
    /**
     * Only for MESH_INTERNAL services.
     */
    public WorkloadSelector workloadSelector;
    public ArrayList<String> exportTo = new ArrayList<>();
    public ArrayList<String> subjectAltNames = new ArrayList<>();

    public void addHosts(List<DomainHost> hosts) {
        this.hosts.addAll(hosts);
    }

    public void addAddresses(List<IPHost> addresses) {
        this.addresses.addAll(addresses);
    }

    public void addPorts(List<Port> ports) {
        this.ports.addAll(ports);
    }

    public void addEndpoints(WorkloadEntry... endpoints) {
        this.endpoints.addAll(Arrays.asList(endpoints));
    }

    public void addExportTo(String... exportTo) {
        this.exportTo.addAll(Arrays.asList(exportTo));
    }

    public void addSubjectAltNames(String... subjectAltNames) {
        this.subjectAltNames.addAll(Arrays.asList(subjectAltNames));
    }
}
