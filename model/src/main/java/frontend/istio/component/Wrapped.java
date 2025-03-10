package frontend.istio.component;

import frontend.Config;

public class Wrapped {
    public static class PortSelector extends Config {
        public int number;
    }

    public static class Percent extends Config {
        public double value;
    }

    public static class BoolValue extends Config {
        public boolean value;

        public BoolValue(boolean value) {
            this.value = value;
        }

        @Override
        public String toYaml() {
            return String.valueOf(value);
        }
    }
}
