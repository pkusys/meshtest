package frontend.istio.component;

public class DomainHost extends Host {
    public enum DomainKind {
        FQDN, WILDCARD
    }

    public DomainKind kind;
    public String domain;
    public int port = -1;   // port in the host header

    public DomainHost(String domain) {
        if (domain.contains(":")) {
            String[] parts = domain.split(":");
            domain = parts[0];
            this.port = Integer.parseInt(parts[1]);
        }

        this.kind = domain.contains("*") ? DomainKind.WILDCARD : DomainKind.FQDN;
        if (domain.contains("/")) {
            // domain with name space
            String[] parts = domain.split("/");
            String ns = parts[0];
            String name = parts[1];

            if(this.kind == DomainKind.WILDCARD) {
                this.domain = "*." + ns + ".svc.cluster.local";
            } else {
                String shortName = name.split("\\.")[0];
                this.domain = shortName + "." + ns + ".svc.cluster.local";
            }
        } else {
            if (domain.contains(".") || domain.contains("*")) {
                this.domain = domain;
            } else {
                this.domain = domain + ".default.svc.cluster.local";
            }
        }
    }

    // for pseudo header value, e.g. *.bookinfo.com
    public DomainHost(String domain, DomainKind kind) {
        this.domain = domain;
        this.kind = kind;
    }

    public DomainHost() {
        domain = "default";
        kind = DomainKind.FQDN;
    }

    @Override
    public String toYaml() {
        return domain;
    }
}
