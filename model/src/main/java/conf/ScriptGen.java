package conf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import frontend.Config;
import frontend.istio.*;
import utils.AssertionHelper;
import utils.Dump;
import utils.Rand;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ScriptGen {
    public ArrayList<Config> configs;
    public ArrayList<Config> proactiveConfigs;
    public ArrayList<String> interleavingFields;

    public ArrayList<Event> events;

    public ScriptGen(ArrayList<Config> configs, ArrayList<Config> proactiveConfigs, ArrayList<String> interleavingFields) {
        this.configs = configs;
        this.proactiveConfigs = proactiveConfigs;
        this.interleavingFields = interleavingFields;
        this.events = new ArrayList<>();
    }

    private void generateBasicEvent() {
        Event base = new Event(Event.Operation.BASE, "NONE", "NONE");
        events.add(base);
    }

    private String getConfigName(Config config) {
        if (config instanceof VirtualService) {
            return ((VirtualService) config).metadata.get("name");
        } else if (config instanceof DestinationRule) {
            return ((DestinationRule) config).metadata.get("name");
        } else if (config instanceof Gateway) {
            return ((Gateway) config).metadata.get("name");
        } else if (config instanceof ServiceEntry) {
            return ((ServiceEntry) config).metadata.get("name");
        } else if (config instanceof Service) {
            return ((Service) config).metadata.get("name");
        } else if (config instanceof Pod) {
            return (String) ((Pod) config).metadata.get("name");
        } else {
            return null;
        }
    }

    private String getConfigKind(Config config) {
        if (config instanceof VirtualService) {
            return "vs";
        } else if (config instanceof DestinationRule) {
            return "dr";
        } else if (config instanceof Gateway) {
            return "gw";
        } else if (config instanceof ServiceEntry) {
            return "se";
        } else if (config instanceof Service) {
            return "svc";
        } else if (config instanceof Pod) {
            return "pod";
        } else {
            return null;
        }
    }

    private Event.Operation getOperation() {
        int setFieldSegment = 1;
        int removeConfigSegment = 2;
        int rand = Rand.RandInt(removeConfigSegment);

        if (rand < setFieldSegment) {
            return Event.Operation.SET_FIELD;
        } else {
            return Event.Operation.REMOVE_CONFIG;
        }
    }

    public void generateEvents() {
        generateBasicEvent();
        int rationalEventsNum = 2;
        for (int i = 0; i < rationalEventsNum; ++ i) {
            int rand = Rand.RandInt(proactiveConfigs.size());
            Event.Operation operation = getOperation();
            Config config = proactiveConfigs.get(rand);

            if (operation == Event.Operation.SET_FIELD) {
                String field = interleavingFields.get(rand);
                events.add(new Event(operation, getConfigName(config), field));
            } else if (operation == Event.Operation.REMOVE_CONFIG){
                String kind = getConfigKind(config);
                events.add(new Event(operation, getConfigName(config), kind));
                proactiveConfigs.remove(rand);
                interleavingFields.remove(rand);
            } else {
                AssertionHelper.Assert(false, "Unknown operation");
            }
        }
    }

    public void outputEvents(String path) {
        try (FileWriter fileWriter = new FileWriter(path)){
            JsonArray json = new JsonArray();
            int index = 0;
            for (; index < events.size(); ++ index) {
                Event event = events.get(index);
                JsonObject eventJson = new JsonObject();

                eventJson.addProperty("index", index);
                eventJson.addProperty("operation", event.operation.toString());
                eventJson.addProperty("config", event.configName);
                eventJson.addProperty("extra", event.extraInfo);

                json.add(eventJson);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(json, fileWriter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void applyEvent(Event event, ArrayList<Config> configs) {
        if (event.operation == Event.Operation.BASE) {
            event.yaml.addAll(Dump.GetYamlResources(configs));
            return;
        }

        Config target = null;
        for (Config config: configs) {
            if (getConfigName(config).equals(event.configName)) {
                target = config;
                break;
            }
        }

        // since sometimes SET_FIELD will change the name of a config
        if (target == null) {
            return;
        }

        if (event.operation == Event.Operation.REMOVE_CONFIG) {
            configs.remove(target);
            event.yaml.addAll(Dump.GetYamlResources(List.of(target)));
        } else if (event.operation == Event.Operation.SET_FIELD){
            Object value;
            try {
                value = Interleave.createSubstituteField(event.extraInfo);
            } catch(Exception e) {
                return;
            }
            Util.putWithField(event.extraInfo, target, value);
            event.yaml.addAll(Dump.GetYamlResources(List.of(target)));
        } else {
            AssertionHelper.Assert(false, "Unknown operation");
        }
    }
}
