import random
import time

import requests
from scapy.all import *
import json

from scapy.layers.inet import IP, TCP
from concurrent.futures import ThreadPoolExecutor

lock = threading.Lock()
log_lock = threading.Lock()


class packet:
    def __init__(self, type, index, stage):
        self.type = type
        self.index = index
        self.stage = stage

    def __str__(self):
        return str(self.__dict__)

    def __repr__(self):
        return str(self.__dict__)

    def send(self):
        pass

    def dump(self):
        return self.__dict__


class http_packet(packet):
    def __init__(
            self, host, port, uri, pkt_type, method, headers, index, stage, address="1.1.1.1"
    ):
        super().__init__("HTTP", index, stage)

        self.host = host
        self.port = port
        self.method = method
        self.pkt_type = pkt_type
        self.uri = uri
        self.headers = headers
        self.scheme = "http"
        self.address = address

    def send(self):
        # compose an HTTP packet
        if self.pkt_type == "REQUEST":
            if self.address.endswith(".default.svc.cluster.local") and \
                    str(self.host).endswith(".default.svc.cluster.local"):
                url = (
                        self.scheme
                        + "://"
                        + self.host
                        + ":"
                        + str(self.port)
                        + "/"
                        + self.uri
                )
            else:
                url = (
                        self.scheme
                        + "://"
                        + self.address
                        + ":"
                        + str(self.port)
                        + "/"
                        + self.uri
                )

            hdrs = {
                "idx": str(self.index),
                "stage": self.stage,
                "Host": self.host,
            }

            hdrs = {**hdrs, **self.headers}
            # print(self.host + ":" + str(self.port) + "/" + self.uri)
            # todo: more methods
            r = requests.get(url=url, headers=hdrs, timeout=1)
            print(url, hdrs)
            print(r.status_code)

            if r.status_code >= 500 and r.status_code < 600:
                assert False, "Server error"
        else:
            assert False, "Only HTTP REQUEST is supported"

    def dump(self):
        return {**super().dump(), **self.__dict__}


class https_packet(packet):
    def __init__(
            self, host, port, uri, pkt_type, method, headers, index, stage, address="1.1.1.1"
    ):
        super().__init__("HTTPS", index, stage)

        self.host = host
        self.port = port
        self.method = method
        self.pkt_type = pkt_type
        self.uri = uri
        self.headers = headers
        self.scheme = "https"
        self.address = address

    def send(self):
        # todo: do not send HTTPS packet for now
        print("HTTPS packet omitted")
        return

        # OMITTED: compose an HTTPS packet
        # if self.pkt_type == "REQUEST":
        #     url = (
        #             self.scheme
        #             + "://"
        #             + self.address
        #             + ":"
        #             + str(self.port)
        #             + "/"
        #             + self.uri
        #     )
        #
        #     hdrs = {
        #         "idx": str(self.index),
        #         "stage": self.stage,
        #         "Host": self.host + ":" + str(self.port),
        #     }
        #
        #     hdrs = {**hdrs, **self.headers}
        #
        #     r = requests.get(url=url, headers=hdrs, verify=False, timeout=1)
        #     print(url, hdrs)
        #     print(r.status_code)
        #
        #     if r.status_code >= 500 and r.status_code < 600:
        #         assert False, "Server error"
        # else:
        #     assert False, "Only HTTP REQUEST is supported"

    def dump(self):
        return {**super().dump(), **self.__dict__}


class tls_packet(packet):
    def __init__(self, index):
        super().__init__("TLS", index)

    def send(self):
        print("TLS packet omitted")
        pass

    def dump(self):
        return {**super().dump(), **self.__dict__}


