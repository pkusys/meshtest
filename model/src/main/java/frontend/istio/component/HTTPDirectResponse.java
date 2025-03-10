package frontend.istio.component;

import frontend.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used to send a fixed response to the client.
 */
public class HTTPDirectResponse extends Config {
    public int status = -1;

    public static class HTTPBody extends Config {
        public static final int STRING = 0;
        public static final int BYTES = 1;

        public String value;
        public int kind;

        @Override
        public LinkedHashMap<String, Object> toYaml() {
            LinkedHashMap<String, Object> yaml = new LinkedHashMap<>();
            switch (kind) {
                case STRING: yaml.put("string", value); break;
                case BYTES: yaml.put("bytes", value); break;
            }
            return yaml;
        }

        @Override
        public void fromYaml(Map<String, Object> yaml) {
            if (yaml.containsKey("string")) {
                value = (String) yaml.get("string");
                kind = STRING;
            } else if (yaml.containsKey("bytes")) {
                value = (String) yaml.get("bytes");
                kind = BYTES;
            }
        }
    }

    public HTTPBody body;
}
