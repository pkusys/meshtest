import json
import yaml
import os
import sys


def encode_ip(ip: str):
    ip = ip.split(".")
    res = 0
    for i in range(4):
        res = res * 256 + int(ip[i])
    return res


def check_pkt(yaml_conf, actual_packet: list, test_packet: list, ref_packet: list):
    """
    Check packet of one index is correct or not
    """
    pkt_result = []
    md_result = []
    all_pass = True
    for packet in actual_packet:
        ty = packet["type"]
        pod = packet["pod"]
        if ty == "HTTP":
            http_pkt_result = check_http_pkt(packet, test_packet, ref_packet)
            http_md_result = check_http_md(yaml_conf, packet, test_packet, ref_packet)
            pkt_result.append({pod: http_pkt_result})
            md_result.append({pod: http_md_result})
            if http_pkt_result != "Pass" or http_md_result != "Pass":
                all_pass = False
        elif ty == "TCP":
            tcp_pkt_result = check_tcp_pkt(packet, test_packet, ref_packet)
            tcp_md_result = check_tcp_md(yaml_conf, packet, test_packet, ref_packet)
            pkt_result.append({pod: tcp_pkt_result})
            md_result.append({pod: tcp_md_result})
            if tcp_pkt_result != "Pass" or tcp_md_result != "Pass":
                all_pass = False

    return {"pkt": pkt_result, "md": md_result}, all_pass


def check_http_pkt(recv_packet: dict, test_packet: list, ref_packet: list):
    msg = ""

    parts = recv_packet["host"].split(":")
    # pkt.host
    if not check_list_field(parts[0], test_packet, ref_packet, "pkt.host", True, "."):
        msg += "Fail: pkt.host not match\t "
    # pkt.port
    if len(parts) == 2 and not check_ordinary_field(
            parts[1], test_packet, ref_packet, "pkt.port"
    ):
        msg += "Fail: pkt.port not match\t "
    # pkt.http.uri
    if not check_list_field(
            recv_packet[":uri"], test_packet, ref_packet, "pkt.http.uri", False, "/"
    ):
        msg += "Fail: pkt.http.uri not match\t "
    # do not check pkt.http.scheme, because scheme could be header name
    # if not check_ordinary_field("", test_packet, ref_packet, "pkt.http.scheme"):
    #     msg += "Fail: pkt.http.scheme not match\t "
    # pkt.http.method
    if not check_ordinary_field(
            recv_packet[":method"], test_packet, ref_packet, "pkt.http.method"
    ):
        msg += "Fail: pkt.http.method not match\n "

    # pkt.http.header
    res = False
    for packet in ref_packet:
        res = True
        for key in list(packet):
            if packet[key] == "NONE":
                if recv_packet.keys().__contains__(key[16:]):
                    res = False
                    break
            else:
                if key.startswith("pkt.http.header"):
                    if not recv_packet.keys().__contains__(
                            key[16:]
                    ) or not check_ordinary_field(
                        recv_packet[key[16:]], test_packet, ref_packet, key
                    ):
                        res = False
                        break
        if res:
            break
    if not res:
        msg += "Fail: pkt.http.header not match\t "

    return "Pass" if msg == "" else msg


def check_http_md(yaml_conf, packet: dict, test_packet: list, ref_packet: list):
    msg = ""
    pod = packet["pod"]

    resources = yaml.safe_load_all(open(yaml_conf, "r"))
    for resource in resources:
        if resource["kind"] == "Pod" and resource["metadata"]["name"] == pod:
            pod_resource = resource
            break

    md_host_match = check_list_field(
        pod + (".default.svc.cluster.local" if not pod.__contains__(".") else ""),
        test_packet,
        ref_packet,
        "md.host",
        True,
        ".",
    )
    if not md_host_match:
        msg += "Fail: md.host not match\t "

    md_dstlabel_match = False
    for packet in ref_packet:
        md_dstlabel_match = True
        for key in list(packet):
            if key.startswith("md.dstlabel"):
                if (
                        packet[key] != "NONE"
                        and pod_resource["metadata"]["labels"][key[12:]] != packet[key]
                ):
                    md_dstlabel_match = False
                    break
        if md_dstlabel_match:
            break
    if not md_dstlabel_match:
        msg += "Fail: md.dstlabel not match\t "

    return "Pass" if msg == "" else msg


