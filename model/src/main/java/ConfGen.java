import conf.*;
import frontend.Config;
import translator.Translator;
import utils.AssertionHelper;
import utils.Rand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static conf.Resource.createProactiveResources;
import static utils.Dump.DumpResources;
import static utils.Dump.DumpYamlResources;

public class ConfGen {
    RationalGen rationalGen;
    RandGen randGen;
    ScriptGen scriptGen;
    Parameters parameters;

    public ConfGen(Parameters parameters) {
        this.rationalGen = new RationalGen(parameters);
        this.randGen = new RandGen();
        this.parameters = parameters;
    }

    public void generateAll(String dir) {
        ArrayList<Interleave.InterleavePair> pairs = Interleave.getProactivePairs(-1, null);
        int count = 0;
        for (Interleave.InterleavePair pair : pairs) {
            Rand.setSeed(count);
            System.out.println("Generating " + count + "th proactive pair");
            if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            String path = dir + "/" + count + ".yaml";
            dogenerate(path, pair);
            count++;
        }
    }

    public void generate(String path) {
        ArrayList<Interleave.InterleavePair> pairs = Interleave.getProactivePairs(-1, null);
        Interleave.InterleavePair pair = pairs.get(Rand.RandInt(pairs.size()));
        dogenerate(path, pair);
    }

    // config directory structure (e.g., path=testconf/1.yaml)
    // testconf/1.yaml: original config, used for istio
    // testconf/meshtest-1.yaml: config in istio-api format, used for meshtest
    // note: 1.yaml and meshtest-1.yaml are the same in istio mode, different in gateway-api mode
    public void dogenerate(String path, Interleave.InterleavePair pair) {
        String meshtest_path = path.substring(0, path.lastIndexOf("/")) + "/meshtest-" + path.substring(path.lastIndexOf("/") + 1);
        ArrayList<Resource> ProactiveResources = createProactiveResources(pair, rationalGen.maker, 2);
        rationalGen.addResource(ProactiveResources.get(0));
        rationalGen.addResource(ProactiveResources.get(1));

        if (parameters.verbose) rationalGen.showAll();
        rationalGen.fill();

        // generate dynamic plan if needed
        if (parameters.dynamic) {
            AssertionHelper.Assert(false, "Dynamic change is not supported in this version");
//            ArrayList<Config> proactiveConfigs = new ArrayList<>(List.of(
//                    rationalGen.configs.get(rationalGen.resourceIndex(ProactiveResources.get(0))),
//                    rationalGen.configs.get(rationalGen.resourceIndex(ProactiveResources.get(1)))
//            ));
//            ArrayList<String> interleavingFields = new ArrayList<>(List.of(pair.field1, pair.field2));
//            scriptGen = new ScriptGen(rationalGen.configs, proactiveConfigs, interleavingFields);
//            scriptGen.generateEvents();
        }

        rationalGen.merge();
        rationalGen.sort();

        // config after dynamic change
        if (parameters.dynamic) {
            AssertionHelper.Assert(false, "Dynamic change is not supported in this version");
//            String casePrefix = path.substring(0, path.lastIndexOf("."));
//            File caseDir = new File(casePrefix);
//            if (!caseDir.exists()) {
//                caseDir.mkdir();
//            }
//            Integer stageIndex = 0;
//            for (Event event : scriptGen.events) {
//                scriptGen.applyEvent(event, rationalGen.configs);
//                DumpResources(rationalGen.configs, casePrefix + "/stage-" + stageIndex + ".yaml");
//                DumpYamlResources(event.yaml, casePrefix + "/action-" + stageIndex + ".yaml");
//                stageIndex++;
//            }
//            scriptGen.outputEvents(casePrefix + "/change.json");
        } else {
            DumpResources(rationalGen.configs, meshtest_path);
        }

        if (parameters.gatewayAPI) {
            Translator translator = new Translator(rationalGen.configs);
            try {
                translator.translateAll();
                translator.dump(path);
            } catch (RuntimeException exception) {
                // remove meshtest_path
                File meshtestFile = new File(meshtest_path);
                if (meshtestFile.exists()) {
                    meshtestFile.delete();
                }
                System.out.println("Error: " + exception.getMessage());
            }
        } else {
            DumpResources(rationalGen.configs, path);
        }
        rationalGen.clear();
    }

    public static void main(String[] args) {
        Boolean dumpAll = true;
        Parameters parameters = new Parameters(false, false, false, true, dumpAll);
        if (dumpAll) {
            ConfGen gen = new ConfGen(parameters);
            gen.generateAll("testconf");
        } else {
            int caseNum = 10;
            for (int i = 0; i < caseNum; i++) {
                long seed = i;
                Rand.setSeed(seed);
                System.out.println("Random seed: " + seed);
                ConfGen gen = new ConfGen(parameters);

                String path = "testconf/" + seed + ".yaml";
                gen.generate(path);
            }
        }
    }
}

