package conf;

public class Parameters {
    // This class is use for the configuration of the parameters
    // for generators, and promises the extension in the future.
    public Boolean dynamic;
    public Boolean verbose;
    public Boolean ambient;
    public Boolean gatewayAPI;
    public Boolean dumpAll;

    public Parameters(Boolean dynamic, Boolean verbose, Boolean ambient, Boolean gatewayAPI, Boolean dumpAll) {
        this.dynamic = dynamic;
        this.verbose = verbose;
        this.ambient = ambient;
        this.gatewayAPI = gatewayAPI;
        this.dumpAll = dumpAll;
    }

    public Parameters() {
        this(false, false, false, false, false);
    }
}
