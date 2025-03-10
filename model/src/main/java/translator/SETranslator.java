package translator;

import frontend.Config;
import frontend.istio.Service;
import frontend.istio.ServiceEntry;
import frontend.istio.component.DomainHost;
import frontend.istio.component.Port;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class SETranslator {
    private ArrayList<Config> configs;

    public SETranslator(ArrayList<Config> configs) {
        this.configs = configs;
    }

    public ArrayList<Object> translateAll() {
        ArrayList<Object> results = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof ServiceEntry se) {
                // For ServiceEntry with resolution STATIC,
                // we create a HTTPRoute for it
                if (se.resolution.equals("STATIC")) {
                    results.add(translate(se));
                    // If this backend Service does not exist, we need to create a new one
                    Object svc = createSvcIfNotExist(se.workloadSelector.labels.get("app"));
                    if (svc != null) {
                        results.add(svc);
                    }
                }
            }
        }
        return results;
    }

    private Object translate(ServiceEntry se) {
        LinkedHashMap<String, Object> hr = new LinkedHashMap<>();
        hr.put("apiVersion", "gateway.networking.k8s.io/v1");
        hr.put("kind", "HTTPRoute");
        hr.put("metadata", new LinkedHashMap<String, Object>(
                Map.of("name", Translator.getHRName())
        ));

        LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
        ArrayList<Object> parentRefs = new ArrayList<>();
        for (Port port: se.ports) {
            parentRefs.add(new LinkedHashMap<String, Object>(
                    Map.of("name", Translator.defaultSvc,
                            "kind", "Service",
                            "group", "",
                            "port", port.number)
            ));
        }
        spec.put("parentRefs", parentRefs);

        String hostname = se.hosts.get(0).domain;
        if (Translator.hostnameEffect) {
            ArrayList<String> hostnames = new ArrayList<>();
            for (DomainHost host : se.hosts) {
                hostnames.add(host.domain);
            }
            spec.put("hostnames", hostnames);
        }

        ArrayList<Object> rules = new ArrayList<>();
        ArrayList<Object> backendRefs = new ArrayList<>();
        String svcName = se.workloadSelector.labels.get("app");
        backendRefs.add(new LinkedHashMap<String, Object>(
                Map.of("name", svcName,
                        "port", Translator.defaultPort)
        ));

        ArrayList<Object> matches = new ArrayList<>();
        if (!Translator.hostnameEffect) {
            LinkedHashMap<String, Object> match = new LinkedHashMap<>();
            ArrayList<Object> headers = new ArrayList<>();
            String type = hostname.contains("*") ? "RegularExpression" : "Exact";
            headers.add(new LinkedHashMap<String, Object>(
                    Map.of("name", "host",
                            "type", type,
                            "value", type.equals("Exact") ? hostname : Translator.regexReformat(hostname))
            ));
            match.put("headers", headers);
            matches.add(match);
        }

        rules.add(new LinkedHashMap<String, Object>(
                Map.of("matches", matches,
                        "backendRefs", backendRefs)
        ));
        spec.put("rules", rules);

        hr.put("spec", spec);
        return hr;
    }

    private Object createSvcIfNotExist(String svcName) {
        for (Config config: configs) {
            if (config instanceof Service svc) {
                if (svc.metadata.get("name").equals(svcName)) {
                    return null;
                }
            }
        }
        LinkedHashMap<String, Object> svc = new LinkedHashMap<>();
        svc.put("apiVersion", "v1");
        svc.put("kind", "Service");
        svc.put("metadata", new LinkedHashMap<String, Object>(
                Map.of("name", svcName)
        ));

        LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
        LinkedHashMap<String, String> selector = new LinkedHashMap<>();
        selector.put("app", svcName);
        spec.put("selector", selector);

        ArrayList<Object> ports = new ArrayList<>();
        ports.add(new LinkedHashMap<String, Object>(
                Map.of("port", Translator.defaultPort,
                        "targetPort", 9080)
        ));
        spec.put("ports", ports);

        svc.put("spec", spec);
        return svc;
    }
}
