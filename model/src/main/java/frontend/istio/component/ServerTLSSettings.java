package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

public class ServerTLSSettings extends Config {
    public Wrapped.BoolValue httpsRedirect;

    public static final String PASSTHROUGH = "PASSTHROUGH";
    public static final String SIMPLE = "SIMPLE";
    public static final String MUTUAL = "MUTUAL";
    public static final String AUTO_PASSTHROUGH = "AUTO_PASSTHROUGH";
    public static final String ISTIO_MUTUAL = "ISTIO_MUTUAL";
    public static final String OPTIONAL_MUTUAL = "OPTIONAL_MUTUAL";

    public String mode;
    /**
     * Required in SIMPLE or MUTUAL mode.
     */
    public String serverCertificate;
    /**
     * Required in SIMPLE or MUTUAL mode.
     */
    public String privateKey;
    /**
     * Required in MUTUAL or OPTIONAL_MUTUAL mode.
     */
    public String caCertificates;
    public String credentialName;
    public ArrayList<String> subjectAltNames = new ArrayList<>();
    public ArrayList<String> verifyCertificateSpki = new ArrayList<>();
    public ArrayList<String> verifyCertificateHash = new ArrayList<>();

    public static final String TLS_AUTO = "TLS_AUTO";
    public static final String TLSV1_0 = "TLSV1_0";
    public static final String TLSV1_1 = "TLSV1_1";
    public static final String TLSV1_2 = "TLSV1_2";
    public static final String TLSV1_3 = "TLSV1_3";

    public String minProtocolVersion;
    public String maxProtocolVersion;

    public ArrayList<String> cipherSuites = new ArrayList<>();

    public void addSubjectAltNames(String... subjectAltNames) {
        this.subjectAltNames.addAll(Arrays.asList(subjectAltNames));
    }

    public void addVerifyCertificateSpki(String... verifyCertificateSpki) {
        this.verifyCertificateSpki.addAll(Arrays.asList(verifyCertificateSpki));
    }

    public void addVerifyCertificateHash(String... verifyCertificateHash) {
        this.verifyCertificateHash.addAll(Arrays.asList(verifyCertificateHash));
    }

    public void addCipherSuites(String... cipherSuites) {
        this.cipherSuites.addAll(Arrays.asList(cipherSuites));
    }
}
