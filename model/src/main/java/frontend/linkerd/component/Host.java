package frontend.linkerd.component;

import frontend.Config;

public class Host extends Config {
    public enum Type {
        FQDN,
        Wildcard,
    }
    public Type type;
    public String domain;

    public Host(Type type, String domain) {
        this.type = type;
        this.domain = domain;
    }

    public Host(String domain) {
        if (domain.startsWith("*.")) {
            this.type = Type.Wildcard;
            this.domain = domain.substring(2);
        } else {
            this.type = Type.FQDN;
            this.domain = domain;
        }
    }
}
