package translator;

import frontend.Config;
import frontend.istio.Pod;
import frontend.istio.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Translator {
    public static final String defaultSvc = "back";
    public static final Integer defaultPort = 9080;
    public static final String gatewayClassName = "contour";
    public static final Boolean hostnameEffect = true;
    private static Integer hrCounter = 0;
    private ArrayList<Config> configs;
    private ArrayList<Object> results;
    private final VSTranslator vsTranslator;
    private final SETranslator seTranslator;
    private final GWTranslator gwTranslator;

    public Translator(ArrayList<Config> configs) {
        this.configs = configs;
        this.results = new ArrayList<>();
        this.vsTranslator = new VSTranslator(configs);
        this.seTranslator = new SETranslator(configs);
        this.gwTranslator = new GWTranslator(configs);
    }

    // In the translation process, we won't give the exact class of gateway API.
    // Instead, we use Map to represent the class and dump the result to the yaml file.
    public void translateAll() {
        results.addAll(vsTranslator.translateAll());
        results.addAll(seTranslator.translateAll());
        results.addAll(gwTranslator.translateAll());
        hrCounter = 0;  // reset the counter

        // Add Service back for the address resolution
        LinkedHashMap<String, Object> svc = new LinkedHashMap<>();
        svc.put("apiVersion", "v1");
        svc.put("kind", "Service");
        svc.put("metadata", new LinkedHashMap<String, Object>() {{
            put("name", defaultSvc);
        }});

        LinkedHashMap<String, Object> spec = new LinkedHashMap<>();
        ArrayList<Object> ports = new ArrayList<>();
        ArrayList<Integer> portList = new ArrayList<>(
                List.of(80, 8080, 9080, 9090)
        );
        int counter = 0;
        for (Integer port: portList) {
            ports.add(new LinkedHashMap<>(
                    Map.of("port", port,
                            "name", "general-" + counter++,
                            "targetPort", defaultPort)
            ));
        }
        spec.put("ports", ports);
        svc.put("spec", spec);
        results.add(svc);
    }

    public void dump(String path) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml prettyYaml = new Yaml(options);
        ArrayList<Object> k8sConfigs = new ArrayList<>();
        for (Config config: configs) {
            if (config instanceof Service || config instanceof Pod) {
                k8sConfigs.add(config.toYaml());
            }
        }

        try (FileWriter writer = new FileWriter(path)) {
            // dump to file with dumpAll
            prettyYaml.dumpAll(results.iterator(), writer);
            writer.write("---\n");
            prettyYaml.dumpAll(k8sConfigs.iterator(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getHRName() {
        return "hr-" + hrCounter++;
    }

    public static String regexReformat(String regex) {
        String[] parts = regex.split("\\.");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part: parts) {
            if (part.equals("*")) {
                sb.append(".*");
            } else {
                if (first) {
                    sb.append(part);
                    first = false;
                } else {
                    sb.append("\\\\.").append(part);
                }
            }
        }
        return sb.toString();
    }
}
