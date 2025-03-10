package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class HeaderModifier extends Config {
    public LinkedHashMap<String, String> set = new LinkedHashMap<>();
    public LinkedHashMap<String, String> add = new LinkedHashMap<>();
    public ArrayList<String> remove = new ArrayList<>();

    public void addSet(Map.Entry<String, String> ...entries) {
        for (Map.Entry<String, String> entry : entries) {
            set.put(entry.getKey(), entry.getValue());
        }
    }

    public void addAdd(Map.Entry<String, String> ...entries) {
        for (Map.Entry<String, String> entry : entries) {
            add.put(entry.getKey(), entry.getValue());
        }
    }

    public void addRemove(String ...entries) {
        remove.addAll(Arrays.asList(entries));
    }
}