def check_tcp_pkt(packet, test_packet, ref_packet):
    return "Pass"


def check_tcp_md(yaml_conf, recv_packet, test_packet, ref_packet):
    pod = recv_packet["pod"]

    msg = ""

    resources = yaml.safe_load_all(open(yaml_conf, "r"))
    for resource in resources:
        if resource["kind"] == "Pod" and resource["metadata"]["name"] == pod:
            if not check_list_field(
                    pod + (".default.svc.cluster.local" if not pod.__contains__(".") else ""),
                    test_packet,
                    ref_packet,
                    "md.host",
                    True,
                    ".",
            ):
                msg += "Fail: md.host not match\t "
            if not check_ordinary_field(
                    encode_ip(recv_packet["dstIP"]), test_packet, ref_packet, "md.dstIP"
            ):
                msg += "Fail: md.dstIP not match\t "
            if not check_ordinary_field(
                    recv_packet["dstPort"], test_packet, ref_packet, "md.port"
            ):
                msg += "Fail: md.port not match\t "
            res = False
            for packet in ref_packet:
                res = True
                for key in list(packet):
                    if key.startswith("md.dstlabel"):
                        if (
                                packet[key] != "NONE"
                                and resource["metadata"]["labels"][key[12:]] != packet[key]
                        ):
                            res = False
                            break
                if res:
                    break
            if not res:
                msg += "Fail: md.dstlabel not match\t "
            return "Pass" if msg == "" else msg
    return "Fail: not find pod\t"


def check_list_field(
        target: str, test_packet, ref_packet, sym_name, reverse: bool, delimiter
):
    if target.startswith(delimiter):
        target = target.split(delimiter)[1:]
    else:
        target = target.split(delimiter)
    if target.__contains__(""):
        target.remove("")
    length = len(target)

    res = False
    for packet in ref_packet:
        if res:
            break
        if length != int(packet[sym_name + "_len"]):
            continue
        res = True
        for i in range(length):
            if reverse:
                name = sym_name + "_" + str(length - i - 1)
            else:
                name = sym_name + "_" + str(i)
            # symbolic not change
            if packet[name] == name:
                field = test_packet[0][name]
            else:
                field = packet[name]

            if field != "ANY" and field != target[i]:
                res = False
                break
    return res


def check_ordinary_field(target: str, test_packet, ref_packet, sym_name):
    res = False
    for packet in ref_packet:
        if res:
            break
        if list(test_packet[0]).__contains__(packet[sym_name]):
            field = test_packet[0][packet[sym_name]]
        else:
            field = packet[sym_name]
        if field != "ANY" and field != str(target):
            continue
        res = True
    return res


def load_actual_packets(path: str):
    """
    :return: {stage -> { idx -> [packet1, packet2] }}
    stage should be int
    """
    staged_actual_packets = {}
    for file in os.listdir(path):
        if file.endswith("recv.json"):
            pod = file.split("/")[-1][:-10]
            packets = json.load(open(path + "/" + file, "r"))

            for all_pkt in packets.values():
                for pkt in all_pkt:
                    idx = int(pkt["idx"])
                    stage = int(pkt["stage"])
                    pkt["pod"] = pod

                    if stage in staged_actual_packets:
                        if idx in staged_actual_packets[stage]:
                            staged_actual_packets[stage][idx].append(pkt)
                        else:
                            staged_actual_packets[stage][idx] = [pkt]
                    else:
                        staged_actual_packets[stage] = {idx: [pkt]}
    return staged_actual_packets


def load_model_packets(root_path: str, stage_num: int):
    """
    :return: {stage -> (<case> { idx -> [packet1, packet2] }, <ref>)}
    stage should be int
    """
    result = {}
    # for static test
    if stage_num <= 1:
        case_path = root_path + "/case-" + root_path.split("/")[-1] + ".json"
        ref_path = root_path + "/ref-" + root_path.split("/")[-1] + ".json"
        if not os.path.exists(case_path) or not os.path.exists(ref_path):
            return None
        case = load_packets(case_path)
        ref = load_packets(ref_path)
        if case is None or ref is None:
            return None
        result[0] = (case, ref)
        return result
    for stage in range(0, stage_num):
        stage_path = root_path + "/stage-" + str(stage)
        case_path = stage_path + "/case-stage-" + str(stage) + ".json"
        ref_path = stage_path + "/ref-stage-" + str(stage) + ".json"
        if not os.path.exists(case_path) or not os.path.exists(ref_path):
            return None
        case = load_packets(case_path)
        ref = load_packets(ref_path)
        if case is None or ref is None:
            return None
        result[stage] = (case, ref)
    return result


