package utils;

import frontend.Config;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Dump {
    public static void DumpResources(ArrayList<Config> resources, String output_path) {
        // dump yaml to file
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml prettyYaml = new Yaml(options);
        ArrayList<Object> result = new ArrayList<>();
        for (Config resource: resources) {
            Config.isResource = true;
            result.add(resource.toYaml());
        }

        try (FileWriter writer = new FileWriter(output_path)) {
            // dump to file with dumpAll
            prettyYaml.dumpAll(result.iterator(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void DumpYamlResources(List<Object> yaml, String output_path) {
        // dump yaml to file
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml prettyYaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(output_path)) {
            // dump to file with dumpAll
            prettyYaml.dumpAll(yaml.iterator(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static ArrayList<Object> GetYamlResources(List<Config> resources) {
        ArrayList<Object> result = new ArrayList<>();
        for (Config resource: resources) {
            result.add(resource.toYaml());
        }
        return result;
    }
}
