package frontend.istio.component;

import frontend.Config;

import java.util.LinkedHashMap;

public class WorkloadSelector extends Config {
    public LinkedHashMap<String, String> labels = new LinkedHashMap<>();
}