def load_packets(path: str):
    """
    :param path: path of packet json file
    :return: { idx -> [packet1, packet2] }
    """
    raw = json.load(open(path, "r"))
    result = {}
    for pkt in raw:
        idx = pkt["test_metadata"]["index"]
        if idx in result:
            result[idx].append(pkt)
        else:
            result[idx] = [pkt]
    return result


def get_test_packet(idx, test_packets: list):
    for pkt in test_packets:
        if int(pkt["test_metadata"]["index"]) == idx:
            return [pkt]
    return []


def get_ref_packets(idx, ref_packets: list):
    result = []
    for pkt in ref_packets:
        if int(pkt["test_metadata"]["index"]) == idx:
            result.append(pkt)
    return result


def compare_pkts(yaml_conf, actual_packets: dict, test_packets: dict, ref_packets: dict):
    result = {}
    all_pass = True
    for idx in test_packets.keys():
        if idx not in actual_packets:  # packet not exist
            if test_packets[idx][0]["md.type"] in ("HTTP", "TCP"):
                result[idx] = "Fail: not received\t"
                all_pass = False
            continue

        # test_packet, ref_packet, actual_packet are all list
        test_packet = get_test_packet(idx, test_packets[idx])  # only one
        ref_packet = get_ref_packets(idx, ref_packets[idx])  # may be more than one
        actual_packet = actual_packets[idx]  # may be more than one
        result[idx], pass_result = check_pkt(yaml_conf, actual_packet, test_packet, ref_packet)
        if not pass_result:
            all_pass = False
    return result, all_pass


def main(args):
    if len(args) < 2:
        print("Usage: python3 checker.py <case root path> <stage number>")
        exit(1)
    case_root_path = args[0]
    stage_number = int(args[1])
    # testcase_path = args[0]
    # case_index = args[1]

    stable_actual_packets = load_actual_packets(case_root_path + "/kind-istio-stable")
    dev_actual_packets = load_actual_packets(case_root_path + "/kind-istio-dev")
    model_packets = load_model_packets(case_root_path, stage_number)

    all_stage_pass = True
    for stage in range(0, stage_number):
        if model_packets[stage] is None:
            print("Fail: model packets not found")
            exit(1)
        case = model_packets[stage][0]
        ref = model_packets[stage][1]
        stable = stable_actual_packets[stage]
        dev = dev_actual_packets[stage]
        stage_yaml = case_root_path + "/stage-" + str(stage) + ".yaml"
        if not os.path.exists(stage_yaml):
            stage_yaml = case_root_path + "/" + case_root_path.split("/")[-1] + ".yaml"

        stable_result, stable_all_pass = compare_pkts(stage_yaml, stable, case, ref)
        dev_result, dev_all_pass = compare_pkts(stage_yaml, dev, case, ref)
        json.dump(
            stable_result,
            open(case_root_path + "/stage-" + str(stage) + "-check-stable.json", "w"),
            indent=4,
        )
        json.dump(
            dev_result,
            open(case_root_path + "/stage-" + str(stage) + "-check-dev.json", "w"),
            indent=4,
        )
        if stable_all_pass and dev_all_pass:
            print("Pass: stage-" + str(stage))
            with open(case_root_path + "/stage-" + str(stage) + "-PASS.txt", "w") as f:
                f.write("Pass")
        else:
            print("Fail: stage-" + str(stage))
            with open(case_root_path + "/stage-" + str(stage) + "-FAIL.txt", "w") as f:
                f.write("Fail")
            all_stage_pass = False
    if all_stage_pass:
        print("Pass: all stages")
        with open(case_root_path + "/Test-PASS.txt", "w") as f:
            f.write("Pass")
    else:
        print("Fail: some stages failed")
        with open(case_root_path + "/Test-FAIL.txt", "w") as f:
            f.write("Fail")


if __name__ == "__main__":
    main(sys.argv[1:])
