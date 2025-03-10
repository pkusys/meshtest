import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import utils.AssertionHelper;
import utils.Timer;

import javax.swing.text.Style;
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

public class Controller {

    public static Timer timer = new Timer(new ArrayList<String>() {{
        add("Set Env");
        add("CFG Model");
        add("Symbolic Execution");
        add("Send/Recv Packets");
    }});

    private static void runPython(String pyFile, String arg) {
        utils.IO.Info("[Running] python3 " + pyFile + " " + arg);
        try {
            Process p = Runtime.getRuntime().exec("python3 " + pyFile + " " + arg);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
            in.close();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                utils.IO.Error("[Error] python3 " + pyFile + " " + arg);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void runMake(String target) {
        utils.IO.Info("[Running] make " + target);
        try {
            Process p = Runtime.getRuntime().exec("make " + target);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
            in.close();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                utils.IO.Error("[Error] make " + target);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

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

    private static void waitDone(String workDir) {
        HashSet<String> agents = new HashSet<>();
        // if ends with .pkts.json in parentPath, then it is a sender
        for (File f: new File(workDir).listFiles()) {
            if (f.getName().endsWith(".pkts.json")) {
                agents.add(f.getName().substring(0, f.getName().indexOf(".")));
            }
        }
        AssertionHelper.Assert(agents.size() > 0, "No agents found");
        for (int i = 0; i < 20; i++) {
            // sleep 1 second
            try {
                boolean done = true;
                for (String agent: agents) {
                    String donePath = workDir + "/" + agent + ".done";
                    File doneFile = new File(donePath);
                    if (!doneFile.exists()) {
                        done = false;
                        break;
                    }
                }
                if (done) {
                    utils.IO.Info("All agents are done");
                    return;
                }
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void runActualTest(String test_path) {
        try {
            File test_dir = new File(test_path);
            String testID = test_dir.getName();
            utils.IO.Highlight("Running test: " + testID);

            String stableYaml = test_path + "/kind-istio-stable-" + testID + ".yaml";
            String devYaml = test_path + "/kind-istio-dev-" + testID + ".yaml";
            String casejson = test_path + "/case-" + testID + ".json";

            utils.IO.Info("[INFO] start running test");
            Controller.timer.start("Set Env");
            runPython("driver/controller.py", stableYaml + " " + devYaml + " " + casejson + " env 0");
            Controller.timer.stop("Set Env");
            Controller.timer.start("Send/Recv Packets");
            runPython("driver/controller.py", stableYaml + " " + devYaml + " " + casejson + " sender-start 0");
            waitDone(test_path);
            runPython("driver/controller.py", stableYaml + " " + devYaml + " " + casejson + " sender-stop 0");
            Controller.timer.stop("Send/Recv Packets");
//            wait(3);
            Controller.timer.start("Set Env");
            runPython("driver/controller.py", stableYaml + " " + devYaml + " " + casejson + " stop 0");
//            wait(5);
            runPython("driver/checker.py", test_path + " 1");
            Controller.timer.stop("Set Env");
        } catch (Exception e) {
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

    private static void runStaticTest(File test_file, File output_dir) throws IOException {
        if (test_file.isFile() && test_file.getName().endsWith(".yaml") && !test_file.getName().startsWith("ml")) {
            String testID = test_file.getName().substring(0, test_file.getName().lastIndexOf("."));

            // remove directory for test case in output path if exist
            File test_dir = new File(output_dir.getAbsolutePath() + "/" + testID);
            deleteDir(test_dir);
            createDir(test_dir);

            try {
                Files.copy(test_file.toPath(), Paths.get(test_dir.getAbsolutePath() + "/" + test_file.getName()));
                Files.copy(test_file.toPath(), Paths.get(test_dir.getAbsolutePath() + "/" + "kind-istio-stable-" + test_file.getName()));
                Files.copy(test_file.toPath(), Paths.get(test_dir.getAbsolutePath() + "/" + "kind-istio-dev-" + test_file.getName()));
                setReceiverPod(test_dir.getAbsolutePath() + "/" + "kind-istio-stable-" + test_file.getName(), testID);
                setReceiverPod(test_dir.getAbsolutePath() + "/" + "kind-istio-dev-" + test_file.getName(), testID);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // generate test case template
            utils.IO.Highlight("Generating test cases for: " + test_file.getName());
//            File model_file;
//            try {
//                model_file = new File(test_file.getAbsolutePath().replace(test_file.getName(), "ml-" + test_file.getName()));
//            } catch (Exception e) {
//                model_file = test_file;
//            }
            Main.run(test_file.getAbsolutePath(), test_dir.getAbsolutePath());

            runActualTest(Paths.get(test_dir.getAbsolutePath()).normalize().toString());

            // delete the test conf if pass
//                File passFlag = new File(test_dir.getAbsolutePath() + "/Result-PASS.log");
//                if (passFlag.exists()) {
//                    test_file.delete();
//                }
        }
    }

    private static void wait(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static JsonArray getChangeStages(File testDir){
        // load change stages
        JsonArray changeStages = new JsonArray();
        try {
            JsonElement element = JsonParser.parseReader(new FileReader(testDir.getAbsolutePath() + "/change.json"));
            changeStages = element.getAsJsonArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return changeStages;
    }


    public static void prepareDynamicTest(File caseDir, File outputDir) throws IOException {
        if (!caseDir.isDirectory()) {
            return;
        }
        String caseName = caseDir.getName();
        File testDir = new File(outputDir.getAbsolutePath() + "/" + caseName);
        deleteDir(testDir);
        createDir(testDir);

        // copy base.yaml and change.json into testDir
        try {
            Files.copy(Paths.get(caseDir.getAbsolutePath() + "/stage-0.yaml"), Paths.get(testDir.getAbsolutePath() + "/base.yaml"));
            Files.copy(Paths.get(caseDir.getAbsolutePath() + "/stage-0.yaml"), Paths.get(testDir.getAbsolutePath() + "/kind-istio-stable-base.yaml"));
            Files.copy(Paths.get(caseDir.getAbsolutePath() + "/stage-0.yaml"), Paths.get(testDir.getAbsolutePath() + "/kind-istio-dev-base.yaml"));
            Files.copy(Paths.get(caseDir.getAbsolutePath() + "/change.json"), Paths.get(testDir.getAbsolutePath() + "/change.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        JsonArray changeStages = getChangeStages(testDir);

        // run symbolic execution on each stages
        for (int stageIndex = 0; stageIndex < changeStages.size(); stageIndex++) {
            // run every actual stage test
            String stageName = String.valueOf(stageIndex);
            try {
                Files.copy(Paths.get(caseDir.getAbsolutePath() + "/action-" + stageName + ".yaml"), Paths.get(testDir.getAbsolutePath() + "/action-" + stageName + ".yaml"));
                Files.copy(Paths.get(caseDir.getAbsolutePath() + "/stage-" + stageName + ".yaml"), Paths.get(testDir.getAbsolutePath() + "/stage-" + stageName + ".yaml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            utils.IO.Highlight("Generating test cases for: " + caseName + " on stage " + stageName);
            createDir(new File(testDir.getAbsolutePath() + "/stage-" + stageName));
            Main.run(caseDir.getAbsolutePath() + "/stage-" + stageName + ".yaml", testDir.getAbsolutePath() + "/stage-" + stageName);
        }

        // set receiver pods
        setReceiverPod(testDir.getAbsolutePath() + "/" + "kind-istio-stable-base.yaml", caseName);
        setReceiverPod(testDir.getAbsolutePath() + "/" + "kind-istio-dev-base.yaml", caseName);

        return;
    }


    public static void runDynamicTest(File caseDir, File outputDir) throws IOException {
        String caseName = caseDir.getName();
        File testDir = new File(outputDir.getAbsolutePath() + "/" + caseName);
        JsonArray changeStages = getChangeStages(testDir);

        // run actual test
        for (int stageIndex = 0; stageIndex < changeStages.size(); stageIndex++) {
            String stageName = String.valueOf(stageIndex);
            utils.IO.Highlight("Running test for: " + caseName + " on stage " + stageName);
            String jsonPath = Paths.get(testDir.getAbsolutePath() + "/stage-" + stageName + "/case-stage-" + stageName + ".json").normalize().toString();
            String yamlPath = Paths.get(testDir.getAbsolutePath() + "/base.yaml").normalize().toString();
            String stableYaml = Paths.get(testDir.getAbsolutePath() + "/kind-istio-stable-base.yaml").normalize().toString();
            String devYaml = Paths.get(testDir.getAbsolutePath() + "/kind-istio-dev-base.yaml").normalize().toString();
            String workDir = Paths.get(testDir.getAbsolutePath() + "/stage-" + stageName).normalize().toString();

            JsonElement element = changeStages.get(stageIndex);
            String operation = element.getAsJsonObject().get("operation").getAsString();
            if (Objects.equals(operation, "BASE")) {
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " env");
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-start " + stageName);
                waitDone(workDir);
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-stop " + stageName);
                wait(3);
            } else if (Objects.equals(operation, "REMOVE_CONFIG")) {
                String config = element.getAsJsonObject().get("config").getAsString();
                String extra = element.getAsJsonObject().get("extra").getAsString();
                runKubectl("delete " + extra + " " + config, "kind-istio-stable");
                runKubectl("delete " + extra + " " + config, "kind-istio-dev");
                wait(3);
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-start " + stageName);
                waitDone(workDir);
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-stop " + stageName);
                wait(3);
            } else if (Objects.equals(operation, "SET_FIELD")) {
                String config = element.getAsJsonObject().get("config").getAsString();
                String extra = element.getAsJsonObject().get("extra").getAsString();
                if (extra.contains("metadata.name")) { // if set name, remove and restart
                    String ResourceType = extra.substring(0, extra.indexOf("."));
                    runKubectl("delete " + ResourceType + " " + config, "kind-istio-stable");
                    runKubectl("delete " + ResourceType + " " + config, "kind-istio-dev");
                }
                runKubectl("apply -f " + testDir.getAbsolutePath() + "/action-" + stageName + ".yaml", "kind-istio-stable");
                runKubectl("apply -f " + testDir.getAbsolutePath() + "/action-" + stageName + ".yaml", "kind-istio-dev");
                wait(3);
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-start " + stageName);
                waitDone(workDir);
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " sender-stop " + stageName);
                wait(3);
            } else {
                AssertionHelper.Assert(false, "Unknown operation");
            }

            if (stageIndex == changeStages.size() - 1) {
                runPython("driver/controller.py", stableYaml + " " + devYaml + " " + jsonPath + " stop " + stageName);
                wait(5);
                runPython("driver/checker.py", testDir + " " + changeStages.size());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        // first argument: the path of yaml files
        System.load("/Library/Java/Extensions/libz3.dylib");
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
            if (!test_file.getName().endsWith(".yaml") || test_file.getName().startsWith("ml")) {
                continue;
            }
            long startTime = System.currentTimeMillis();
//            prepareDynamicTest(test_file, output_dir);
//            runDynamicTest(test_file, output_dir);
            runStaticTest(test_file, output_dir);
            long endTime = System.currentTimeMillis();
            utils.IO.Highlight("Testing time: " + (endTime - startTime) / 1000 + " seconds");
        }
        Controller.timer.print();
    }
}
