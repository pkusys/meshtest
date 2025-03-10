package frontend.istio.component;

import frontend.Config;

public class HTTPRewrite extends Config {
    /**
     * Rewrite the Path portion of the URI with this value.
     */
    public String uri;
    /**
     * Rewrite the Authority portion of the URI with this value.
     */
    public DomainHost authority;

    public static class RegexWrite extends Config {
        public String match;
        public String rewrite;

        @Override
        public String toYaml() {
            StringBuilder yaml = new StringBuilder("{");
            if (match != null) {
                yaml.append("match: \"").append(match).append("\", ");
            }
            if (rewrite != null) {
                yaml.append("rewrite: ").append(rewrite).append(", ");
            }
            yaml.append("}");
            return yaml.toString();
        }
    }

    public RegexWrite uriRegexRewrite;
}
