package frontend.istio.component;

import frontend.Config;

public class Port extends Config {
    public int number = -1;
    public String protocol;
    public String name;
    public int targetPort = -1;
}