class tcp_packet(packet):
    @staticmethod
    def int_to_ip(ip_int: int):
        byte1 = (ip_int >> 24) & 0xFF
        byte2 = (ip_int >> 16) & 0xFF
        byte3 = (ip_int >> 8) & 0xFF
        byte4 = ip_int & 0xFF

        ip_str = f"{byte1}.{byte2}.{byte3}.{byte4}"
        return ip_str

    @staticmethod
    def get_random_ip():
        ip1 = random.randint(0x00000001, 0x0A000000)
        ip2 = random.randint(0x0A000000, 0x0AFFFFFF)
        ip3 = random.randint(0x0B000000, 0x0CA80000)
        ip4 = random.randint(0x0CA80000, 0x0CA8FFFF)
        ip5 = random.randint(0x0CA90000, 0xFFFFFFFF)
        ip_list = [ip1, ip2, ip3, ip4, ip5]
        return random.choice(ip_list)

    def __init__(self, dst_host, dst_ip, dst_port, index, stage):
        super().__init__("TCP", index, stage)

        if dst_ip == "ANY":
            self.dst_ip = socket.gethostbyname(dst_host)
        else:
            # int to ip str
            self.dst_ip = dst_ip
        if dst_port == "ANY":
            dst_port = "10000"
        self.dst_port = int(dst_port)
        self.body = "TCPidx: " + str(index) + ", stage: " + stage

    def send(self):
        # compose an TCP packet
        pkt = IP(dst=self.dst_ip) / TCP(dport=self.dst_port) / self.body
        send(pkt)

    def show(self):
        pkt = IP(dst=self.dst_ip) / TCP(dport=self.dst_port) / self.body
        pkt.show()

    def dump(self):
        return {**super().dump(), **self.__dict__}


def gen_pkt(pkt, stage: str, target_IP: str) -> packet:
    idx = int(pkt["test_metadata"]["index"])

    tcp_host_len = int(pkt["pkt.host_len"])
    tcp_host = ""
    for i in range(tcp_host_len):
        host_element = pkt["pkt.host_" + str(tcp_host_len - i - 1)]
        if tcp_host == "":
            tcp_host = tcp_host + host_element
        else:
            tcp_host = tcp_host + "." + host_element

    if pkt["md.type"] == "HTTP" or pkt["md.type"] == "HTTPS":
        http_uri_len = int(pkt["pkt.http.uri_len"])
        http_uri = ""
        for i in range(http_uri_len):
            uri_element = pkt["pkt.http.uri_" + str(i)]
            if http_uri == "":
                http_uri = http_uri + uri_element
            else:
                http_uri = http_uri + "/" + uri_element

        http_port = int(pkt["pkt.port"]) if pkt["pkt.port"] != "ANY" else 80
        http_type = pkt["pkt.http.type"].upper()
        assert http_type == "REQUEST", "Only HTTP REQUEST is supported"

        http_method = pkt["pkt.http.method"]
        http_scheme = pkt["pkt.http.scheme"]
        http_headers = {}
        # for all fields start with pkt.http.header, add to http_headers
        for key in pkt:
            if key.startswith("pkt.http.header"):
                if pkt[key] != "NONE":
                    http_headers[key[16:]] = pkt[key]

        if pkt["md.type"] == "HTTPS":
            return https_packet(
                host=tcp_host,
                uri=http_uri,
                pkt_type=http_type,
                method=http_method,
                headers=http_headers,
                port=http_port,
                index=idx,
                stage=stage,
                address=target_IP,
            )
        else:
            return http_packet(
                host=tcp_host,
                uri=http_uri,
                pkt_type=http_type,
                method=http_method,
                headers=http_headers,
                port=http_port,
                index=idx,
                stage=stage,
                address=target_IP,
            )
    elif pkt["md.type"] == "TCP":
        # TCP is based on IP address and port
        # tcp dst ip cannot be ANY!
        assert "pkt.port" in pkt, "TCP packet must have dst port"
        # tcp_port = int(pkt["pkt.port"]) if pkt["pkt.port"] != "ANY" else 10000
        return tcp_packet(
            dst_host=tcp_host,
            dst_ip=pkt["pkt.dstIP"],
            dst_port=pkt["pkt.port"],
            index=idx,
            stage=stage,
        )
    elif pkt["md.type"] == "TLS":
        return tls_packet(index=idx)
    else:
        assert False, "Unsupported packet type"


def test_send_packet():
    pkt = tcp_packet(
        dst_host="productpage-tcp", dst_ip="ANY", dst_port="10000", index=0
    )
    pkt.show()
    pkt.send()


