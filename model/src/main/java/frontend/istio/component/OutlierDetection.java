package frontend.istio.component;

import frontend.Config;

public class OutlierDetection extends Config {
    public Wrapped.BoolValue splitExternalLocalOriginErrors;
    public int consecutiveLocalOriginFailures = -1;
    public int consecutiveGatewayErrors = -1;
    public int consecutive5xxErrors = -1;
    public String interval;
    public String baseEjectionTime;
    public int maxEjectionPercent = -1;
    public int minHealthPercent = -1;
}
