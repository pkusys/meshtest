package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manipulate headers.
 */
public class Headers extends Config {

    public static class HeaderOperations extends Config {
        /**
         * Overwrite the header with the given value.
         */
        public LinkedHashMap<String, String> set = new LinkedHashMap<>();
        /**
         * Append the given value to the header.
         */
        public LinkedHashMap<String, String> add = new LinkedHashMap<>();
        /**
         * Remove the given header.
         */
        public ArrayList<String> remove = new ArrayList<>();

        public void addSet(Map.Entry<String, String>... entries) {
            for (Map.Entry<String, String> entry : entries) {
                set.put(entry.getKey(), entry.getValue());
            }
        }

        public void addAdd(Map.Entry<String, String>... entries) {
            for (Map.Entry<String, String> entry : entries) {
                add.put(entry.getKey(), entry.getValue());
            }
        }

        public void addRemove(String... entries) {
            remove.addAll(Arrays.asList(entries));
        }

        public boolean isEmpty() {
            return set.isEmpty() && add.isEmpty() && remove.isEmpty();
        }
    }

    public HeaderOperations request;
    public HeaderOperations response;
}
