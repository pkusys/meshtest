package frontend.linkerd.component;

import frontend.Config;

public class Redirect extends Config {
    public String scheme;
    public Host hostname;
    public PathModifier path;
    public Integer port = -1;
    public Integer statusCode = -1;
}