def send_packet(pkt: packet):
    try:
        lock.acquire()
        try:
            print("#############")
            pkt.send()
            print("Sent packet " + str(pkt.index))
            print("#############")
        finally:
            lock.release()
        return pkt, True
    except Exception as e:
        print(e)
        print("Failed to send packet: " + str(pkt))
        print("#############")
        return pkt, False


def send_loop(pkt: packet, num: int, pkt_log):
    wait_time = 0
    if pkt.type == "TCP":
        # num *= 2
        num = 1
        for i in range(num):
            time.sleep(wait_time)
            _, succ = send_packet(pkt)
            update_pkt_log(pkt_log, pkt, succ)
            wait_time = 1
    else:
        failed_num = 0
        for i in range(num):
            time.sleep(wait_time)
            _, succ = send_packet(pkt)
            update_pkt_log(pkt_log, pkt, succ)
            wait_time = 0.1
            # if not succ:
            #     failed_num += 1
            # if succ:
            #     wait_time = 0.1
            # else:
            #     wait_time = 0.5 * (failed_num + 1)


def update_pkt_log(pkt_log, pkt, succ):
    log_lock.acquire()
    if pkt.index not in pkt_log:
        pkt_log[pkt.index] = {"stat": {"sent": 0, "failed": 0}, "pkt": pkt.dump()}

    if succ:
        pkt_log[pkt.index]["stat"]["sent"] = pkt_log[pkt.index]["stat"]["sent"] + 1
    else:
        pkt_log[pkt.index]["stat"]["failed"] = pkt_log[pkt.index]["stat"]["failed"] + 1

    log_lock.release()


def dump_sender_log(log_path, pkt_log, finished_path, finished: threading.Event()):
    while not finished.is_set():
        # sort pkt_log by key
        log = {key: pkt_log[key] for key in sorted(pkt_log)}
        time.sleep(1)
        log_lock.acquire()
        with open(log_path, "w") as f:
            json.dump(log, f, indent=4)
            f.close()
        log_lock.release()
    with open(finished_path, "w") as f:
        f.write("done")
        f.close()
    print("All packets sent")


def main(argv):
    # show /app/config
    folder_path = "/app/config"
    files = [f for f in os.listdir(folder_path) if os.path.isfile(os.path.join(folder_path, f))]
    for f in files:
        print(f)

    assert (
            len(argv) >= 3
    ), "Usage: python3 sender.py <json file> <cluster> <stage> [<gateway IP>]"
    if sys.version_info < (3, 9):
        print("Please use Python 3.9 or newer")
        sys.exit(1)

    json_file = argv[0]
    cluster = argv[1]
    stage = argv[2]
    target_ip = argv[3] if len(argv) >= 4 else "1.1.1.1"

    parent_dir = os.path.dirname(json_file)
    sender_id = json_file.split("/")[-1].split(".")[0]
    fail_to_encode_path = os.path.join(
        parent_dir, cluster + "/" + sender_id + "_fail_to_encode.json"
    )
    log_path = os.path.join(parent_dir, cluster + "/" + sender_id + "_log.json")
    finished_path = os.path.join(parent_dir, sender_id + ".done")

    fail_to_encode = []

    # read json to list of packets
    with open(json_file, "r") as f:
        data = json.load(f)
        pkts = []
        for _, x in enumerate(data):
            try:
                pkts.append(gen_pkt(x, stage, target_ip))
            except:
                fail_to_encode.append(x)
        f.close()

    with open(fail_to_encode_path, "w") as f:
        json.dump(fail_to_encode, f, indent=4)
        f.close()

    pkt_log = {}  # index -> {stat, pkt}
    finished = threading.Event()
    dump_log_thread = threading.Thread(target=dump_sender_log, args=(log_path, pkt_log, finished_path, finished))
    dump_log_thread.start()
    with ThreadPoolExecutor(max_workers=10) as executor:
        executor.map(send_loop, pkts, [10] * len(pkts), [pkt_log] * len(pkts))
    finished.set()
    dump_log_thread.join()

    while True:
        try:
            time.sleep(2)
        except KeyboardInterrupt:
            print("Exiting...")
            return


if __name__ == "__main__":
    main(sys.argv[1:])
