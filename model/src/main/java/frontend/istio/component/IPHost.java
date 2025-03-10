package frontend.istio.component;

public class IPHost extends Host {
    public enum IPKind {
        IP, SUBNET
    }

    public IPKind kind;
    public String ip;

    public IPHost(String ip) {
        this.kind = ip.contains("/") ? IPKind.SUBNET : IPKind.IP;
        this.ip = ip;
    }

    @Override
    public String toYaml() {
        return ip;
    }
}
