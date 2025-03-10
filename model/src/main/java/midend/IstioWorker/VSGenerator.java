package midend.IstioWorker;

import utils.ExpHelper;
import utils.IPHelper;
import utils.PktHelper;
import utils.StringMap;
import frontend.Config;
import frontend.istio.VirtualService;
import frontend.istio.component.*;
import midend.Exp.*;
import midend.Exp.MatchExp.MatchType;
import midend.Node.AssumeNode;
import midend.Node.LetNode;
import midend.Node.Node;
import midend.Node.NullNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import static midend.Exp.LogicExp.LogicType.AND;
import static midend.Exp.LogicExp.LogicType.OR;
import static utils.AssertionHelper.Assert;

public class VSGenerator {
    public static HashSet<String> fieldSet = new HashSet<>();
    public static HashSet<String> dstLabels = new HashSet<>();

    public static void generateAll(ArrayList<Config> list, Node entry, Node exit) {
        Exp guardCondition = null;
        for (Config config: list) {
            if (config instanceof VirtualService) {
                VirtualService vs = (VirtualService) config;
                String name = vs.metadata.get("name");
                Node vsTmpEntry = new NullNode(name + "-Entry");
                Node vsTmpExit = new NullNode(name + "-Exit");

                if (guardCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(guardCondition));
                    entry.addSucc(guard);
                    guard.addSucc(vsTmpEntry);
                    guardCondition = new LogicExp(OR, guardCondition, generate(vs, vsTmpEntry, vsTmpExit));
                } else {
                    entry.addSucc(vsTmpEntry);
                    guardCondition = generate(vs, vsTmpEntry, vsTmpExit);
                }

                vsTmpExit.addSucc(exit);
            }
        }

