package frontend.linkerd.component;

import frontend.Config;

public class HTTPRouteFilter extends Config {
    public enum Type {
        ExtensionRef,
        RequestHeaderModifier,
        RequestMirror,
        RequestRedirect,
        ResponseHeaderModifier,
        URLRewrite,
    }
    public Type type;
    public HeaderModifier requestHeaderModifier;
    public HeaderModifier responseHeaderModifier;
    public Redirect requestRedirect;
    public URLRewrite urlRewrite;
}
