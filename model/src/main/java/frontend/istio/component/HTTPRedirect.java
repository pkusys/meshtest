package frontend.istio.component;

import frontend.Config;

/**
 * Used to send a 301 redirect response to the user, and redirect them to a different location.
 */
public class HTTPRedirect extends Config {
    /**
     * Overwrite the Path portion of the URL with this value.
     */
    public String uri;
    /**
     * Overwrite the Authority portion of the URL with this value.
     */
    public DomainHost authority;
    public int port = -1;

    public static final String FROM_PROTOCOL_DEFAULT = "FROM_PROTOCOL_DEFAULT";
    public static final String FROM_REQUEST_PORT = "FROM_REQUEST_PORT";
    public String derivePort;
    /**
     * Overwrite the Scheme portion of the URL with this value.
     */
    public String scheme;
    public int redirectCode = -1;
}
