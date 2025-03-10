package midend.LinkerdWorker;

import frontend.Config;
import frontend.linkerd.HTTPRoute;
import frontend.linkerd.component.*;
import frontend.linkerd.component.Object;
import midend.Exp.Exp;
import midend.Node.Node;
import midend.Node.NullNode;
import utils.StringMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import static midend.Worker.ExpHelper.*;
import static midend.Worker.NodeHelper.*;

public class HRGenerator {
    public static HashSet<String> headerSet = new HashSet<>();
    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        Exp guard = bool(false);
        for (Config config: list) {
            if (config instanceof HTTPRoute) {
                HTTPRoute hr = (HTTPRoute) config;
                Node hrEntry = new NullNode(hr.metadata.get("name") + "-Entry");
                Node hrExit = new NullNode(hr.metadata.get("name") + "-Exit");

                entry.addSucc(hrEntry);
                hrExit.addSucc(exit);
                guard = or(guard, generate(hr, hrEntry, hrExit));
            }
        }
        // default route for cluster service
        assume(entry,
                not(guard))
                .addSucc(exit);
    }

    public static Exp generate(HTTPRoute hr, Node entry, Node exit) {
        Node curr = entry;
        Node tmp;
        Exp result = bool(false);
        HashSet<Integer> portSet = new HashSet<>();

        if (!hr.parentRefs.isEmpty()) {
            tmp = new NullNode("Temp");
            for (Object parent : hr.parentRefs) {
                if (parent.kind.equals("Gateway")) {
                    curr = assume(entry,
                            eq(var("md.proxy"), literal("gateway", "md.proxy"), true));
                    curr = assume(curr,
                            eq(var("md.gateway"), literal(parent.name, "md.gateway"), true));
                } else {
                    curr = assume(entry,
                            eq(var("md.proxy"), literal("sidecar", "md.proxy"), true));
                    portSet.add(parent.port);
                }
                assume(curr,
                        eq(var("md.port"), literal(parent.port), true))
                        .addSucc(tmp);
            }
            curr = tmp;
        }

        if (!hr.hostnames.isEmpty()) {
            tmp = new NullNode("Temp");
            for (Host host : hr.hostnames) {
                if (host.domain.endsWith(".default.svc.cluster.local")) {
                    for (Integer port: portSet) {
                        result = or(result,
                                and(eq(var("md.host"), literalList("md.host", host.domain, "\\.", false),
                                        host.type == Host.Type.FQDN),
                                        eq(var("md.port"), literal(port), true)));
                    }
                }
                assume(curr,
                        eq(var("md.host"), literalList("md.host", host.domain, "\\.", false),
                                host.type == Host.Type.FQDN))
                        .addSucc(tmp);
            }
            curr = tmp;
        }

        if (!hr.rules.isEmpty()) {
            for (HTTPRouteRule rule : hr.rules) {
                ruleGenerate(rule, curr, exit);
            }
        }

        return result;
    }

    public static void ruleGenerate(HTTPRouteRule rule, Node entry, Node exit) {
        Node curr = entry;
        Node tmp;

        if (!rule.matches.isEmpty()) {
            tmp = new NullNode("Temp");
            for (HTTPRouteMatch match : rule.matches) {
                matchGenerate(match, curr, tmp);
            }
            curr = tmp;
        }


        if (!rule.filters.isEmpty()) {
            for (HTTPRouteFilter filter: rule.filters) {
                tmp = new NullNode("Temp");
                filterGenerate(filter, curr, tmp);
                curr = tmp;
            }
        }

        if (!rule.backendRefs.isEmpty()) {
            for (HTTPBackendRef backend : rule.backendRefs) {
                backendGenerate(backend, curr, exit);
            }
        }
    }

    public static void backendGenerate(HTTPBackendRef backend, Node entry, Node exit) {
        Node curr = entry;
        Node tmp;
        curr = let(curr,
                "md.host",
                literalList("md.host", backend.backendRef.name, "\\.", false));
        curr = let(curr,
                "md.port",
                literal(backend.backendRef.port));
        curr = let(curr,
                "md.weight",
                literal(backend.weight));
        for (HTTPRouteFilter filter: backend.filters) {
            tmp = new NullNode("Temp");
            filterGenerate(filter, curr, tmp);
            curr = tmp;
        }
        curr.addSucc(exit);
    }

    public static void filterGenerate(HTTPRouteFilter filter, Node entry, Node exit) {
        switch (filter.type) {
            case RequestHeaderModifier:
                headerModifierGenerate(filter.requestHeaderModifier, entry, exit);
                break;
            case RequestRedirect:
                redirectGenerate(filter.requestRedirect, entry, exit);
                break;
            case URLRewrite:
                rewriteGenerate(filter.urlRewrite, entry, exit);
                break;
        }
    }

    public static void headerModifierGenerate(HeaderModifier requestHeaderModifier, Node entry, Node exit) {
        Node curr = entry;

        for (Map.Entry<String, String> header: requestHeaderModifier.set.entrySet()) {
            curr = let(curr,
                    "pkt.header." + header.getKey(),
                    literal("pkt.header." + header.getKey(), header.getValue()));
        }

        for (Map.Entry<String, String> header: requestHeaderModifier.add.entrySet()) {
            curr = let(curr,
                    "pkt.header." + header.getKey(),
                    literal("pkt.header." + header.getKey(), ""));
        }

        for (String header: requestHeaderModifier.remove) {
            curr = let(curr,
                    "pkt.header." + header,
                    literal(StringMap.NONE));
        }

        curr.addSucc(exit);
    }

    public static void redirectGenerate(Redirect requestRedirect, Node entry, Node exit) {
        Node curr = entry;

        if (requestRedirect.scheme != null) {
            curr = let(curr,
                    "pkt.scheme",
                    literal("pkt.scheme", requestRedirect.scheme));
        }

        if (requestRedirect.hostname != null) {
            curr = let(curr,
                    "pkt.host",
                    literalList("md.host", requestRedirect.hostname.domain, "\\.", false));
        }

        if (requestRedirect.path != null) {
            curr = let(curr,
                    "pkt.uri",
                    literalList("pkt.uri", requestRedirect.path.replaceFullPath, "/"));
        }

        if (requestRedirect.port != -1) {
            curr = let(curr,
                    "pkt.port",
                    literal(requestRedirect.port));
        }

        if (requestRedirect.statusCode != -1) {
            curr = let(curr,
                    "pkt.status",
                    literal(requestRedirect.statusCode));
        }

        curr.addSucc(exit);
    }

    public static void rewriteGenerate(URLRewrite urlRewrite, Node entry, Node exit) {
        Node curr = entry;
        if (urlRewrite.hostname != null) {
            curr = let(curr,
                    "pkt.host",
                    literalList("md.host", urlRewrite.hostname.domain, "\\.", false));
        }
        if (urlRewrite.path != null) {
            curr = let(curr,
                    "pkt.uri",
                    literalList("pkt.uri", urlRewrite.path.replaceFullPath, "/"));
        }
        curr.addSucc(exit);
    }

    public static void matchGenerate(HTTPRouteMatch match, Node entry, Node exit) {
        Node curr = entry;

        if (match.path != null) {
            curr = assume(curr,
                    eq(var("pkt.uri"), literalList("pkt.uri", match.path.value, "/"),
                            match.path.type == Match.Type.Exact));
        }

        if (match.method != null) {
            curr = assume(curr,
                    eq(var("pkt.method"), literal("pkt.method", match.method), true));
        }

        for (Match header: match.headers) {
            curr = assume(curr,
                    eq(var("pkt.header." + header.name), literal("pkt.header." + header.name, header.value),
                            header.type == Match.Type.Exact));
            headerSet.add(header.name);
        }

        for (Match queryParam: match.queryParams) {
            curr = assume(curr,
                    eq(var("pkt.query." + queryParam.name), literal("pkt.query." + queryParam.name, queryParam.value),
                            queryParam.type == Match.Type.Exact));
        }

        curr.addSucc(exit);
    }
}
