package frontend.linkerd.component;

import frontend.Config;

import java.util.ArrayList;
import java.util.Arrays;

public class HTTPRouteMatch extends Config {
    public Match path;
    public ArrayList<Match> headers = new ArrayList<>();
    public ArrayList<Match> queryParams = new ArrayList<>();
    public String method;

    public void addHeaders(Match ...headers) {
        this.headers.addAll(Arrays.asList(headers));
    }

    public void addQueryParams(Match ...queryParams) {
        this.queryParams.addAll(Arrays.asList(queryParams));
    }
}
