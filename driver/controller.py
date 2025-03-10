import json
import os
import sys
import time
from typing import Dict, List, Tuple

import yaml
from kubernetes import client, config
from kubernetes.stream import stream
import docker


class sender_agent:
    # static agent cnt
    agent_cnt = 0

    def __init__(self, proxy, test_id, cluster, src_label=None, src_ns="default", stage="0", target_ip="1.1.1.1"):
        self.test_id = test_id
        self.cluster = cluster
        # gateway or sidecar
        self.proxy = proxy
        self.stage = stage
        self.target_ip = target_ip

        # todo: src label/ns
        self.src_label = src_label
        self.src_ns = src_ns

        self.pkt_list = []
        self.agent_idx = sender_agent.agent_cnt
        sender_agent.agent_cnt += 1

    def add_pkt(self, pkt):
        self.pkt_list.append(pkt)

    def dump(self) -> Tuple[Dict[str, str], List[Dict]]:
        md = {}
        md["md.proxy"] = self.proxy
        md["md.src_label"] = "None" if self.src_label is None else self.src_label
        md["md.src_ns"] = self.src_ns
        md["md.agent_idx"] = str(self.agent_idx)
        md["md.test_id"] = self.test_id
        md["md.cluster"] = self.cluster
        return md, self.pkt_list


def load_json(json_file) -> List[Dict]:
    with open(json_file) as f:
        data = json.load(f)
    return data


def dispatch_case(pkts: list, test_id, cluster, stage, gtw_ips) -> List[sender_agent]:
    agents = []
    sc_target_ip = "back.default.svc.cluster.local" if get_sm_type(cluster) == "gtw" else "1.1.1.1"
    sc_agent = sender_agent(proxy="sidecar", test_id=test_id, cluster=cluster, stage=stage, target_ip=sc_target_ip)
    gw_agents = {}
    gateways = gtw_ips.keys()
    for gtw_name in gateways:
        gw_agents[gtw_name] = sender_agent(
            proxy=gtw_name,
            test_id=test_id,
            cluster=cluster,
            stage=stage,
            target_ip=gtw_ips[gtw_name],
        )
    agents.append(sc_agent)
    agents.extend(gw_agents.values())

    for pkt in pkts:
        assert "md.proxy" in pkt, "Packet must have md.proxy"
        proxy = pkt["md.proxy"].lower()
        gateway = pkt["md.gateway"].lower()
        if proxy == "any":
            proxy = "sidecar"

        if proxy == "gateway":
            gw_agents[gateway].add_pkt(pkt)
        elif proxy == "sidecar":
            sc_agent.add_pkt(pkt)
        else:
            assert False, "Unsupported proxy type"
    return agents


def dump_agents(agents: List[sender_agent], output_path):
    for agent in agents:
        md, pkt_list = agent.dump()

        agent_idx = agent.agent_idx
        # pretty print
        agent_md_json = json.dumps(md, indent=4, sort_keys=True)
        agent_pkts_json = json.dumps(pkt_list, indent=4, sort_keys=True)
        with open(output_path + "/" + str(agent_idx) + ".md.json", "w") as f:
            f.write(agent_md_json)
            f.close()
        with open(output_path + "/" + str(agent_idx) + ".pkts.json", "w") as f:
            f.write(agent_pkts_json)
            f.close()


# sender log is stored in the same directory as the json config file
def generate_config(json_file, stage, gtw_ips: dict, cluster) -> List[sender_agent]:
    # output_path is parent dir of json_file
    output_path = json_file.split("/")[:-1]
    test_id = output_path[-1]
    if output_path[-2] != "testcase":
        test_id = output_path[-2] + "/" + test_id
    output_path = "/".join(output_path)

    pkts = load_json(json_file)
    agents = dispatch_case(pkts, test_id, cluster, stage, gtw_ips)
    os.makedirs(output_path + "/" + cluster, exist_ok=True)
    dump_agents(agents, output_path)
    return agents


