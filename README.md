# Kubernetes demo

The repo contain a minimal HTTP endpoint to demo kubernetes HA fucntionality with `autoscale`, `liveness` and `readiness` probes.

## Require

 > java      >= 9

 > mvn       >= 3.5

 > minikube  >= 0.28.2
 
 > kubectl   >= 1.10
 
 > dockerAPI >= 1.38
 
 The above versions was installed during test.
 

## Getting started

Compile and build the docker image `jwausle/de.jwausle.kubernetes`.

```
$ git clone https://github.com/jwausle/de.jwausle.kubernetes.demo
$ cd de.jwausle.kubernetes.demo
$ mvn clean package docker:build
```

### Getting started with docker

> require installed `docker`

```
$ docker run --rm -p 8080:8080 jwausle/de.jwausle.kubernetes

$ curl http://localhost:8080
$ curl http://localhost:8080/readiness # check READY|UNREADY
$ curl http://localhost:8080/unready   # set unready for 3 /readiness checks. Afterwards come back to READY.
$ curl http://localhost:8080/kill      # kill the java process inside the contianer

# Restart container after kill
$ docker run --rm -p 8080:8080 jwausle/de.jwausle.kubernetes
```

### Getting started with kubernetes

> require installed `minikube`, `kubectl`

```
$ minikube start --vm-driver=hyperkit \
     --extra-config=apiserver.authorization-mode=RBAC

# optional
$ eval $(minikube docker-env)           # to use minikube docker daemon for `docker ...` commands

# optional
$ source <(kubectl completion zsh)      # enable kubectl tab completion 


$ minikube addons enable dashboard      # make dashboard login available
$ minikube addons enable metrics-server # enabel metric-server
$ kubectl create clusterrolebinding add-on-cluster-admin \
 --clusterrole=cluster-admin \
 --serviceaccount=kube-system:default 
$ minikube dashboard                    # open the kuberntes dasboard in browser
```

You can find more infos here: https://kubernetes.io/docs/tutorials/hello-minikube/

# Useful commands

```
$ kubectl config view --minify # show current context

$ kubectl run demo --image=jwausle/de.jwausle.kubernetes:latest  --port=8080 --image-pull-policy=Never
$ kubectl expose deployment demo --type=LoadBalancer
$ minikube service demo --url

```

```
# get apiserver access token
kubectl get secret $(kubectl get serviceaccount default -o json | jq -r '.secrets[].name') -o yaml | grep "token:" | awk {'print $2'} |  base64 -d
```

# Links

Kubernetes service types [NodePort/LoadBalancer/Ingress](https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0)

[Minikube](https://kubernetes.io/docs/setup/minikube/) installation  

[Kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) installation

Real cluster installation (+3 nodes) - [Itemis intern](https://gitlab-intern.itemis.de/vagrant-examples/kubernetes-cluster)

