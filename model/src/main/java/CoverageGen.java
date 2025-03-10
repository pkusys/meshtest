import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import utils.AssertionHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static utils.FileHelper.createDir;
import static utils.FileHelper.deleteDir;

public class CoverageGen {
    private static void runKubectl(String args, String context) {
        utils.IO.Info("[Running] kubectl --context " + context + " " + args);
        try {
            Process p = Runtime.getRuntime().exec("kubectl --context " + context + " " + args);
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                utils.IO.Error("[Error] kubectl --context " + context + " " + args);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Rewrite yamlFile with receiver pod
     * */
    private static void setReceiverPod(String yamlFile, String testID) {
        // yamlFile is like /a/b/c/kind-istio-stable-testID.yaml
        String cluster = yamlFile.substring(yamlFile.lastIndexOf("/") + 1, yamlFile.lastIndexOf("-"));
        AssertionHelper.Assert(Objects.equals(cluster, "kind-istio-stable") || Objects.equals(cluster, "kind-istio-dev"),
                "Cluster name should be kind-istio-stable or kind-istio-dev");
        // load yaml file and modify it
        try {
            Yaml yaml = new Yaml();
            InputStream fis = new FileInputStream(yamlFile);
            Iterable<Object> yamlConf = yaml.loadAll(fis);
            ArrayList<Object> result = new ArrayList<>();
            for (Object conf : yamlConf) {
                result.add(conf);
                Map<String, Object> confMap = (Map<String, Object>) conf;
                if (confMap.get("kind").equals("Pod")) {
                    String name = ((Map<String, Object>) confMap.get("metadata")).get("name").toString();
                    Map<String, Object> spec = (Map<String, Object>) confMap.get("spec");
                    ArrayList<Object> containers = (ArrayList<Object>) spec.get("containers");
                    AssertionHelper.Assert(containers.size() == 1, "Only one container is allowed in a pod");
                    Map<String, Object> container = (Map<String, Object>) containers.get(0);
                    ArrayList<Object> args = new ArrayList<>();
                    container.put("args", args);
                    args.add("receiver.py");
                    args.add("/app/config/" + testID + "/" + cluster + "/" + name + "-recv.json");
                } else {
                    continue;
                }
            }
            fis.close();

            // dump yaml to file
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml prettyYaml = new Yaml(options);
            FileWriter writer = new FileWriter(yamlFile);
            prettyYaml.dumpAll(result.iterator(), writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

    }

    private static void requestCoverage() {
        // create a file in covdir
        String covdir = "coverage-stable";
        File file = new File(covdir + "/dump");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runStaticTest(File test_file, File output_dir) throws IOException {
        if (test_file.isFile() && test_file.getName().endsWith(".yaml") && !test_file.getName().startsWith("meshtest-")) {
            String testID = test_file.getName().substring(0, test_file.getName().lastIndexOf("."));

            // remove directory for test case in output path if exist
            File test_dir = new File(output_dir.getAbsolutePath() + "/" + testID);
            deleteDir(test_dir);
            createDir(test_dir);

            try {
                Files.copy(test_file.toPath(), Paths.get(test_dir.getAbsolutePath() + "/" + testID + ".yaml"));
                Files.copy(test_file.toPath(), Paths.get(test_dir.getAbsolutePath() + "/" + "kind-istio-stable-" + testID + ".yaml"));
                setReceiverPod(test_dir.getAbsolutePath() + "/" + "kind-istio-stable-" + testID + ".yaml", testID);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            runKubectl("apply -f " + test_dir.getAbsolutePath() + "/kind-istio-stable-" + test_file.getName(), "kind-istio-stable");
            wait(3);
            runKubectl("delete -f " + test_dir.getAbsolutePath() + "/kind-istio-stable-" + test_file.getName() + " --force", "kind-istio-stable");
            requestCoverage();
        }
    }

    private static void wait(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        // first argument: the path of yaml files
        String input_path = "testconf";
        String output_path = "testcase";
        utils.IO.Highlight("Input path: " + input_path);
        utils.IO.Highlight("Output path: " + output_path);
        File input_dir = new File(input_path);
        if (!input_dir.exists()) {
            utils.IO.Error("Input path does not exist!");
            return;
        }
        // if output path does not exist, create it
        File output_dir = new File(output_path);
        if (!output_dir.exists()) {
            output_dir.mkdirs();
        }

        for (File test_file: Objects.requireNonNull(input_dir.listFiles())) {
            if (!test_file.getName().endsWith(".yaml") || test_file.getName().startsWith("meshtest-")) {
                continue;
            }
            runStaticTest(test_file, output_dir);
        }
    }
}
