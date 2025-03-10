package conf;

import java.util.ArrayList;

public class Event {
    public enum Operation {
        BASE,
        SET_FIELD,
        REMOVE_CONFIG,
    }
    public Operation operation;
    public String configName;
    public String extraInfo;
    public ArrayList<Object> yaml;

    public Event(Operation operation, String configName, String extraInfo) {
        this.operation = operation;
        this.configName = configName;
        this.extraInfo = extraInfo;
        this.yaml = new ArrayList<>();
    }
}
