package frontend.linkerd.component;

import frontend.Config;

public class PathModifier extends Config {
    public enum Type {
        ReplaceFullPath,
        ReplacePrefixMatch,
    }
    public String replaceFullPath;
    public String replacePrefixMatch;
}
