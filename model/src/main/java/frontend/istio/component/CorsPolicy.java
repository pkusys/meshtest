package frontend.istio.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Describe the Cross-Origin Resource Sharing policy.
 */
public class CorsPolicy extends Config {
    /**
     * String pattern describing the origins allowed to access the resources.
     */
    public ArrayList<HTTPMatchRequest.StringMatch> allowOrigins = new ArrayList<>();
    public ArrayList<String> allowMethods = new ArrayList<>();
    public ArrayList<String> allowHeaders = new ArrayList<>();
    public ArrayList<String> exposeHeaders = new ArrayList<>();
    /**
     * This field is wrapped with ", which is not as field of the same type in other structures.
     */
    public String maxAge;
    /**
     * I set it to 0, while 1 represents true and -1 represents false.
     */
    public Wrapped.BoolValue allowCredentials;

    public void addAllowOrigins(HTTPMatchRequest.StringMatch... allowOrigin) {
        allowOrigins.addAll(Arrays.asList(allowOrigin));
    }

    public void addAllowMethods(String... allowMethod) {
        allowMethods.addAll(Arrays.asList(allowMethod));
    }

    public void addAllowHeaders(String... allowHeader) {
        allowHeaders.addAll(Arrays.asList(allowHeader));
    }

    public void addExposeHeaders(String... exposeHeader) {
        exposeHeaders.addAll(Arrays.asList(exposeHeader));
    }
}