def launch_sc_agent(agent: sender_agent, cluster: str):
    # set k8s context to cluster
    config.load_kube_config(context=cluster)
    pod = client.V1Pod(
        api_version="v1",
        kind="Pod",
        metadata=client.V1ObjectMeta(
            name="sender-" + str(agent.agent_idx), namespace="default"
        ),
        spec=client.V1PodSpec(
            containers=[
                client.V1Container(
                    name="sender-" + str(agent.agent_idx),
                    image="localhost:15000/sender",
                    image_pull_policy="Always",
                    command=["python3"],
                    args=[
                        "sender.py",
                        "config/"
                        + str(agent.test_id)
                        + "/"
                        + str(agent.agent_idx)
                        + ".pkts.json",
                        agent.cluster,
                        agent.stage,
                        agent.target_ip,
                    ],
                    volume_mounts=[
                        client.V1VolumeMount(
                            name="config-volume", mount_path="/app/config"
                        )
                    ],
                )
            ],
            restart_policy="OnFailure",
            volumes=[
                client.V1Volume(
                    name="config-volume",
                    host_path=client.V1HostPathVolumeSource(path="/app/config"),
                )
            ],
        ),
    )
    api_instance = client.CoreV1Api()
    api_instance.create_namespaced_pod(namespace="default", body=pod)


def launch_gw_agent(agent: sender_agent, cluster: str):
    container_name = cluster.removeprefix("kind-") + "-control-plane"
    work_dir = "/app/config/" + agent.test_id + "/"
    command = (
            "python3 /app/sender/sender.py "
            + work_dir
            + str(agent.agent_idx)
            + ".pkts.json "
            + cluster
            + " "
            + agent.stage
            + " "
            + agent.target_ip
            + " > "
            + work_dir
            + agent.cluster
            + "_gw_sender.log 2>&1"
    )
    try:
        docker_client = docker.from_env()
        container = docker_client.containers.get(container_name)
        container.exec_run(command, detach=True)
    except Exception as e:
        print("Failed to launch agent: " + str(agent.agent_idx))
        print(e)
        sys.exit(1)


def launch_agent(agent: sender_agent):
    if agent.proxy == "sidecar":
        launch_sc_agent(agent, agent.cluster)
        print("Info: Launched sidecar agent: " + str(agent.agent_idx))
    else:
        launch_gw_agent(agent, agent.cluster)
        print("Info: Launched gateway agent: " + str(agent.agent_idx) + " on gateway " + agent.proxy)


def get_ingress_port(config_path: str):
    with open(config_path, "r") as f:
        data = yaml.safe_load_all(f)
        res = set()
        for resource in data:
            if resource == None:
                continue
            if (
                    resource["apiVersion"].startswith("networking.istio.io")
                    and resource["kind"] == "Gateway"
            ):
                servers = resource["spec"]["servers"]
                for server in servers:
                    port = server["port"]["number"]
                    res.add(int(port))
            elif (
                    resource["apiVersion"].startswith("gateway.networking.k8s.io")
                    and resource["kind"] == "Gateway"
            ):
                listeners = resource["spec"]["listeners"]
                for listener in listeners:
                    port = listener["port"]
                    res.add(int(port))
    return list(res)


def kube_apply(yaml_path: str, cluster: str):
    os.system("kubectl --context " + cluster + " apply -f " + yaml_path + "> /dev/null")


def kube_delete(yaml_path: str, cluster: str):
    os.system("kubectl --context " + cluster + " delete -f " + yaml_path + " --force > /dev/null")


def kube_run_cmd(command: str, cluster: str):
    os.system("kubectl --context " + cluster + " " + command + "> /dev/null")


def open_istio_port(ports: List[int], cluster: str):
    # get service istio-ingressgateway
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    while True:
        svcs = api_instance.list_namespaced_service(namespace="istio-system")
        ready = False
        for svc in svcs.items:
            if svc.metadata.name == "istio-ingressgateway":
                ready = True
                break
        if ready:
            break
        else:
            time.sleep(1)
    svc = api_instance.read_namespaced_service("istio-ingressgateway", "istio-system")
    # get ports of the service
    for port in ports:
        port_exist = False
        for existing_port in svc.spec.ports:
            if existing_port.port == port:
                port_exist = True
                break
        if not port_exist:
            svc.spec.ports.append(
                client.V1ServicePort(
                    port=port,
                    target_port=port,
                    name="test-" + str(port),
                    protocol="TCP",
                )
            )
    api_instance.replace_namespaced_service("istio-ingressgateway", "istio-system", svc)


def open_k8s_gtw_port(gtws: List[str], ports: List[int], cluster: str):
    gtw_svcs = ["envoy-" + gtw for gtw in gtws]
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    svcs = api_instance.list_namespaced_service(namespace="default").items
    for svc in svcs:
        svc_name = svc.metadata.name
        if svc_name in gtw_svcs:
            new_svc = api_instance.read_namespaced_service(svc_name, "default")
            for port in ports:
                port_exist = False
                for existing_port in new_svc.spec.ports:
                    if existing_port.port == port:
                        port_exist = True
                        break
                if not port_exist:
                    new_svc.spec.ports.append(
                        client.V1ServicePort(
                            port=port,
                            target_port=port,
                            name="test-" + str(port),
                            protocol="TCP",
                        )
                    )
            api_instance.replace_namespaced_service(svc_name, "default", new_svc)


