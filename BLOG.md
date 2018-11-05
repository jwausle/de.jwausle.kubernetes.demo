# Wer nicht skaliert verliert

'_Wer nicht skaliert verliert_' oder '_Wem`s gelingt gewinnt_' sind Leitsätze von [Kubernetes](https://kubernetes.io/docs/concepts/). Die Cluster-Container Technologie aus dem Hause Google setzt dabei konsequent auf Hochverfügbarkeit von Netzwerkservices.


>  'Netzwerkservice - ist ein protokollbasierter, adressierbarer Dienst im Netz.'

Kubernetes ist dabei die ideale Ergänzung zu [Docker](https://www.docker.com/). Wo Docker aufwendig wird, fängt Kubernetes an. Es kümmert sich neben der Hochverfügbarkeit von Services, um den Lebenszyklus von Containern. Das betrifft alle Dinge rund um die Konfiguration, das Monitoring und den Betrieb von Containern auf unterschiedlichen Docker-Hosts. Das oberste Ziel ist einfache Konfigurierbarkeit und maximale Automatisierung von hochverfügbaren Netzwerkservices.

>  'Hochverfügbarkeit - Sero-Downtime bzw. 24/7 Verfügbarkeit eines Netzwerkservice'

Die Idee von Kubernetes ist - die Einführung einer Serviceschicht, durch die Entkopplung der Container- von seiner Serviceadresse. Somit kann jede Serviceanfrage an einen Service gestellt und von mehreren Containern beantwortet werden. Jeder Service ist somit horizontal skallierbar.

> <img src="doc/kubernetes-service.png" alt="drawing" style="width:400px;"/>

Zentrale Kubernetes Instrumente sind dabei:

1. Automatisierte [Liveness- und Readyness-Probe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/) von Pods
2. Automatisierter up- und down-scale von Services - durch Resourcenmonitoring

> 'Pod - Kleinste Kubernetes Resource. Synonym für einen Container'

## 1. Automatisierte Liveness- und Readyness-Probe von Pods

Jeder Pod wird von Kubernetes überwacht, um ihn entweder bei Bedarf neu zu starten oder ihm Zeit zur Selbstheilung zu geben, falls der Pod ausgelastet ist. Der Neustart wird durch die Liveness-Probe ausgelöst und die Selbstheilung durch die Readyness-Probe. Beide Probes können entweder HTTP Endpunkte oder Containerkommandos sein. Sie werden von Kubernetes in einem definierbaren Takt abgefragt. Im wiederholten Fehlerfall passiert folgendes:

* Liveness Fehlerfall - Kubernetes started den Pod neu.
* Readyness Fehlerfall - Kubernetes routet keinen weiteren Traffic zu diesem Pod.

Beide Probes können Bestandteil eines Pods sein.

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: demo
  name: demo-pod
spec:
  containers:
  - image: jwausle/de.jwausle.kubernetes:demo
    imagePullPolicy: Never
    livenessProbe:
      failureThreshold: 3
      httpGet:
        path: /liveness
        port: 8080
        scheme: HTTP
      initialDelaySeconds: 30
      periodSeconds: 3
      successThreshold: 1
      timeoutSeconds: 1
    name: demo
    ports:
    - containerPort: 8080
      protocol: TCP
    readinessProbe:
      failureThreshold: 3
      httpGet:
        path: /readiness
        port: 8080
        scheme: HTTP
      initialDelaySeconds: 30
      periodSeconds: 3
      successThreshold: 1
      timeoutSeconds: 1
```

Das `initialeDelaySeconds: 30` definiert die initiale Wartezeit beim Podstart, wo Kubernetes keine Probeanfrage stellt. Danach stellt Kubernetes aller 3 Sekunden (`periodSeconds: 3`) eine Probeanfrage. Sollte 3 mal hintereinander eine Fehler passieren (`failureThreshold: 3`), dann setzt Kubernetes die entsprechende Probe in den Fehlermodi. Die Probes werden weiterhin angefragt, um auf etwaige erfolgreiche Anfragen zu reagieren und den Fehlermodi wieder zu verlassen (`successThreshold: 1`). Um nicht jeden Pod einzeln zu konfigurieren, können beide Probes im [ReplicationController](https://kubernetes.io/docs/concepts/workloads/controllers/replicationcontroller/), [ReplicaSet](https://kubernetes.io/docs/concepts/workloads/controllers/replicaset/) oder [Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) konfiguriert werden.

Zusammen mit "Automatisierten up-scale von Services" kann Kubernetes nahe zu Sero-Downtime für jeden Service gewährleisten.

## 2. Automatisierter up- und down-scale von Services - durch Resourcen monitoring

Kubernetes überwacht periodisch die Ressourcen aller Pods wie z.B. vCPU- und Speicher-Auslastung. Für jede Resource kann ein Schwellwert (bezüglich aller Pods) definiert werden, ab welchem Kubernetes Pods automatsich nach startet beziehungsweise herunterfährt. Dieser Schwellwert wird im [HorizontalPodAutoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) konfiguriert.

```
apiVersion: v1
items:
- apiVersion: autoscaling/v1
  kind: HorizontalPodAutoscaler
  metadata:
    name: ${DEPLOYMENT_CONFIG_NAME}
  spec:
    maxReplicas: 10
    minReplicas: 2
    scaleTargetRef:
      apiVersion: v1
      kind: ReplicationController
      name: ${DEPLOYMENT_CONFIG_NAME}
    targetCPUUtilizationPercentage: 20
kind: List
```

Die Anzahl der gestarteten Pods wird durch die folgende Formel bestimmt.

>  'desiredReplicas = currentReplicas * ( currentMetricValue / desiredMetricValue )'

_Bsp.1 - 2 Pods sollten gestarted werden_

- currentReplicas=2
- currentMetricValue=40%
- desiredMetricValue=20%
- desiredReplicas=4= 2 * ( 40 / 20)

_Bsp.2 - 2 Pods sollten gestoppt werden_

- currentReplicas=4
- currentMetricValue=20%
- desiredMetricValue=40%
- desiredReplicas=2= 4 * ( 20 / 40)

# Wann Kubernetes?

Microservices eignen sich Ideal für den Einsatz von Kubernetes. Statuslos und/oder clusterfähig sind dabei Haupteigenschaften eines Microservices. Ein sehr gutes Bespiel sind 'Build Slaves' für Jenkins oder Gitlab. Eher nicht geeignet sind zustandsabhängige Services, wie klassische Datenbanken. Diese sollten besser außerhalb von Kubernetes betrieben werden. Das [Overlay Netzwerk](https://github.com/coreos/flannel#flannel) von Kubernetes bietet auch dafür eine nathlose Integrationsmöglichkeit.

# Fazit

Kubernetes ist die ideale Ergänzung zu Docker im Produktiveinsatz. Wo Docker aufwendig wird, setzt Kubernetes an. Es kümmert sich neben der Hochverfügbarkeit und das Routing von Services, hauptsächlich um den Betrieb von Containern über einen längeren Zeitraum. Der automtiserte Neustart eines Pods ist dabei zentrales Instrument zur Skalierung.

Wie alles hat auch Kubernetes eine Kehrseite. Die Konfiguration passiert fast ausschliesslich über YAML/JSON Dateien, für die der Toolsupport meist mit dem Syntax-Highlighting aufhört. Das schafft ein erhöhtes Konfigurationsrisiko bei starkt vernetzte Mikroservice-Infrastrukturen.
