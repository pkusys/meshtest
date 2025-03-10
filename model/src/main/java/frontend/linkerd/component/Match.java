package frontend.linkerd.component;

import frontend.Config;

public class Match extends Config {
    public enum Type {
        Exact,
        PathPrefix,
        RegularExpression,
    }
    public Type type;
    public String name;
    public String value;
}
