package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

public class FrontendTLSValidation extends Config {
    public ArrayList<Object> caCertificateRefs = new ArrayList<>();

    public void addCaCertificateRefs(Object ...caCertificateRefs) {
        this.caCertificateRefs.addAll(Arrays.asList(caCertificateRefs));
    }
}
