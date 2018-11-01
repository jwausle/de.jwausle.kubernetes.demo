# Kubernetes demo

The repo contain a minimal HTTP endpoint to demo kubernetes HA fuctionality with `liveness` and `readiness` probes.

## Getting started

Compile and build the docker image `jwausle/de.jwausle.kubernetes`.

```
$ git clone ...
$ cd de.jwausle.kubernetes
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
$ eval $(minikube docker-env) # to use minikube docker daemon for `docker ...` commands

# optional
$ source <(kubectl completion zsh) # enable kubectl tab completion     

# make dashboard login available
$ minikube addons enable dashboard
$ kubectl create clusterrolebinding add-on-cluster-admin \
 --clusterrole=cluster-admin \
 --serviceaccount=kube-system:default

$ minikube dashboard   # open the kuberntes dasboard in browser

# metric-server
$ minikube addons enable metrics-server
```

You can find more infos here: https://kubernetes.io/docs/tutorials/hello-minikube/

# useful commands

```
$ kubectl config view --minify # show current context

$ kubectl run demo --image=jwausle/de.jwausle.kubernetes:latest  --port=8080 --image-pull-policy=Never
$ kubectl expose deployment demo --type=LoadBalancer
$ minikube service demo --url

```

## Links

Kubernetes service types (NodePort/LoadBalancer/Ingress)
https://medium.com/google-cloud/kubernetes-nodeport-vs-loadbalancer-vs-ingress-when-should-i-use-what-922f010849e0
