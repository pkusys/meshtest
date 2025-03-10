package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * SSL/TLS related settings for upstream connections.
 */
public class ClientTLSSettings extends Config {
    public static final String DISABLE = "DISABLE";
    public static final String SIMPLE = "SIMPLE";
    public static final String MUTUAL = "MUTUAL";
    public static final String ISTIO_MUTUAL = "ISTIO_MUTUAL";

    /**
     * Indicates whether connections to this port should be secured using TLS.
     */
    public String mode;
    /**
     * Required in MUTUAL, omitted in ISTIO_MUTUAL.
     */
    public String clientCertificate;
    /**
     * Required in MUTUAL, omitted in ISTIO_MUTUAL.
     */
    public String privateKey;
    /**
     * Omitted in ISTIO_MUTUAL.
     */
    public String caCertificates;
    public String credentialName;
    public ArrayList<String> subjectAltNames = new ArrayList<>();
    public String sni;
    /**
     * Before Istio 1.9(include 1.9), it's false by default. But things will change in the future.
     */
    public Wrapped.BoolValue insecureSkipVerify;

    public void addSubjectAltNames(String... subjectAltNames) {
        this.subjectAltNames.addAll(Arrays.asList(subjectAltNames));
    }
}
