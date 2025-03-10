# set up environment
setup: registry sender receiver



DEV_TAG=1.24-alpha.a4af094644efe8d2f044f65610b2f3445aac234d
STABLE_TAG=1.22.3

env: registry create-cluster sender receiver
	kubectl apply -f setup/registry.yaml
	docker network connect "kind" "registry" > /dev/null &
	kubectl config use-context kind-istio-stable
	kubectl label namespace default istio-injection=enabled

istio: registry create-cluster download-istio
	@echo "Setting up Kubernetes..."
	kubectl apply -f setup/registry.yaml
	docker network connect "kind" "registry" > /dev/null &
	@echo "Setting up Istio..."
	kubectl config use-context kind-istio-stable
	./istioctl/istioctl-stable install --set tag=$(STABLE_TAG) --set profile=demo --set values.global.logging.level=debug -y
	kubectl label namespace default istio-injection=enabled
	# install gateway
	kubectl apply -f setup/metallb-native.yaml
	kubectl wait --namespace metallb-system --for=condition=ready pod --selector=app=metallb --timeout=90s
	kubectl apply -f setup/gw-network-istio-stable.yaml

	@echo "Setting up Istio dev..."
	kubectl config use-context kind-istio-dev
	./istioctl/istioctl-dev install --set tag=$(DEV_TAG) --set profile=demo --set values.global.logging.level=debug --set hub=gcr.dockerproxy.com/istio-testing -y
	kubectl label namespace default istio-injection=enabled
	# install gateway
	kubectl apply -f setup/metallb-native.yaml
	kubectl wait --namespace metallb-system --for=condition=ready pod --selector=app=metallb --timeout=90s
	kubectl apply -f setup/gw-network-istio-dev.yaml


linkerd: registry create-cluster
	# though this is named linekrd, it will setup both linkerd and istio for gateway-api
	@echo "Setting up Kubernetes..."
	kubectl apply -f setup/registry.yaml
	docker network connect "kind" "registry" > /dev/null &
	@echo "Setting up Linkerd..."
	kubectl config use-context kind-istio-stable
	linkerd check --pre
	linkerd install --crds | kubectl apply -f -
	linkerd install | kubectl apply -f -
	linkerd check
	kubectl get ns default -o yaml | linkerd inject - | kubectl apply -f -
	kubectl apply -f setup/metallb-native.yaml
	kubectl wait --namespace metallb-system --for=condition=ready pod --selector=app=metallb --timeout=90s
	kubectl apply -f setup/gw-network-linkerd.yaml

	@echo "Setting up Istio(Gateway API)..."
	kubectl config use-context kind-istio-dev
	./istioctl/istioctl-dev install -f setup/demo-profile-no-gateways.yaml --set values.global.logging.level=debug -y
	kubectl label namespace default istio-injection=enabled
	kubectl apply -f setup/metallb-native.yaml
	kubectl wait --namespace metallb-system --for=condition=ready pod --selector=app=metallb --timeout=90s
	kubectl apply -f setup/gw-network-istio-dev.yaml


gateway-api: create-cluster
	@echo "Setting up Gateway Environment..."
	kubectl apply --context kind-istio-stable -f setup/experimental-install.yaml
	kubectl apply --context kind-istio-stable -f setup/contour-gateway-provisioner.yaml
	kubectl apply --context kind-istio-stable -f setup/contour-gateway-class.yaml

	kubectl apply --context kind-istio-dev -f setup/experimental-install.yaml
	kubectl apply --context kind-istio-dev -f setup/contour-gateway-provisioner.yaml
	kubectl apply --context kind-istio-dev -f setup/contour-gateway-class.yaml


download-istio:
	mkdir -p istioctl
	@echo "Downloading Istio stable"
	wget https://github.com/istio/istio/releases/download/$(STABLE_TAG)/istioctl-$(STABLE_TAG)-osx-arm64.tar.gz -O istioctl/istioctl-$(STABLE_TAG)-osx-arm64.tar.gz
	tar -xzf istioctl/istioctl-$(STABLE_TAG)-osx-arm64.tar.gz -C istioctl
	mv istioctl/istioctl istioctl/istioctl-stable
	@echo "Downloading Istio dev"
	wget https://github.com/istio/istio/releases/download/$(DEV_TAG)/istioctl-$(DEV_TAG)-osx-arm64.tar.gz -O istioctl/istioctl-$(DEV_TAG)-osx-arm64.tar.gz
	tar -xzf istioctl/istioctl-$(DEV_TAG)-osx-arm64.tar.gz -C istioctl
	mv istioctl/istioctl istioctl/istioctl-dev


