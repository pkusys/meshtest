# Model

## Documentation of Fields

In our implementation, strings are encoded into integers for Z3 solver.

### Packet

| Field                 | Type                   | Initializer    | Remark                                                                      |
|-----------------------|------------------------|----------------|-----------------------------------------------------------------------------|
| pkt.host              | List[String]/List[Int] | List[Symbolic] | Reserve 0-255 in StringMap for IP address. List is the reverse form of host |
| pkt.port              | Int                    | Symbolic       |                                                                             |
| pkt.http.type         | Int                    | HTTPRequest    | Value must be in HTTPTypeList                                               |
| pkt.http.uri          | List[String]           | List[Symbolic] |                                                                             |
| pkt.http.scheme       | String                 | Symbolic       |                                                                             |
| pkt.http.method       | Int                    | Symbolic       | Value must be in MethodList                                                 |
| pkt.http.header.*     | String                 | Symbolic       | Concrete fields are collected from VS and DR                                |
| pkt.http.queryparam.* | String                 | Symbolic       | Concrete fields are collected from VS and DR                                |
| pkt.http.status       | Int                    |                |                                                                             |
| pkt.http.body         | String                 |                |                                                                             |

### Metadata

| Field         | Type                   | Initializer | Remark                                                                      |
|---------------|------------------------|-------------|-----------------------------------------------------------------------------|
| md.host       | List[String]/List[Int] | pkt.host    | Reserve 0-255 in StringMap for IP address. List is the reverse form of host |
| md.port       | Int                    | pkt.port    |                                                                             |
| md.type       | Int                    | Symbolic    | Value must be in TypeList                                                   |
| md.proxy      | Int                    | Symbolic    | Value must be in ProxyList                                                  |
| md.gateway    | String                 | Symbolic    |                                                                             |
| md.srcns      | String                 | Symbolic    |                                                                             |
| md.subset     | String                 | NONE        | Set NONE to skip subset matching in the DR                                  |
| md.delegate   | Int                    | NoDelegate  | This field indicates whether the packet is delegated to another VS          |
| md.srclabel.* | String                 | Symbolic    | Concrete fields are collected from VS and DR                                |
| md.dstns      | String                 |             |                                                                             |
| md.dstlabel.* | String                 |             |                                                                             |
| md.dstIP      | String                 | Symbolic    | encoded IP address string into integer                                      |


## Configuration in Our Scope

As for pseudo header, uri, scheme, method and authority can interleave with headers.

| configuration                        |
|--------------------------------------|
| vs.metadata.name                     |
| vs.hosts                             |
| vs.gateways                          |
| vs.http.match.uri                    |
| vs.http.match.scheme                 |
| vs.http.match.method                 |
| vs.http.match.authority              |
| vs.http.match.headers                |
| vs.http.match.port                   |
| vs.http.match.sourceLabels           |
| vs.http.match.gateways               |
| vs.http.match.withoutHeaders         |
| vs.http.route.destination.host       |
| vs.http.route.destination.subset     |
| vs.http.route.destination.port       |
| vs.http.route.headers.request.set    |
| vs.http.route.headers.request.add    |
| vs.http.route.headers.request.remove |
| vs.http.delegate.name                |
| vs.http.rewrite.uri                  |
| vs.http.rewrite.authority            |
| vs.http.headers.request.set          |
| vs.http.headers.request.add          |
| vs.http.headers.request.remove       |
| vs.tcp.match.port                    |
| vs.tcp.match.sourceLabels            |
| vs.tcp.match.gateways                |
| vs.tcp.route.destination.host        |
| vs.tcp.route.destination.subset      |
| vs.tcp.route.destination.port        |
| dr.host                              |
| dr.subsets.labels                    |
| dr.workloadSelector.matchLabels      |
| se.hosts                             |
| se.ports.number                      |
| se.ports.targetPort                  |
| se.workloadSelector.labels           |
| gw.servers.port.number               |
| gw.servers.hosts                     |
| gw.selector                          |
| svc.metadata.name                    |
| svc.selector                         |
| svc.ports.port                       |
| svc.ports.targetPort                 |
| pod.metadata.name                    |
| pod.metadata.labels                  |

I divide the configuration into 9 categories by its effect on our network. This division is based on my understanding, maybe change in the future.

| effect           | interleaving configuration                                                                                                                                                                                                                                                                                                                                                                                                       |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Host             | vs.metadata.name, vs.hosts, vs.http.match.authority, vs.http.match.headers, vs.http.match.withoutHeaders, vs.http.route.destination.host, vs.http.route.headers.request.set, vs.http.route.headers.request.add, vs.http.delegate.name, vs.http.rewrite.authority, vs.http.rewrite.authority, vs.http.headers.request.set, vs.http.headers.request.add, dr.host, se.hosts, gw.servers.hosts, svc.metadata.name, pod.metadata.name |
| Port             | vs.http.match.port, vs.http.route.destination.port, vs.tcp.match.port, vs.tcp.route.destination.port, se.ports.number, se.ports.targetPort, gw.servers.port.number, svc.ports.number, svc.ports.targetPort                                                                                                                                                                                                                       |
| Uri              | vs.http.match.uri, vs.http.match.headers, vs.http.match.withoutHeaders, vs.http.rewrite.uri                                                                                                                                                                                                                                                                                                                                      |
| Scheme           | vs.http.match.scheme, vs.http.match.headers, vs.http.match.withoutHeaders                                                                                                                                                                                                                                                                                                                                                        |
| Method           | vs.http.match.method, vs.http.match.headers, vs.http.match.withoutHeaders                                                                                                                                                                                                                                                                                                                                                        |
| Header           | vs.http.match.headers, vs.http.match.withoutHeaders, vs.http.route.headers.request.set, vs.http.route.headers.request.add, vs.http.route.headers.request.remove, vs.http.headers.request.set, vs.http.headers.request.add, vs.http.headers.request.remove                                                                                                                                                                        |
| Gateway          | vs.gateways, vs.http.match.gateways, vs.tcp.match.gateways                                                                                                                                                                                                                                                                                                                                                                       |
| SourceLabel      | vs.http.match.sourceLabels, vs.tcp.match.sourceLabels                                                                                                                                                                                                                                                                                                                                                                            |
| DestinationLabel | vs.http.route.destination.subset, vs.tcp.route.destination.subset, dr.subsets.labels, dr.workloadSelector.matchLabels, se.workloadSelector.labels, gw.selector, svc.selector, pod.metadata.labels                                                                                                                                                                                                                                |