def get_istio_ingress_gw_address(config_path: str, cluster: str):
    config.load_kube_config(context=cluster)
    gtws = []
    with open(config_path, "r") as f:
        data = yaml.safe_load_all(f)
        for resource in data:
            if resource == None:
                continue
            if (
                    resource["apiVersion"].startswith("networking.istio.io")
                    and resource["kind"] == "Gateway"
            ):
                gw_name = resource["metadata"]["name"]
                gtws.append(gw_name)

    api_instance = client.CoreV1Api()
    while True:
        svcs = api_instance.list_namespaced_service(namespace="istio-system")
        ready = False
        for svc in svcs.items:
            if svc.metadata.name == "istio-ingressgateway":
                ready = True
                break
        if ready:
            break
        else:
            time.sleep(1)
    svc = api_instance.read_namespaced_service("istio-ingressgateway", "istio-system")
    # get external ip
    external_ip = svc.status.load_balancer.ingress[0].ip
    return {gtw: external_ip for gtw in gtws}


def get_k8s_gtw_address(config_path: str, cluster: str):
    """
    :return: {gtw -> external_ip}
    """
    k8s_gtws = []
    with open(config_path, "r") as f:
        data = yaml.safe_load_all(f)
        for resource in data:
            if resource == None:
                continue
            if (
                    resource["apiVersion"].startswith("gateway.networking.k8s.io")
                    and resource["kind"] == "Gateway"
            ):
                gw_name = resource["metadata"]["name"]
                k8s_gtws.append(gw_name)

    k8s_gtw_svcs = ["envoy-" + gw for gw in k8s_gtws]
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    while True:  # wait for all gateways to be created
        svcs = api_instance.list_namespaced_service(namespace="default")
        ready = True
        for gtw_svc in k8s_gtw_svcs:
            created = False
            for svc in svcs.items:
                if svc.metadata.name == gtw_svc:
                    created = True
                    break
            if not created:
                ready = False
                break
        if ready:
            break
        else:
            time.sleep(1)
    # get external ip of all k8s gateways
    gtw_ip = {}
    for gtw in k8s_gtws:
        svc = api_instance.read_namespaced_service("envoy-" + gtw, "default")

        external_ip = svc.status.load_balancer.ingress[0].ip
        gtw_ip[gtw] = external_ip
    return gtw_ip


def get_sm_type(cluster):
    # if istiod is running, it is istio
    # otherwise, it is gtw
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    pods = api_instance.list_namespaced_pod(namespace="istio-system")
    if len(pods.items) < 2:
        return "gtw"
    return "istio"


def prepare_gw(config_path: str, cluster: str):
    """
    :param config_path: config yaml path
    :param cluster: cluster of testbed
    :param kind: istio or gtw
    :return: {gateway_name -> external_ip}
    """
    kind = get_sm_type(cluster)
    assert kind == "istio" or kind == "gtw", "Only istio and k8s gtw are supported"

    # get all ports used in gateway
    ports = get_ingress_port(config_path)
    if kind == "istio":
        istio_gw_ip = get_istio_ingress_gw_address(config_path, cluster)
        open_istio_port(ports, cluster)
        return istio_gw_ip
    elif kind == "gtw":
        k8s_gw_ip = get_k8s_gtw_address(config_path, cluster)
        open_k8s_gtw_port(list(k8s_gw_ip.keys()), ports, cluster)
        return k8s_gw_ip
    else:
        assert False, "Unsupported kind"


def wait_pods_ready(cluster: str):
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    while True:
        pods = api_instance.list_namespaced_pod(namespace="default")
        ready = True
        for pod in pods.items:
            if pod.status.phase != "Running":
                ready = False
                break
        if ready:
            break
        else:
            time.sleep(1)


def wait_all_pods_deleted(cluster: str):
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()
    cnt = 0
    while True:
        pods = api_instance.list_namespaced_pod(namespace="default")
        if len(pods.items) == 0:
            break
        else:
            time.sleep(1)
        if cnt > 60:
            print("Failed to delete all pods")
            break
        cnt += 1


