package frontend.istio;

import frontend.Config;
import frontend.istio.component.KPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Service extends Config {
    public String apiVersion = "v1";
    public String kind = "Service";
    public LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
    public LinkedHashMap<String, String> selector = new LinkedHashMap<>();
    public ArrayList<KPort> ports = new ArrayList<>();
}
