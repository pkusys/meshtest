package utils;

import java.util.ArrayList;

public class PktHelper {

    // TODO: more types
    public static final int TypeHTTP = 0;
    public static final int TypeTLS = 1;
    public static final int TypeTCP = 2;
    public static final int TypeHTTPS = 3;

    public static final ArrayList<Integer> TypeList = new ArrayList<>() {
        {
            add(TypeHTTP);
            add(TypeTLS);
            add(TypeTCP);
            add(TypeHTTPS);
        }
    };

    public static final int MethodCONNECT = 0;
    public static final int MethodDELETE = 1;
    public static final int MethodGET = 2;
    public static final int MethodHEAD = 3;
    public static final int MethodOPTIONS = 4;
    public static final int MethodPATCH = 5;
    public static final int MethodPOST = 6;
    public static final int MethodPUT = 7;
    public static final int MethodTRACE = 8;

    public static final ArrayList<Integer> MethodList = new ArrayList<>() {
        {
            add(MethodGET);
            // currently only support GET
        }
    };

    public static final int ProxySidecar = 0;
    public static final int ProxyGateway = 1;

    public static final ArrayList<Integer> ProxyList = new ArrayList<>() {
        {
            add(ProxySidecar);
            add(ProxyGateway);
        }
    };

    public static final int HTTPRequest = 0;
    public static final int HTTPResponse = 1;
    public static final ArrayList<Integer> HTTPTypeList = new ArrayList<>() {
        {
            add(HTTPRequest);
            add(HTTPResponse);
        }
    };
    public static final int NoDelegate = 0;
    public static final int ToDelegate = 1;
    public static final int Delegated = 2;
}