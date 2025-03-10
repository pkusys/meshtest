package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

public class HTTPRouteRetry extends Config {
    public ArrayList<Integer> codes = new ArrayList<>();
    public Integer attempts = -1;
    public String backoff;

    public void addCodes(Integer ...codes) {
        this.codes.addAll(Arrays.asList(codes));
    }
}