create-cluster: create-stable create-dev

create-stable:
	@if ! kind get clusters | grep -q istio-stable; then \
		bash -c 'https_proxy="" http_proxy="" all_proxy="" kind create cluster --config setup/kind-istio-stable.yaml'; \
	else \
		echo "Cluster 'istio-stable' already exists, not creating."; \
	fi
	docker cp driver/sender istio-stable-control-plane:/app; \
	docker exec istio-stable-control-plane bash -c "sed -i s@/deb.debian.org/@/mirrors.tuna.tsinghua.edu.cn/@g /etc/apt/sources.list.d/debian.sources"; \
	docker exec istio-stable-control-plane bash -c "apt-get update && apt-get install -y python3 python3-pip && pip3 install -r /app/sender/requirements.txt --break-system-packages > /dev/null"; \

create-dev:
	@if ! kind get clusters | grep -q istio-dev; then \
		bash -c 'https_proxy="" http_proxy="" all_proxy="" kind create cluster --config setup/kind-istio-dev.yaml'; \
	else \
		echo "Cluster 'istio-dev' already exists, not creating."; \
	fi
	docker cp driver/sender istio-dev-control-plane:/app; \
	docker exec istio-dev-control-plane bash -c "sed -i s@/deb.debian.org/@/mirrors.tuna.tsinghua.edu.cn/@g /etc/apt/sources.list.d/debian.sources"; \
	docker exec istio-dev-control-plane bash -c "apt-get update && apt-get install -y python3 python3-pip && pip3 install -r /app/sender/requirements.txt --break-system-packages > /dev/null"; \



registry:
	@echo "Setting up registry..."
	docker start registry || docker run -d -p 127.0.0.1:15000:5000 --restart=always --network bridge --name registry registry


receiver: registry driver/receiver/Dockerfile driver/receiver/receiver.py driver/receiver/requirements.txt
	@echo "Building receiver..."
	cd driver/receiver && docker build -t receiver:latest .
	docker tag receiver 127.0.0.1:15000/receiver:latest
	docker push 127.0.0.1:15000/receiver:latest


sender-build: registry driver/sender/Dockerfile driver/sender/sender.py driver/sender/requirements.txt
	@echo "Building sender..."
	cd driver/sender && docker build -t sender:latest .
	docker tag sender 127.0.0.1:15000/sender:latest
	docker push 127.0.0.1:15000/sender:latest

sender: sender-build create-cluster
	docker cp driver/sender istio-stable-control-plane:/app
	docker cp driver/sender istio-dev-control-plane:/app


istio-stop:
	@echo "Stopping k8s resources..."
	@kubectl delete virtualservices --all --context kind-istio-stable
	@kubectl delete destinationrules --all --context kind-istio-stable
	@kubectl delete gateways --all --context kind-istio-stable
	@kubectl delete serviceentries --all --context kind-istio-stable
	@kubectl delete deployments --all --force --context kind-istio-stable
	@kubectl delete pods --all --force --context kind-istio-stable
	@kubectl delete services --all --force --context kind-istio-stable

	@kubectl delete virtualservices --all --context kind-istio-dev
	@kubectl delete destinationrules --all --context kind-istio-dev
	@kubectl delete gateways --all --context kind-istio-dev
	@kubectl delete serviceentries --all --context kind-istio-dev
	@kubectl delete deployments --all --force --context kind-istio-dev
	@kubectl delete pods --all --force --context kind-istio-dev
	@kubectl delete services --all --force --context kind-istio-dev
	@echo "Done"


linkerd-stop:
	@echo "Stopping k8s resources..."
	@kubectl delete gateway --all --context kind-istio-stable
	@kubectl delete httproute --all --context kind-istio-stable
	@kubectl delete pods --all --force --context kind-istio-stable
	@kubectl delete services --all --force --context kind-istio-stable

	@kubectl delete gateway --all --context kind-istio-dev
	@kubectl delete httproute --all --context kind-istio-dev
	@kubectl delete pods --all --force --context kind-istio-dev
	@kubectl delete services --all --force --context kind-istio-dev
	@echo "Done"


clean: istio-stop
	@echo "Cleaning test cases..."
	@find testcase -mindepth 1 -maxdepth 1 -type d -exec rm -rf {} +
	@rm testconf/*.yaml
	@echo "Done"


purge:
	@echo "Clearing..."
	@kind delete cluster --name istio-stable
	@kind delete cluster --name istio-dev
	@docker stop registry
	@docker rm -v registry
	@echo "Done"
#	@rm -rf istioctl

.PHONY: run

