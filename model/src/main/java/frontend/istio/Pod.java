package frontend.istio;

import frontend.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Pod extends Config {
    public String apiVersion = "v1";
    public String kind = "Pod";
    public LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
    public ArrayList<Object> containers = new ArrayList<>();
    public ArrayList<Object> volumes = new ArrayList<>();
}
