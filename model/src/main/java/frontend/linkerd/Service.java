package frontend.linkerd;

import frontend.Config;
import frontend.linkerd.component.Port;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Service extends Config {
    public String apiVersion = "v1";
    public String kind = "Service";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public LinkedHashMap<String, String> selector = new LinkedHashMap<>();
    public ArrayList<Port> ports = new ArrayList<>();
}
