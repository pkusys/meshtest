package translator;

import frontend.Config;
import frontend.istio.Gateway;
import frontend.istio.component.Server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class GWTranslator {
    private ArrayList<Config> configs;

    public GWTranslator(ArrayList<Config> configs) {
        this.configs = configs;
    }

    public ArrayList<Object> translateAll() {
        ArrayList<Object> results = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof Gateway gw) {
                results.add(translate(gw));
            }
        }
        return results;
    }

    private Object translate(Gateway gw) {
        LinkedHashMap<String, Object> gtw = new LinkedHashMap<>();
        gtw.put("apiVersion", "gateway.networking.k8s.io/v1");
        gtw.put("kind", "Gateway");
        gtw.put("metadata", new LinkedHashMap<String, Object>(
                Map.of("name", gw.metadata.get("name"))
        ));

        LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
        spec.put("gatewayClassName", Translator.gatewayClassName);

        ArrayList<Object> listeners = new ArrayList<>();
        for (Server server: gw.servers) {
            LinkedHashMap<String, Object> listener = new LinkedHashMap<>();
            listener.put("name", server.port.name);
            listener.put("port", server.port.number);
            listener.put("protocol", "HTTP");
            listener.put("hostname", server.hosts.get(0).domain);
            listener.put("allowedRoutes", new LinkedHashMap<>(Map.of(
                    "namespaces", new LinkedHashMap<>(Map.of("from", "All"))
            )));
            listeners.add(listener);
        }
        spec.put("listeners", listeners);

        gtw.put("spec", spec);
        return gtw;
    }
}
