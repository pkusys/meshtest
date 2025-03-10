import http.server
import json
import queue
import socketserver
import threading

from scapy.all import *
from scapy.layers.inet import TCP, IP

pkt_queue = queue.Queue()


def extract_http_md(line: str):
    # split with ' '
    mds = line.split()
    methods = ["GET", "POST", "HEAD", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE"]
    md_method = mds[0].upper()
    md_uri = mds[1]
    md_version = mds[2]
    if md_method not in methods:
        return None
    return md_method, md_uri, md_version


def process_http(raw_string: str):
    lines = raw_string.splitlines()
    lines = [line for line in lines if line.strip()]
    fields = {}

    md_method, md_uri, md_version = extract_http_md(lines[0])
    if md_method == None:
        return None
    # host: www.bookinfo.com
    # parse all header fields
    fields["type"] = "HTTP"
    fields[":method"] = md_method
    fields[":uri"] = md_uri
    fields[":version"] = md_version
    for line in lines[1:]:
        k = line.split(":")[0].strip()
        v = line.split(":")[1].strip()
        if not k.startswith("x-"):
            fields[k] = v

    # if idx not in the packet, ignore it
    if "idx" in fields:
        return fields

    return None


def process_tcp(pkt, raw_string: str):
    line = raw_string[3:]
    assert line.startswith("idx"), "TCP packet must have idx"
    fields = {}
    # line is like "idx: 1, stage: 0"
    idx = line.split(",")[0].split(":")[1].strip()
    stage = line.split(",")[1].split(":")[1].strip()
    fields["idx"] = idx
    fields["stage"] = stage
    fields["dstIP"] = pkt[IP].dst
    fields["srcIP"] = pkt[IP].src
    fields["srcPort"] = pkt[TCP].sport
    fields["dstPort"] = pkt[TCP].dport
    fields["type"] = "TCP"
    return fields


def process_packet(packet):
    if not packet.haslayer(TCP):
        return
    if packet.haslayer(Raw):
        r = packet[0][Raw].load
        try:
            # HTTP or TCP
            raw_string = r.decode("utf-8")
            if raw_string.startswith("TCP"):  # starts with magic number TCP
                # tcp
                fields = process_tcp(packet, raw_string)
            else:
                # http
                fields = process_http(raw_string)
            if fields != None:
                pkt_queue.put(fields)
        except:
            return


def sniff_packets():
    interfaces = ["eth0", "lo"]
    print(interfaces)
    pkt = sniff(iface=interfaces, filter="inbound and tcp", prn=process_packet)


def dump_packets(path: str):
    recv_pkts = {}  # key: idx, value: [pkt]
    if os.path.exists(path):
        os.remove(path)
    while True:
        if not pkt_queue.empty():
            pkt = pkt_queue.get()
            idx = pkt["idx"]

            if idx in recv_pkts:
                if str(pkt) not in [str(x) for x in recv_pkts[idx]]:
                    recv_pkts[idx].append(pkt)
            else:
                recv_pkts[idx] = [pkt]
            pkt_queue.task_done()
        else:
            time.sleep(1)
        # if parent dir does not exist, recursively create path
        if not os.path.exists(os.path.dirname(path)):
            os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(recv_pkts, f, indent=4, sort_keys=True)


def server():
    port = 9080
    handler = http.server.SimpleHTTPRequestHandler
    with socketserver.TCPServer(("", port), handler) as httpd:
        print("serving at port ", port)
        httpd.serve_forever()


def main(args):
    server_thread = threading.Thread(target=server)
    sniff_thread = threading.Thread(target=sniff_packets)
    dump_thread = threading.Thread(target=dump_packets, args=(args[0],))
    server_thread.start()
    sniff_thread.start()
    dump_thread.start()
    server_thread.join()
    sniff_thread.join()
    dump_thread.join()


if __name__ == "__main__":
    main(sys.argv[1:])
