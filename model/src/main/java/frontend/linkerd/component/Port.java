package frontend.linkerd.component;

import frontend.Config;

public class Port extends Config {
    public int port = -1;
    public int targetPort = -1;
    public String protocol;
    public String name;
    public int nodePort = -1;
    public String appProtocol;
}
