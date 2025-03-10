package translator;

import frontend.Config;
import frontend.istio.ServiceEntry;
import frontend.istio.VirtualService;
import frontend.istio.component.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static utils.AssertionHelper.Assert;

public class VSTranslator {
    private ArrayList<Config> configs;

    public VSTranslator(ArrayList<Config> configs) {
        this.configs = configs;
    }

    public ArrayList<Object> translateAll() {
        ArrayList<Object> results = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof VirtualService vs) {
                // We assume that delegate VirtualService won't lead to bugs.
                // Since the merge between delegation root and succ is done
                // by ourselves manually.
                if (!vs.hosts.isEmpty()) {
                    results.add(translate(vs));
                }
            }
        }
        return results;
    }

    private Object translate(VirtualService vs) {
        LinkedHashMap<String, Object> hr = new LinkedHashMap<>();
        hr.put("apiVersion", "gateway.networking.k8s.io/v1");
        hr.put("kind", "HTTPRoute");
        hr.put("metadata", new LinkedHashMap<String, Object>(
                Map.of("name", Translator.getHRName())
        ));

        LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
        ArrayList<Object> parentRefs = new ArrayList<>();
        for (String name: vs.gateways) {
            if (name.equals("mesh")) {
                continue;
            }
            parentRefs.add(new LinkedHashMap<String, Object>(
                    Map.of("name", name,
                            "kind", "Gateway",
                            "group", "gateway.networking.k8s.io")
            ));
        }
        ArrayList<Integer> ports = getSEPortsByHost(vs.hosts.get(0).domain);
        for (Integer port: ports) {
            parentRefs.add(new LinkedHashMap<String, Object>(
                    Map.of("name", Translator.defaultSvc,
                            "kind", "Service",
                            "group", "",
                            "port", port)
            ));
        }
        if (!ports.contains(80)) {
            parentRefs.add(new LinkedHashMap<String, Object>(
                    Map.of("name", Translator.defaultSvc,
                            "kind", "Service",
                            "group", "",
                            "port", 80)
            ));
        }
        spec.put("parentRefs", parentRefs);

        String hostname = vs.hosts.get(0).domain;
        if (Translator.hostnameEffect) {
            ArrayList<String> hostnames = new ArrayList<>();
            for (DomainHost host : vs.hosts) {
                hostnames.add(host.domain);
            }
            spec.put("hostnames", hostnames);
        }

        ArrayList<Object> rules = new ArrayList<>();
        for (HTTPRoute route: vs.http) {
            Assert(route.delegate == null, "Now we don't support to translate delegation into k8s Gateway API.");
            LinkedHashMap<String, Object> rule = new LinkedHashMap<>();

            ArrayList<Object> matches = new ArrayList<>();
            matches.add(translateMatch(route.match.get(0), hostname));
            rule.put("matches", matches);

            if (route.rewrite != null || route.headers != null) {
                ArrayList<Object> filters = new ArrayList<>();
                if (route.rewrite != null)
                    filters.add(translateRewrite(route.rewrite));
                if (route.headers != null)
                    filters.add(translateHeaders(route.headers));
                rule.put("filters", filters);
            }

            ArrayList<Object> backendRefs = new ArrayList<>();
            LinkedHashMap<String, Object> backendRef = new LinkedHashMap<>();
            backendRef.put("name", route.route.get(0).destination.host.domain.split("\\.")[0]);
            backendRef.put("port", route.route.get(0).destination.port.number);
            // For a backend, there may be RequestHeaderModifier.
            if (route.route.get(0).headers != null) {
                ArrayList<Object> filters = new ArrayList<>();
                filters.add(translateHeaders(route.route.get(0).headers));
                backendRef.put("filters", filters);
            }
            backendRefs.add(backendRef);
            rule.put("backendRefs", backendRefs);

            rules.add(rule);
        }
        spec.put("rules", rules);

        hr.put("spec", spec);
        return hr;
    }

    private Object translateMatch(HTTPMatchRequest match, String hostname) {
        LinkedHashMap<String, Object> Match = new LinkedHashMap<>();
        if (match.uri != null) {
            Match.put("path", new LinkedHashMap<>(
                    Map.of("type", matchType(match.uri.type),
                            "value", match.uri.value)
            ));
        }
        if (!Translator.hostnameEffect || !match.headers.isEmpty() || match.authority != null) {
            ArrayList<Object> headers = new ArrayList<>();
            for (Map.Entry<String, HTTPMatchRequest.StringMatch> entry: match.headers.entrySet()) {
                Assert(!entry.getKey().startsWith(":"), "Pseudo headers are invalid in k8s Gateway API.");
                headers.add(new LinkedHashMap<>(
                        Map.of("type", matchType(entry.getValue().type),
                                "name", entry.getKey(),
                                "value", entry.getValue().value)
                ));
            }
            if (!Translator.hostnameEffect) {
                String type = hostname.contains("*") ? "RegularExpression" : "Exact";
                headers.add(new LinkedHashMap<>(
                        Map.of("type", type,
                                "name", "host",
                                "value", type.equals("Exact") ? hostname : Translator.regexReformat(hostname))
                ));
            } else if (match.authority != null) {
                headers.add(new LinkedHashMap<>(
                        Map.of("type", "Exact",
                                "name", "host",
                                "value", match.authority.value)
                ));
            }
            Match.put("headers", headers);
        }
        if (match.method != null) {
            Match.put("method", match.method.value);
        }
        return Match;
    }

    private Object translateRewrite(HTTPRewrite rewrite) {
        LinkedHashMap<String, Object> Filter = new LinkedHashMap<>();
        Filter.put("type", "URLRewrite");

        LinkedHashMap<String, Object> urlRewrite = new LinkedHashMap<>();
        if (rewrite.authority != null) {
            urlRewrite.put("hostname", rewrite.authority.domain);
        }
        if (rewrite.uri != null) {
            urlRewrite.put("path", new LinkedHashMap<>(
                    Map.of("type", "ReplaceFullPath",
                            "replaceFullPath", rewrite.uri)
            ));
        }
        Filter.put("urlRewrite", urlRewrite);

        return Filter;
    }

    private Object translateHeaders(Headers headers) {
        LinkedHashMap<String, Object> Filter = new LinkedHashMap<>();
        Filter.put("type", "RequestHeaderModifier");
        LinkedHashMap<String, Object> requestHeaderModifier = new LinkedHashMap<>();

        if (!headers.request.set.isEmpty()) {
            ArrayList<Object> set = new ArrayList<>();
            for (Map.Entry<String, String> entry: headers.request.set.entrySet()) {
                Assert(!entry.getKey().startsWith(":"), "Pseudo headers are invalid in k8s Gateway API.");
                set.add(new LinkedHashMap<>(
                        Map.of("name", entry.getKey(),
                                "value", entry.getValue())
                ));
            }
            requestHeaderModifier.put("set", set);
        }
        if (!headers.request.add.isEmpty()) {
            ArrayList<Object> add = new ArrayList<>();
            for (Map.Entry<String, String> entry: headers.request.add.entrySet()) {
                Assert(!entry.getKey().startsWith(":"), "Pseudo headers are invalid in k8s Gateway API.");
                add.add(new LinkedHashMap<>(
                        Map.of("name", entry.getKey(),
                                "value", entry.getValue())
                ));
            }
            requestHeaderModifier.put("add", add);
        }
        if (!headers.request.remove.isEmpty()) {
            ArrayList<String> remove = new ArrayList<>(headers.request.remove);
            requestHeaderModifier.put("remove", remove);
        }

        Filter.put("requestHeaderModifier", requestHeaderModifier);
        return Filter;
    }

    private ArrayList<Integer> getSEPortsByHost(String host) {
        HashSet<Integer> ports = new HashSet<>();
        ArrayList<String> hosts = hostFamily(host);
        for (Config config: configs) {
            if (config instanceof ServiceEntry se) {
                if (hosts.contains(se.hosts.get(0).domain)) {
                    for (Port port: se.ports) {
                        ports.add(port.number);
                    }
                }
            }
        }
        return new ArrayList<>(ports);
    }

    private ArrayList<String> hostFamily(String host) {
        ArrayList<String> family = new ArrayList<>();
        family.add(host);
        if (host.startsWith("*.")) {
            family.add("www." + host.substring(2));
        } else {
            // Assume that host starts with www.
            family.add("*." + host.substring(4));
        }
        return family;
    }

    private String matchType(HTTPMatchRequest.StringMatch.MatchType type) {
        return switch (type) {
            case EXACT -> "Exact";
            case PREFIX -> "PathPrefix";
            case REGEX -> "RegularExpression";
        };
    }
}