        AssumeNode defaultGuard;
        defaultGuard = guardCondition != null ? new AssumeNode(new NotExp(guardCondition)) : new AssumeNode(new TrueExp());
        AssumeNode fromSidecar = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxySidecar)));
        entry.addSucc(fromSidecar);
        fromSidecar.addSucc(defaultGuard);
        defaultGuard.addSucc(exit);
    }


    // return condition of entering this virtual service
    public static Exp generate(VirtualService vs, Node entry, Node exit) {
        Node curr = entry;
        ExpHelper helper = new ExpHelper();

        // we use host as only guard condition now
        Node hostExit = new NullNode("Host-Exit");
        for (Host h: vs.hosts) {
            AssumeNode guard = HostGuard(h, "md.host");
            helper.add(guard.getCondition(), ExpHelper.OR);
            curr.addSucc(guard);
            guard.addSucc(hostExit);
        }

        AssumeNode delegateGuard = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.delegate"), new IntLiteralExp(PktHelper.Delegated)));
        AssumeNode delegateTarget = HostGuard(new DomainHost(vs.metadata.get("name")), "md.host");

        curr.addSucc(delegateGuard);
        delegateGuard.addSucc(delegateTarget);
        delegateTarget.addSucc(hostExit);
        helper.add(new LogicExp(AND, delegateGuard.getCondition(),
                delegateTarget.getCondition()), ExpHelper.OR);

        curr = hostExit;


        // filter proxy: the pkt comes from gateway/sidecar, which gateway
        Node proxyExit = new NullNode("Proxy-Exit");
        if (vs.gateways.isEmpty()) {
            // if gateway is omitted, it should be mesh
            AssumeNode guard = new AssumeNode(new MatchExp(MatchType.EXACT,
                    new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxySidecar)));
            curr.addSucc(guard);
            guard.addSucc(proxyExit);
        } else {
            AssumeNode gwGuard = new AssumeNode(new MatchExp(MatchType.EXACT,
                    new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxyGateway)));
            curr.addSucc(gwGuard);

            for (String s: vs.gateways) {
                if (s.equals("mesh")) {
                    AssumeNode guard = new AssumeNode(new MatchExp(MatchType.EXACT,
                            new VarExp("md.proxy"), new IntLiteralExp(PktHelper.ProxySidecar)));
                    curr.addSucc(guard);
                    guard.addSucc(proxyExit);
                } else {
                    AssumeNode guard;
                    guard = AssumeStringEq(s, "md.gateway", "md.gateway");
                    gwGuard.addSucc(guard);
                    guard.addSucc(proxyExit);
                }
            }
        }
        curr = proxyExit;

        LetNode setProxy = new LetNode(new VarExp("md.proxy"),
                new IntLiteralExp(PktHelper.ProxySidecar));
        curr.addSucc(setProxy);
        curr = setProxy;

        AssumeNode http = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTP)));
        AssumeNode tls = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeTLS)));
        AssumeNode tcp = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeTCP)));
        AssumeNode https = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTPS)));

        if (!vs.http.isEmpty()) {
            Node httpEntry = new NullNode("HTTP-Entry");
            Node httpExit = new NullNode("HTTP-Exit");
            Exp httpCondition = null;

            curr.addSucc(http); http.addSucc(httpEntry);
            curr.addSucc(https); https.addSucc(httpEntry);

            int count = 0;
            for (HTTPRoute hr: vs.http) {
                String name = hr.name != null ? hr.name : "HR-" + count;
                Node httpTmpEntry = new NullNode(name + "-Entry");
                if (httpCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(httpCondition));
                    httpEntry.addSucc(guard);
                    guard.addSucc(httpTmpEntry);
                    httpCondition = new LogicExp(OR, httpCondition, httpGenerate(hr, httpTmpEntry, httpExit));
                } else {
                    httpEntry.addSucc(httpTmpEntry);
                    httpCondition = httpGenerate(hr, httpTmpEntry, httpExit);
                }
                count++;
            }

            httpExit.addSucc(exit);
        }

        if (!vs.tls.isEmpty()) {
            Node tlsEntry = new NullNode("TLS-Entry");
            Node tlsExit = new NullNode("TLS-Exit");
            Exp tlsCondition = null;

            curr.addSucc(tls); tls.addSucc(tlsEntry);

            int count = 0;
            for (TCPRoute tr: vs.tls) {
                String name = "TR-" + count;
                Node tlsTmpEntry = new NullNode(name + "-Entry");
                if (tlsCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(tlsCondition));
                    tlsEntry.addSucc(guard);
                    guard.addSucc(tlsTmpEntry);
                    tlsCondition = new LogicExp(OR, tlsCondition, tlsGenerate(tr, tlsTmpEntry, tlsExit));
                } else {
                    tlsEntry.addSucc(tlsTmpEntry);
                    tlsCondition = tlsGenerate(tr, tlsTmpEntry, tlsExit);
                }
                count++;
            }

            tlsExit.addSucc(exit);
        }

        if (!vs.tcp.isEmpty()) {
            Node tcpEntry = new NullNode("TCP-Entry");
            Node tcpExit = new NullNode("TCP-Exit");
            Exp tcpCondition = null;

            curr.addSucc(tcp); tcp.addSucc(tcpEntry);

            int count = 0;
            for (TCPRoute tr: vs.tcp) {
                String name = "TR-" + count;
                Node tcpTmpEntry = new NullNode(name + "-Entry");
                if (tcpCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(tcpCondition));
                    tcpEntry.addSucc(guard);
                    guard.addSucc(tcpTmpEntry);
                    tcpCondition = new LogicExp(OR, tcpCondition, tlsGenerate(tr, tcpTmpEntry, tcpExit));
                } else {
                    tcpEntry.addSucc(tcpTmpEntry);
                    tcpCondition = tlsGenerate(tr, tcpTmpEntry, tcpExit);
                }
                count++;
            }

            tcpExit.addSucc(exit);
        }

        return helper.getExp();
    }

    private static Exp httpGenerate(HTTPRoute hr, Node entry, Node exit) {
        Node curr = entry;
        Exp guardCondition = null;

        if (!hr.match.isEmpty()) {
            Node matchExit = new NullNode("Match-Exit");

            int count = 0;
            for (HTTPMatchRequest m: hr.match) {
                String name = "HMatch-" + count;
                Node matchTmpEntry = new NullNode(name + "-Entry");
                if (guardCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(guardCondition));
                    curr.addSucc(guard);
                    guard.addSucc(matchTmpEntry);
                    guardCondition = new LogicExp(OR, guardCondition, httpMatchGenerate(m, matchTmpEntry, matchExit));
                } else {
                    curr.addSucc(matchTmpEntry);
                    guardCondition = httpMatchGenerate(m, matchTmpEntry, matchExit);
                }
                count++;
            }

            curr = matchExit;
        } else {
            guardCondition = new TrueExp();
        }

        if (hr.headers != null && hr.headers.request != null) {
            Node headerExit = new NullNode("Header-Exit");
            headerGenerate(hr.headers, curr, headerExit);
            curr = headerExit;
        }

        Node forwardExit = new NullNode("Forward-Exit");

        int count = 0;
        for (RouteDestination rd: hr.route) {
            String name = "HRoute-" + count;
            Node routeEntry = new NullNode(name + "-Entry");
            curr.addSucc(routeEntry);
            routeGenerate(rd, routeEntry, forwardExit);
            count++;
        }

        if (hr.redirect != null) {
            Node redirectEntry = new NullNode("Redirect-Entry");
            curr.addSucc(redirectEntry);
            redirectGenerate(hr.redirect, redirectEntry, forwardExit);
        }

        if (hr.directResponse != null) {
            Node directResponseEntry = new NullNode("DirectResponse-Entry");
            curr.addSucc(directResponseEntry);
            directResponseGenerate(hr.directResponse, directResponseEntry, forwardExit);
        }

        if (hr.delegate != null) {
            Node delegateEntry = new NullNode("Delegate-Entry");
            curr.addSucc(delegateEntry);
            delegateGenerate(hr.delegate, delegateEntry, forwardExit);
        }

        curr = forwardExit;

        if (hr.rewrite != null) {
            Node rewriteExit = new NullNode("Rewrite-Exit");
            rewriteGenerate(hr.rewrite, curr, rewriteExit);
            curr = rewriteExit;
        }

        curr.addSucc(exit);
        return guardCondition;
    }

    private static Exp tlsGenerate(TCPRoute tr, Node entry, Node exit) {
        Node curr = entry;
        Exp guardCondition = null;

        if (!tr.match.isEmpty()) {
            Node matchExit = new NullNode("Match-Exit");

            int count = 0;
            for (L4MatchAttributes m: tr.match) {
                String name = "TMatch-" + count;
                Node matchTmpEntry = new NullNode(name + "-Entry");
                if (guardCondition != null) {
                    AssumeNode guard = new AssumeNode(new NotExp(guardCondition));
                    curr.addSucc(guard);
                    guard.addSucc(matchTmpEntry);
                    guardCondition = new LogicExp(OR, guardCondition, tlsMatchGenerate(m, matchTmpEntry, matchExit));
                } else {
                    curr.addSucc(matchTmpEntry);
                    guardCondition = tlsMatchGenerate(m, matchTmpEntry, matchExit);
                }
                count++;
            }

            curr = matchExit;
        } else {
            guardCondition = new TrueExp();
        }

        if (!tr.route.isEmpty()) {
            Node forwardExit = new NullNode("Forward-Exit");

            int count = 0;
            for (RouteDestination rd: tr.route) {
                String name = "TRoute-" + count;
                Node routeEntry = new NullNode(name + "-Entry");
                curr.addSucc(routeEntry);
                routeGenerate(rd, routeEntry, forwardExit);
                count++;
            }

            curr = forwardExit;
        }

        curr.addSucc(exit);
        return guardCondition;
    }

    private static Exp httpMatchGenerate(HTTPMatchRequest m, Node entry, Node exit) {
        Node curr = entry;
        ExpHelper helper = new ExpHelper();
        ExpHelper localHelper = new ExpHelper();

        // note: this is for the HTTPMatchRequest is empty.
        helper.add(new TrueExp(), ExpHelper.AND);

        if (m.uri != null) {
            Exp target = new VarExp("pkt.http.uri");
            AssumeNode guard = null;
            switch (m.uri.type) {
                case EXACT: guard = new AssumeNode(new MatchExp(MatchType.EXACT, target,
                        string2List(m.uri.value, "pkt.http.uri", "/", 0))); break;
                case PREFIX: ListExp matcher = string2List(m.uri.value, "pkt.http.uri", "/", 0);
                    guard = new AssumeNode(new LogicExp(OR,
                            new MatchExp(MatchType.PREFIX, target, matcher),
                            new MatchExp(MatchType.EXACT, target, matcher)));
                    break;
                case REGEX: assert m.uri.value.endsWith("/*");
                    matcher = string2List(m.uri.value.substring(0, m.uri.value.length() - 2),
                            "pkt.http.uri", "/", 0);
                    guard = new AssumeNode(new LogicExp(OR,
                            new MatchExp(MatchType.PREFIX, target, matcher),
                            new MatchExp(MatchType.EXACT, target, matcher)));
                    break;
            }
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        if (m.scheme != null) {
            curr = addMatchGuard(m.scheme, "pkt.http.scheme","pkt.http.scheme", curr);
            helper.add(((AssumeNode)curr).getCondition(), ExpHelper.AND);
        }

        if (m.method != null) {
            Integer method = switch (m.method.value) {
                case "GET" -> PktHelper.MethodGET;
                case "HEAD" -> PktHelper.MethodHEAD;
                case "POST" -> PktHelper.MethodPOST;
                case "PUT" -> PktHelper.MethodPUT;
                case "DELETE" -> PktHelper.MethodDELETE;
                case "CONNECT" -> PktHelper.MethodCONNECT;
                case "OPTIONS" -> PktHelper.MethodOPTIONS;
                case "TRACE" -> PktHelper.MethodTRACE;
                case "PATCH" -> PktHelper.MethodPATCH;
                default -> {
                    assert false;
                    yield null;
                }
            };
            AssumeNode guard = new AssumeNode(new MatchExp(MatchType.EXACT, new VarExp("pkt.http.method"), new IntLiteralExp(method)));
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        if (m.authority != null) {
            AssumeNode guard = HostGuard(new DomainHost(m.authority.value), "pkt.host");
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        for (Map.Entry<String, HTTPMatchRequest.StringMatch> e: m.headers.entrySet()) {
            // pseudo header
            if (e.getKey().startsWith(":")) {
                AssumeNode guard = pseudoHeaderGuard(e);
                helper.add(guard.getCondition(), ExpHelper.AND);
                curr.addSucc(guard);
                curr = guard;
                continue;
            }
            if (e.getKey().equals("host")) {
                Assert(e.getValue() != null, "host header should not be null");
                AssumeNode hostGuard = HostGuard(new DomainHost(e.getValue().value, DomainHost.DomainKind.FQDN), "pkt.host");
                helper.add(hostGuard.getCondition(), ExpHelper.AND);
                curr.addSucc(hostGuard);
                curr = hostGuard;
                continue;
            }

            // common header
            addField("pkt.http.header." + e.getKey());
            if (e.getValue().value == null) {
                Exp cond = new NotExp(new MatchExp(MatchType.EXACT,
                        new VarExp("pkt.http.header." + e.getKey()), new IntLiteralExp(StringMap.NONE)));
                Node guard = new AssumeNode(cond);
                curr.addSucc(guard);
                curr = guard;
            } else {
                curr = addMatchGuard(e.getValue(), "pkt.http.header." + e.getKey(),"pkt.http.header." + e.getKey(), curr);
            }
            helper.add(((AssumeNode)curr).getCondition(), ExpHelper.AND);
        }

        if (m.port != -1) {
            AssumeNode guard = new AssumeNode(new MatchExp(MatchType.EXACT, new VarExp("pkt.port"), new IntLiteralExp(m.port)));
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        for (Map.Entry<String, String> e: m.sourceLabels.entrySet()) {
            addField("md.srclabel." + e.getKey());
            AssumeNode guard = AssumeStringEq(e.getValue(), "md.srclabel." + e.getKey(), "md.srclabel." + e.getKey());
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        if (!m.gateways.isEmpty()) {
            Node gwExit = new NullNode("GW-Exit");

            for (String s: m.gateways) {
                AssumeNode guard = AssumeStringEq(s, "md.gateway", "md.gateway");
                curr.addSucc(guard);
                guard.addSucc(gwExit);
                localHelper.add(guard.getCondition(), ExpHelper.OR);
            }

            curr = gwExit;
            helper.add(localHelper.getExp(), ExpHelper.AND);
        }

        for (Map.Entry<String, HTTPMatchRequest.StringMatch> e: m.queryParams.entrySet()) {
            addField("pkt.http.queryparam." + e.getKey());

            if (e.getValue().value == null) {
                Exp cond = new NotExp(new MatchExp(MatchType.EXACT,
                        new VarExp("pkt.http.queryparam." + e.getKey()), new IntLiteralExp(StringMap.NONE)));
                Node guard = new AssumeNode(cond);
                curr.addSucc(guard);
                curr = guard;
            } else {
                curr = addMatchGuard(e.getValue(), "pkt.http.queryparam." + e.getKey(),
                        "pkt.http.queryparam." + e.getKey(), curr);
            }

            helper.add(((AssumeNode)curr).getCondition(), ExpHelper.AND);
        }

        for (Map.Entry<String, HTTPMatchRequest.StringMatch> e: m.withoutHeaders.entrySet()) {
            // pseudo header
            if (e.getKey().startsWith(":")) {
                AssumeNode temp = pseudoHeaderGuard(e);
                AssumeNode guard = new AssumeNode(new NotExp(temp.getCondition()));
                helper.add(guard.getCondition(), ExpHelper.AND);
                curr.addSucc(guard);
                curr = guard;
                continue;
            }
            if (e.getKey().equals("host")) {
                MatchType type = switch (e.getValue().type) {
                    case EXACT -> MatchType.EXACT;
                    case PREFIX -> MatchType.UNKNOWN;
                    case REGEX -> { assert false; yield null; }
                };
                AssumeNode hostGuard = HostGuard(new DomainHost(e.getValue().value, DomainHost.DomainKind.FQDN), "pkt.host");
                AssumeNode guard = new AssumeNode(new NotExp(hostGuard.getCondition()));
                helper.add(guard.getCondition(), ExpHelper.AND);
                curr.addSucc(guard);
                curr = guard;
                continue;
            }

            // common header
            String field = "pkt.http.header." + e.getKey();
            addField(field);
            if (e.getValue().value == null) {
                Exp cond = new MatchExp(MatchType.EXACT,
                        new VarExp("pkt.http.header." + e.getKey()), new IntLiteralExp(StringMap.NONE));
                AssumeNode guard = new AssumeNode(cond);
                curr.addSucc(guard);
                curr = guard;
                helper.add(guard.getCondition(), ExpHelper.AND);
            } else {
                MatchType type = switch (e.getValue().type) {
                    case EXACT -> MatchType.EXACT;
                    case PREFIX -> MatchType.UNKNOWN;
                    case REGEX -> { assert false; yield null; }
                };
                IntLiteralExp matcher = new IntLiteralExp(StringMap.get(field, e.getValue().value), 32);
                MatchExp matchCond = new MatchExp(type, new VarExp("pkt.http.header." + e.getKey()), matcher);
                NotExp notCond = new NotExp(matchCond);
                AssumeNode guard = new AssumeNode(notCond);

                curr.addSucc(guard);
                curr = guard;
                helper.add(guard.getCondition(), ExpHelper.AND);
            }
        }

        if (m.sourceNamespace != null) {
            AssumeNode guard = AssumeStringEq(m.sourceNamespace, "md.srcns", "md.srcns");
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        // TODO: statPrefix field

        curr.addSucc(exit);
        Exp result = helper.getExp();
        return result != null ? result : new TrueExp();
    }

    private static Exp tlsMatchGenerate(L4MatchAttributes m, Node entry, Node exit) {
        Node curr = entry;
        ExpHelper helper = new ExpHelper();
        ExpHelper localHelper = new ExpHelper();

        if (!m.sniHosts.isEmpty()) {
            Node sniExit = new NullNode("Sni-Exit");

            for (Host h: m.sniHosts) {
                AssumeNode guard = HostGuard(h, "md.host");
                curr.addSucc(guard);
                guard.addSucc(sniExit);
                localHelper.add(guard.getCondition(), ExpHelper.OR);
            }

            curr = sniExit;
            helper.add(localHelper.getExp(), ExpHelper.AND);
        }

        if (!m.destinationSubnets.isEmpty()) {
            Node subnetExit = new NullNode("Subnet-Exit");

            for (Host h: m.destinationSubnets) {
                AssumeNode guard = HostGuard(h, "md.dstIP");
                curr.addSucc(guard);
                guard.addSucc(subnetExit);
                localHelper.add(guard.getCondition(), ExpHelper.OR);
            }

            curr = subnetExit;
            helper.add(localHelper.getExp(), ExpHelper.AND);
        }

        if (m.port != -1) {
            AssumeNode guard = new AssumeNode(new MatchExp(MatchType.EXACT,
                    new VarExp("md.port"), new IntLiteralExp(m.port)));
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        for (Map.Entry<String, String> e: m.sourceLabels.entrySet()) {
            addField("md.srclabel." + e.getKey());
            AssumeNode guard = AssumeStringEq(e.getValue(), "md.srclabel." + e.getKey(), "md.srclabel." + e.getKey());
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        if (!m.gateways.isEmpty()) {
            Node gwExit = new NullNode("GW-Exit");

            for (String s: m.gateways) {
                AssumeNode guard = AssumeStringEq(s, "md.gateway", "md.gateway");
                curr.addSucc(guard);
                guard.addSucc(gwExit);
                localHelper.add(guard.getCondition(), ExpHelper.OR);
            }

            curr = gwExit;
            helper.add(localHelper.getExp(), ExpHelper.AND);
        }

        if (m.sourceNamespace != null) {
            AssumeNode guard = AssumeStringEq(m.sourceNamespace, "md.srcns", "md.srcns");
            curr.addSucc(guard);
            curr = guard;
            helper.add(guard.getCondition(), ExpHelper.AND);
        }

        curr.addSucc(exit);

        Exp result = helper.getExp();
        return result != null ? result : new TrueExp();
    }

    private static void headerGenerate(Headers hdr, Node entry, Node exit) {
        assert hdr.request != null;
        Node curr = entry;

        for (Map.Entry<String, String> e: hdr.request.set.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith(":")) {
                LetNode action = pseudoHeaderAction(key, value);
                curr.addSucc(action);
                curr = action;
                continue;
            }
            if (key.equals("host")) {
                LetNode action = HostAction(new DomainHost(value, DomainHost.DomainKind.FQDN), "pkt.host");
                curr.addSucc(action);
                curr = action;
                continue;
            }
            curr = addAction(value, "pkt.http.header." + key, "pkt.http.header." + key, curr);
        }
        for (Map.Entry<String, String> e: hdr.request.add.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith(":")) {
                LetNode action = pseudoHeaderAction(key, value);
                curr.addSucc(action);
                curr = action;
                continue;
            }
            if (key.equals("host")) {
                LetNode action = HostAction(new DomainHost(value, DomainHost.DomainKind.FQDN), "pkt.host");
                curr.addSucc(action);
                curr = action;
                continue;
            }
            curr = addAction(value, "pkt.http.header." + key, "pkt.http.header." + key, curr);
        }
        for (String s: hdr.request.remove) {
            if (s.startsWith(":")) {
                LetNode action = pseudoHeaderAction(s, null);
                curr.addSucc(action);
                curr = action;
                continue;
            }
            curr = addAction(null, "pkt.http.header." + s, "pkt.http.header." + s, curr);
        }

        curr.addSucc(exit);
    }

    private static void routeGenerate(RouteDestination rd, Node entry, Node exit) {
        Node curr = entry;

        LetNode action = HostAction(rd.destination.host, "md.host");
        curr.addSucc(action);
        curr = action;

        if (rd.destination.subset != null)
            curr = addAction(rd.destination.subset, "md.subset", "md.subset", curr);
        if (rd.destination.port != null) {
            action = new LetNode(new VarExp("md.port"), new IntLiteralExp(rd.destination.port.number));
            curr.addSucc(action);
            curr = action;
        }
        if (rd.headers != null && rd.headers.request != null) {
            Node headerExit = new NullNode("Header-Exit");
            headerGenerate(rd.headers, curr, headerExit);
            curr = headerExit;
        }

        if (rd.weight != -1) {
            action = new LetNode(new VarExp("md.weight"), new IntLiteralExp(rd.weight));
            curr.addSucc(action);
            curr = action;
        }

        curr.addSucc(exit);
    }

    private static void redirectGenerate(HTTPRedirect rd, Node entry, Node exit) {
        Node curr = entry;

        LetNode action = new LetNode(new VarExp("pkt.http.type"), new IntLiteralExp(PktHelper.HTTPResponse));
        curr.addSucc(action);
        curr = action;

        if (rd.uri != null) {
            action = new LetNode(new VarExp("pkt.http.uri"), string2List(rd.uri, "pkt.http.uri", "/", 0));
            curr.addSucc(action);
            curr = action;
        }

        if (rd.authority != null) {
            action = HostAction(rd.authority, "pkt.host");
            curr.addSucc(action);
            curr = action;

            if (rd.authority.port != -1) {
                action = new LetNode(new VarExp("pkt.port"), new IntLiteralExp(rd.authority.port));
                curr.addSucc(action);
                curr = action;
            }
        }

        if (rd.port != -1) {
            action = new LetNode(new VarExp("pkt.port"), new IntLiteralExp(rd.port));
            curr.addSucc(action);
            curr = action;
        }

        if (rd.derivePort != null && rd.derivePort.equals(HTTPRedirect.FROM_PROTOCOL_DEFAULT)) {
            Node deriveExit = new NullNode("DerivePort-Exit");
            AssumeNode guard;

            guard = new AssumeNode(new MatchExp(MatchType.EXACT,
                    new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTP)));
            action = new LetNode(new VarExp("pkt.port"), new IntLiteralExp(80));
            curr.addSucc(guard);
            guard.addSucc(action);
            action.addSucc(deriveExit);

            guard = new AssumeNode(new MatchExp(MatchType.EXACT,
                    new VarExp("md.type"), new IntLiteralExp(PktHelper.TypeHTTPS)));
            action = new LetNode(new VarExp("pkt.port"), new IntLiteralExp(443));
            curr.addSucc(guard);
            guard.addSucc(action);
            action.addSucc(deriveExit);

            curr = deriveExit;
        }

        if (rd.scheme != null)
            curr = addAction(rd.scheme, "pkt.http.scheme", "pkt.http.scheme", curr);

        if (rd.redirectCode != -1) {
            action = new LetNode(new VarExp("pkt.http.status"), new IntLiteralExp(rd.redirectCode));
            curr.addSucc(action);
            curr = action;
        }

        curr.addSucc(exit);
    }

    private static void directResponseGenerate(HTTPDirectResponse dr, Node entry, Node exit) {
        Node curr = entry;

        LetNode action = new LetNode(new VarExp("pkt.http.type"), new IntLiteralExp(PktHelper.HTTPResponse));
        curr.addSucc(action);
        curr = action;

        action = new LetNode(new VarExp("pkt.http.status"), new IntLiteralExp(dr.status));
        curr.addSucc(action);
        curr = action;

        if (dr.body != null)
            curr = addAction(dr.body.value, "pkt.http.body", "pkt.http.body", curr);

        curr.addSucc(exit);
    }

    private static void delegateGenerate(Delegate dg, Node entry, Node exit) {
        // TODO: delegate rules

        Node curr = entry;

        AssumeNode delegated = new AssumeNode(new MatchExp(MatchType.EXACT,
                new VarExp("md.delegate"), new IntLiteralExp(PktHelper.Delegated)));
        AssumeNode notDelegated = new AssumeNode(new NotExp(new MatchExp(MatchType.EXACT,
                new VarExp("md.delegate"), new IntLiteralExp(PktHelper.Delegated))));
        LetNode setDelegate = new LetNode(new VarExp("md.delegate"), new IntLiteralExp(PktHelper.ToDelegate));

        curr.addSucc(notDelegated);
        curr = notDelegated;

        if (dg.name != null) {
            LetNode action = HostAction(dg.name, "md.host");
            curr.addSucc(action);
            curr = action;
        }

        if (dg.namespace != null)
            curr = addAction(dg.namespace, "md.dstns","md.dstns", curr);

        curr.addSucc(setDelegate);
        setDelegate.addSucc(exit);

        entry.addSucc(delegated);
        delegated.addSucc(exit);
    }

    private static void rewriteGenerate(HTTPRewrite rw, Node entry, Node exit) {
        Node curr = entry;

        if (rw.uri != null) {
            LetNode action = new LetNode(new VarExp("pkt.http.uri"), string2List(rw.uri, "pkt.http.uri", "/", 0));
            curr.addSucc(action);
            curr = action;
        }

        if (rw.authority != null) {
            LetNode action = HostAction(rw.authority, "pkt.host");
            curr.addSucc(action);
            curr = action;
        }

        // TODO: uriRegexRewrite field

        curr.addSucc(exit);
    }

    // boundary between main resource-generate functions and helper functions

    public static AssumeNode HostGuard(Host host, String targetName) {
        if (host instanceof DomainHost) {
            return DomainHostGuard((DomainHost) host, targetName);
        } else {
            return IPHostGuard((IPHost) host, targetName);
        }
    }

    public static AssumeNode DomainHostGuard(DomainHost host, String targetName) {
        VarExp target = new VarExp(targetName);
        Exp matchCond = null;
        switch(host.kind) {
            case FQDN: {
                matchCond = new MatchExp(MatchType.EXACT, target, string2List(host.domain, "pkt.host", "\\.", 1));
                break;
            }
            case WILDCARD: {
                assert host.domain.startsWith("*");
                // special case single *
                if (host.domain.equals("*")) {
                    matchCond = new TrueExp();
                } else {
                    matchCond = new MatchExp(MatchType.PREFIX, target, string2List(host.domain.substring(2), "pkt.host", "\\.", 1));
                }
                break;
            }
            default: assert false;
        }

        if (host.port != -1) {
            matchCond = new LogicExp(AND, matchCond,
                    new MatchExp(MatchType.EXACT, new VarExp("pkt.port"), new IntLiteralExp(host.port)));
        }

        return new AssumeNode(matchCond);
    }

    public static AssumeNode IPHostGuard(IPHost host, String targetName) {
        VarExp target = new VarExp(targetName);
        Exp matchCond = null;
        switch(host.kind) {
            case IP: {
                matchCond = new MatchExp(MatchType.EXACT, target, new IntLiteralExp(IPHelper.getIPAddress(host.ip)));
                break;
            }
            case SUBNET: {
                matchCond = new MatchExp(MatchType.EXACT, new ArithExp(ArithExp.ArithType.AND, target,
                        new IntLiteralExp(IPHelper.getIPMask(host.ip))), new IntLiteralExp(IPHelper.getIPAddress(host.ip)));
                break;
            }
            default: assert false;
        }
        return new AssumeNode(matchCond);
    }

    // standard exact match
    public static AssumeNode AssumeStringEq(String value, String field, String targetName) {
        IntLiteralExp matcher = new IntLiteralExp(StringMap.get(field, value), 32);
        VarExp target = new VarExp(targetName);
        MatchExp matchCond = new MatchExp(MatchType.EXACT, target, matcher);
        return new AssumeNode(matchCond);
    }

    public static AssumeNode addMatchGuard(HTTPMatchRequest.StringMatch m, String field, String targetName, Node curr) {
        MatchType type = null;
        switch (m.type) {
            case EXACT: type = MatchType.EXACT; break;
            case PREFIX: type = MatchType.UNKNOWN; break;
            case REGEX: assert false;   // in common string match, we do not support regex
        }
        IntLiteralExp matcher = new IntLiteralExp(StringMap.get(field, m.value), 32);
        VarExp target = new VarExp(targetName);
        MatchExp matchCond = new MatchExp(type, target, matcher);
        AssumeNode guard = new AssumeNode(matchCond);
        curr.addSucc(guard);
        return guard;
    }

    public static LetNode HostAction(Host host, String targetName) {
        VarExp target = new VarExp(targetName);
        Exp value = null;
        if (host instanceof DomainHost) {
            DomainHost dHost = (DomainHost) host;
            if (dHost.kind == DomainHost.DomainKind.FQDN) {
                value = string2List(dHost.domain, "pkt.host", "\\.", 1);
            } else {
               Assert(false, "Wildcard domain is not supported");
            }
        } else if (host instanceof IPHost) {
            IPHost iHost = (IPHost) host;
            if (iHost.kind == IPHost.IPKind.IP) {
                value = new IntLiteralExp(IPHelper.getIPAddress(iHost.ip));
            } else {
                Assert(false, "IP subnet is not supported");
            }
        } else {
            Assert(false, "Unknown host type");
        }

        Assert(value != null, "HostAction value is null");
        return new LetNode(target, value);
    }

    public static LetNode setVarString(String value, String field, String targetName) {
        IntLiteralExp matcher = new IntLiteralExp(StringMap.get(field, value), 32);
        VarExp target = new VarExp(targetName);
        return new LetNode(target, matcher);
    }

    public static LetNode addAction(String str, String field, String targetName, Node curr) {
        LetNode action = setVarString(str, field, targetName);
        curr.addSucc(action);
        return action;
    }

    public static ListExp string2List(String str, String field, String delimiter, int mode) {
        String[] tmp;

        if (delimiter.equals("/")) {
            tmp = str.equals("/") ? new String[0] : str.substring(1).split("/");
        } else {
            tmp = str.split(delimiter);
        }

        ArrayList<Exp> list = new ArrayList<>();
        boolean reverse = (mode & 1) > 0;
        boolean integer = (mode & 2) > 0;
        if (reverse) {
            for (int i = tmp.length - 1; i >= 0; i--) {
                if (integer) list.add(new IntLiteralExp(Integer.valueOf(tmp[i]), 32));
                else list.add(new IntLiteralExp(StringMap.get(field, tmp[i]), 32));
            }
        } else {
            for (String s: tmp) {
                if (integer) list.add(new IntLiteralExp(Integer.valueOf(s), 32));
                else list.add(new IntLiteralExp(StringMap.get(field, s), 32));
            }
        }
        return new ListExp(list, new IntLiteralExp(list.size(), 32));
    }

    public static void addField(String field) {
        fieldSet.add(field);
    }

    public static void addLabel(String label) {
        dstLabels.add(label);
    }

    // for pseudo headers like :authority, :method, :scheme, :path
    public static AssumeNode pseudoHeaderGuard(Map.Entry<String, HTTPMatchRequest.StringMatch> entry) {
        String key = entry.getKey();
        HTTPMatchRequest.StringMatch value = entry.getValue();
        switch (key) {
            case ":path": {
                Exp target = new VarExp("pkt.http.uri");
                Exp matchCond;
                switch (value.type) {
                    case EXACT:
                        matchCond = new MatchExp(MatchType.EXACT, target,
                                string2List(value.value, "pkt.http.uri", "/", 0));
                        break;
                    case PREFIX:
                        ListExp matcher = string2List(value.value, "pkt.http.uri", "/", 0);
                        matchCond = new LogicExp(OR,
                                new MatchExp(MatchType.PREFIX, target, matcher),
                                new MatchExp(MatchType.EXACT, target, matcher));
                        break;
                    case REGEX:
                        assert value.value.endsWith("/*");
                        matcher = string2List(value.value.substring(0, value.value.length() - 2),
                                "pkt.http.uri", "/", 0);
                        matchCond = new LogicExp(OR,
                                new MatchExp(MatchType.PREFIX, target, matcher),
                                new MatchExp(MatchType.EXACT, target, matcher));
                        break;
                    default:
                        assert false;
                        return null;
                }
                return new AssumeNode(matchCond);
            }
            case ":authority": {
                return HostGuard(new DomainHost(value.value), "pkt.host");
            }
            case ":method": {
                Exp target = new VarExp("pkt.http.method");
                Integer method = switch (value.value) {
                    case "GET" -> PktHelper.MethodGET;
                    case "HEAD" -> PktHelper.MethodHEAD;
                    case "POST" -> PktHelper.MethodPOST;
                    case "PUT" -> PktHelper.MethodPUT;
                    case "DELETE" -> PktHelper.MethodDELETE;
                    case "CONNECT" -> PktHelper.MethodCONNECT;
                    case "OPTIONS" -> PktHelper.MethodOPTIONS;
                    case "TRACE" -> PktHelper.MethodTRACE;
                    case "PATCH" -> PktHelper.MethodPATCH;
                    default -> {
                        assert false;
                        yield null;
                    }
                };
                return new AssumeNode(new MatchExp(MatchType.EXACT, target, new IntLiteralExp(method)));
            }
            case ":scheme": {
                return AssumeStringEq(value.value, "pkt.http.scheme", "pkt.http.scheme");
            }
            default: {
                // we only support pseudo headers above
                assert false;
                return null;
            }
        }
    }

    public static LetNode pseudoHeaderAction(String key, String value) {
        if (value == null) {
            Exp target;
            switch (key) {
                case ":path": target = new VarExp("pkt.http.uri"); break;
                case ":authority": target = new VarExp("pkt.host"); break;
                case ":method": target = new VarExp("pkt.http.method"); break;
                case ":scheme": target = new VarExp("pkt.http.scheme"); break;
                default: throw new RuntimeException("unknown pseudo header.");
            }
            return new LetNode(target, new IntLiteralExp(StringMap.NONE));
        }

        switch (key) {
            case ":path": {
                return new LetNode(new VarExp("pkt.http.uri"),
                        string2List(value, "pkt.http.uri", "/", 0));
            }
            case ":authority": {
                return HostAction(new DomainHost(value, DomainHost.DomainKind.FQDN), "pkt.host");
            }
            case ":method": {
                Integer method = switch (value) {
                    case "GET" -> PktHelper.MethodGET;
                    case "HEAD" -> PktHelper.MethodHEAD;
                    case "POST" -> PktHelper.MethodPOST;
                    case "PUT" -> PktHelper.MethodPUT;
                    case "DELETE" -> PktHelper.MethodDELETE;
                    case "CONNECT" -> PktHelper.MethodCONNECT;
                    case "OPTIONS" -> PktHelper.MethodOPTIONS;
                    case "TRACE" -> PktHelper.MethodTRACE;
                    case "PATCH" -> PktHelper.MethodPATCH;
                    default -> {
                        assert false;
                        yield null;
                    }
                };
                return new LetNode(new VarExp("pkt.http.method"), new IntLiteralExp(method));
            }
            case ":scheme": {
                return new LetNode(new VarExp("pkt.http.scheme"),
                        new IntLiteralExp(StringMap.get("pkt.http.scheme", value), 32));
            }
            default: {
                // we only support pseudo headers above
                assert false;
                return null;
            }
        }
    }
}
