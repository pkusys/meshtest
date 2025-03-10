package frontend.linkerd.component;

import frontend.Config;

public class Address extends Config {
    public enum Type {
        Hostname,
        IPAddress,
        NamedAddress,
    }
    public Type type;
    public String value;
}
