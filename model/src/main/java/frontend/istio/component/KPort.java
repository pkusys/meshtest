package frontend.istio.component;

import frontend.Config;

/**
 * Kubernetes Port
 */
public class KPort extends Config {
    public int port = -1;
    /**
     * Here we assume targetPort is a integer
     */
    public int targetPort = -1;
    public String protocol;
    public String name;
    public int nodePort = -1;
    public String appProtocol;
}
