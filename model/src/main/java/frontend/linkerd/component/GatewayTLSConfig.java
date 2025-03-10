package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayTLSConfig extends Config {
    public enum TLSModeType {
        Passthrough,
        Terminate,
    }
    public TLSModeType mode;
    public ArrayList<Object> certificateRefs = new ArrayList<>();
    public FrontendTLSValidation frontendTLSValidation;
    public LinkedHashMap<String, String> options = new LinkedHashMap<>();

    public void addCertificateRefs(Object ...certificateRefs) {
        this.certificateRefs.addAll(Arrays.asList(certificateRefs));
    }

    public void addOptions(Map.Entry<String, String> ...options) {
        for (Map.Entry<String, String> option : options) {
            this.options.put(option.getKey(), option.getValue());
        }
    }
}