def prepare_agents(stable_k8s_yaml, dev_k8s_yaml, case_json, stage):
    stable_gw_address = prepare_gw(stable_k8s_yaml, "kind-istio-stable")
    dev_gw_address = prepare_gw(dev_k8s_yaml, "kind-istio-dev")
    assert stage is not None, "Stage must be specified"

    stable_agents = generate_config(case_json, stage=stage, gtw_ips=stable_gw_address, cluster="kind-istio-stable")
    dev_agents = generate_config(case_json, stage=stage, gtw_ips=dev_gw_address, cluster="kind-istio-dev")
    agents = stable_agents + dev_agents
    return agents


def wait_pod_deleted(cluster: str, pod_name: str):
    config.load_kube_config(context=cluster)
    api_instance = client.CoreV1Api()

    cnt = 0
    while True:
        pods = api_instance.list_namespaced_pod(namespace="default")
        deleted = True
        for pod in pods.items:
            if pod.metadata.name == pod_name:
                deleted = False
                break
        if deleted:
            return
        else:
            time.sleep(1)
            cnt += 1
            if cnt > 60:
                print("Failed to delete pod: " + pod_name)
                break


def main(args):
    assert (
            len(args) == 3 or len(args) == 4 or len(args) == 5
    ), ("Usage: python3 controller.py <k8s yaml for stable cluster> <k8s yaml for dev cluster> <case json> "
        "env|sender-start|sender-stop|stop <stage>")

    if len(args) == 3:
        stable_k8s_yaml = args[0]
        dev_k8s_yaml = args[1]
        operation = args[2]
        assert operation == "stop" or operation == "env", "Operation must be stop or env if case json is not provided"
    else:
        stable_k8s_yaml = args[0]
        dev_k8s_yaml = args[1]
        case_json = args[2]
        operation = args[3]

    stage = None
    if len(args) == 5:
        stage = args[4]

    if operation == "env":
        kube_apply(stable_k8s_yaml, "kind-istio-stable")
        kube_apply(dev_k8s_yaml, "kind-istio-dev")

        # wait until all pods are ready
        wait_pods_ready("kind-istio-dev")
        wait_pods_ready("kind-istio-stable")
    elif operation == "sender-start":
        # in this case, k8s_yaml is "/A/B/stage-0.yaml"
        agents = prepare_agents(stable_k8s_yaml, dev_k8s_yaml, case_json, stage)
        for agent in agents:
            launch_agent(agent)
    elif operation == "sender-stop":
        assert stage is not None, "Stage must be specified"
        agents = prepare_agents(stable_k8s_yaml, dev_k8s_yaml, case_json, stage)
        for agent in agents:
            if agent.proxy == "sidecar":
                kube_run_cmd("exec sender-" + str(agent.agent_idx) + " pkill python3", "kind-istio-stable")
                kube_run_cmd("delete pod sender-" + str(agent.agent_idx) + " --force", "kind-istio-stable")
                kube_run_cmd("exec sender-" + str(agent.agent_idx) + " pkill python3", "kind-istio-dev")
                kube_run_cmd("delete pod sender-" + str(agent.agent_idx) + " --force", "kind-istio-dev")
                wait_pod_deleted("kind-istio-stable", "sender-" + str(agent.agent_idx))
                wait_pod_deleted("kind-istio-dev", "sender-" + str(agent.agent_idx))
            elif agent.proxy == "gateway":
                os.system("docker exec istio-dev-control-plane pkill python3")
                os.system("docker exec istio-stable-control-plane pkill python3")
    elif operation == "stop":
        # # in this case, k8s_yaml is "/A/B/0/base.yaml", test_suffix is "base"
        # test_suffix = k8s_yaml.split("/")[-1].split(".")[0]
        # # test_path is "/A/B/0"
        # test_path = k8s_yaml[: k8s_yaml.rfind("/")]
        # stable_yaml = test_path + "/kind-istio-stable-" + test_suffix + ".yaml"
        # dev_yaml = test_path + "/kind-istio-dev-" + test_suffix + ".yaml"

        kube_delete(stable_k8s_yaml, "kind-istio-stable")
        kube_delete(dev_k8s_yaml, "kind-istio-dev")

        # stop all pods
        kube_run_cmd("delete pods --all --force", "kind-istio-dev")
        kube_run_cmd("delete pods --all --force", "kind-istio-stable")
        wait_all_pods_deleted("kind-istio-dev")
        wait_all_pods_deleted("kind-istio-stable")
    else:
        print("Unsupported command")


if __name__ == "__main__":
    main(sys.argv[1:])
