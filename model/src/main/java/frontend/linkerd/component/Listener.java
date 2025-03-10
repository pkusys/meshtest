package frontend.linkerd.component;

import frontend.Config;

public class Listener extends Config {
    public String name;
    public Host hostname;
    public Integer port = -1;
    public String protocol;
    public GatewayTLSConfig tls;
}
