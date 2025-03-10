package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A set of criterion to be met when the route is applied to the HTTP request.
 * It cannot be empty.
 */
public class HTTPMatchRequest extends Config {
    public String name;

    public static class StringMatch extends Config {
        public static enum MatchType {
            EXACT, PREFIX, REGEX
        }

        public String value;
        public MatchType type;

        public StringMatch(MatchType type, String value) {
            this.type = type;
            this.value = value;
        }

        public StringMatch() {
            this.type = MatchType.EXACT;
            this.value = "default";
        }

        @Override
        public LinkedHashMap<String, Object> toYaml() {
            LinkedHashMap<String, Object> yaml = new LinkedHashMap<>();
            switch (type) {
                case EXACT: yaml.put("exact", value); break;
                case PREFIX: yaml.put("prefix", value); break;
                case REGEX: yaml.put("regex", value); break;
            }
            return yaml;
        }

        @Override
        public void fromYaml(Map<String, Object> yaml) {
            if (yaml.containsKey("exact")) {
                value = (String) yaml.get("exact");
                type = MatchType.EXACT;
            } else if (yaml.containsKey("prefix")) {
                value = (String) yaml.get("prefix");
                type = MatchType.PREFIX;
            } else if (yaml.containsKey("regex")) {
                value = (String) yaml.get("regex");
                type = MatchType.REGEX;
            }
        }
    }

    public StringMatch uri;
    public StringMatch scheme;
    public StringMatch method;
    public StringMatch authority;
    /**
     * Match headers. Keys uri, scheme, method, authority will be ignored.
     */
    public LinkedHashMap<String, StringMatch> headers = new LinkedHashMap<>();
    public int port = -1;
    /**
     * Match labels, especially for a virtual service with multiple gateways.
     */
    public LinkedHashMap<String, String> sourceLabels = new LinkedHashMap<>();
    /**
     * Gateways the rule should be applied to.
     * This rule will override the gateways field in top-level virtual service.
     */
    public ArrayList<String> gateways = new ArrayList<>();
    public LinkedHashMap<String, StringMatch> queryParams = new LinkedHashMap<>();
    /**
     * The case will be ignored only in the case of exact and prefix URI matches.
     */
    public Wrapped.BoolValue ignoreUriCase;
    /**
     * If a header is matched, it will not apply this rule.
     */
    public LinkedHashMap<String, StringMatch> withoutHeaders = new LinkedHashMap<>();
    public String sourceNamespace;
    // public String statPrefix;

    public void addHeaders(Map.Entry<String, StringMatch>... headers) {
        for (Map.Entry<String, StringMatch> header : headers)
            this.headers.put(header.getKey(), header.getValue());
    }

    public void addSourceLabels(Map.Entry<String, String>... sourceLabels) {
        for (Map.Entry<String, String> sourceLabel : sourceLabels)
            this.sourceLabels.put(sourceLabel.getKey(), sourceLabel.getValue());
    }

    public void addGateways(String... gateways) {
        this.gateways.addAll(Arrays.asList(gateways));
    }

    public void addQueryParams(Map.Entry<String, StringMatch>... queryParams) {
        for (Map.Entry<String, StringMatch> queryParam : queryParams)
            this.queryParams.put(queryParam.getKey(), queryParam.getValue());
    }

    public void addWithoutHeaders(Map.Entry<String, StringMatch>... withoutHeaders) {
        for (Map.Entry<String, StringMatch> withoutHeader : withoutHeaders)
            this.withoutHeaders.put(withoutHeader.getKey(), withoutHeader.getValue());
    }
}
