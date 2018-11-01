# /bin/bash (create|replace --force)
CLI_USAGE="$0 create|replace|delete [--[no-]service|--[no-]replica|--[no-]scale][--verbose][--dry-run]"
NAME=demo
PORT=8080
REPLICAS=2
REPLICAS_MAX=4

if [ "$1" == "" ]; then
   echo "Parameter expected - Usage: ${CLI_USAGE}"
   exit -1
fi
function help() {
  echo "Usage: ${CLI_USAGE}"
  echo ""
  echo "Commands:"
  echo "- create        to create ${NAME} Service,ReplicationController and/or HorizontalPodAutoscaler if not exist"
  echo "- replace       to replace ${NAME} kubernetes Service,ReplicationController and/or HorizontalPodAutoscaler if exist"
  echo "- delete        to replace ${NAME} kubernetes Service,ReplicationController and/or HorizontalPodAutoscaler if exist"
  echo "- list          show installed ${NAME} kubernetes Service,ReplicationController and/or HorizontalPodAutoscaler"
  echo ""
  echo "Options:"
  echo "--service       create, replace xor delete ${NAME} Service (default:false)"
  echo "--replica       create, replace xor delete ${NAME} ReplicationController (default:true)"
  echo "--no-replica    do not create, replace xor delete ${NAME} ReplicationController (default:false)"
  echo "--scale         create, replace xor delete ${NAME} HorizontalPodAutoscaler (default:true)"
  echo "--no-scale      do not create, replace xor delete ${NAME} HorizontalPodAutoscaler (default:false)"
  echo "--dry-run       force dry run without any resource manipulation (default:false)"
  echo "--verbose       force very verbose mode (default:false)"
  echo ""
  echo "Sample:"
  echo " $0 create              - create ReplicationController and HorizontalPodAutoscaler"
  echo " $0 create --service    - create ReplicationController and HorizontalPodAutoscaler and Service"
  echo " $0 create --no-scale   - create ReplicationController only"
  echo " $0 replace             - replace ReplicationController and HorizontalPodAutoscaler"
  echo " $0 delete --no-replica - delete HorizontalPodAutoscaler only"
  echo " $0 delete --dry-run    - delete ReplicationController and HorizontalPodAutoscaler in dry-run mode"
}
function list() {
  echo "# Service"
  kubectl get service "${NAME}"
  echo "# HorizontalPodAutoscaler"
  kubectl get horizontalpodautoscalers.autoscaling "${NAME}"
  echo "# ReplicationController"
  kubectl get replicationcontrollers "${NAME}"
  echo "# Pods"
  kubectl get pods
}

# Parse CLI parameter
[[ $* == *--help*       ]] && help       && exit 0
CMD="$1"
[ "${CMD}" == "list"     ] && list       && exit 0
CMD=`         [ "$1" == "replace"      ] && echo replace --force || echo ${CMD}`
WITH_SERVICE=`[[ $* == *--service*    ]] && echo true            || echo false`
WITH_REPLICA=`[[ $* == *--no-replica* ]] && echo false           || echo true`
WITH_REPLICA=`[[ $* == *--replica*    ]] && echo true            || echo ${WITH_REPLICA}`
WITH_SCALE=`  [[ $* == *--no-scale*   ]] && echo false           || echo true`
WITH_SCALE=`  [[ $* == *--scale*      ]] && echo true            || echo ${WITH_SCALE}`
VERBOSE=`     [[ $* == *--verbose*    ]] && echo -v 10           || echo -v 0`
DRY_RUN=`     [[ $* == *--dry-run*    ]] && echo --dry-run=true  || echo --dry-run=false`
DRY_RUN_=`[ "${CMD}" == "create"       ] && echo ${DRY_RUN}      || echo ""`

# Run command
echo "Do '${CMD}' 'service:${WITH_SERVICE}' 'replica(${REPLICAS}):${WITH_REPLICA}' and 'scale[${REPLICAS}-${REPLICAS_MAX}]:${WITH_SCALE}'"
#################################
# service
#################################
if [ "${WITH_SERVICE}${DRY_RUN}-${CMD}" == "true--dry-run=true-replace --force" ]; then
  echo "Replace Service (dry run)"
elif [ "${WITH_SERVICE}${DRY_RUN}-${CMD}" == "true--dry-run=true-delete" ]; then
  echo "Delete Service (dry run)"
elif [ "${WITH_SERVICE}" == "true" ]; then
  cat <<EOF | kubectl ${CMD} ${VERBOSE} ${DRY_RUN_} -f -
  apiVersion: v1
  kind: Service
  metadata:
    name: ${NAME}
    labels:
      app: ${NAME}
  spec:
    type: NodePort
    ports:
    - port: ${PORT}
      protocol: TCP
      name: http
    selector:
      app: ${NAME}
EOF
fi

#################################
# replication controller
#################################
if [ "${WITH_REPLICA}${DRY_RUN}-${CMD}" == "true--dry-run=true-replace --force" ]; then
  echo "Replace ReplicationController (dry run)"
elif [ "${WITH_REPLICA}${DRY_RUN}-${CMD}" == "true--dry-run=true-delete" ]; then
  echo "Delete ReplicationController (dry run)"
elif [ "${WITH_REPLICA}" == "true" ]; then
  cat <<EOF | kubectl ${CMD} ${VERBOSE} ${DRY_RUN_} -f -
  apiVersion: v1
  kind: ReplicationController
  metadata:
    name: ${NAME}
  spec:
    replicas: ${REPLICAS}
    template:
      metadata:
        labels:
          app: ${NAME}
      spec:
        containers:
        - name: ${NAME}
          image: jwausle/de.jwausle.kubernetes:demo
          imagePullPolicy: Never
          resources:
            requests:
              memory: "64Mi"
              cpu: "250m"
            limits:
              memory: "128Mi"
              cpu: "500m"
          ports:
          - containerPort: ${PORT}
          livenessProbe:
            httpGet:
              path: /liveness
              port: ${PORT}
            initialDelaySeconds: 30
            timeoutSeconds: 1
            periodSeconds: 3
            successThreshold: 1
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /readiness
              port: ${PORT}
            initialDelaySeconds: 30
            timeoutSeconds: 1
            periodSeconds: 3
            successThreshold: 1
            failureThreshold: 3
EOF
fi

#################################
# autoscale
#################################
if [ "${WITH_SCALE}${DRY_RUN}-${CMD}" == "true--dry-run=true-replace --force" ]; then
  echo "Replace HorizontalPodAutoscaler (dry run)"
elif [ "${WITH_SCALE}${DRY_RUN}-${CMD}" == "true--dry-run=true-delete" ]; then
  echo "Delete HorizontalPodAutoscaler (dry run)"
elif [ "${WITH_SCALE}" == "true" ]; then
  cat <<EOF | kubectl ${CMD} ${VERBOSE} ${DRY_RUN_} -f -
  apiVersion: v1
  items:
  - apiVersion: autoscaling/v1
    kind: HorizontalPodAutoscaler
    metadata:
      name: ${NAME}
    spec:
      maxReplicas: ${REPLICAS_MAX}
      minReplicas: ${REPLICAS}
      scaleTargetRef:
        apiVersion: v1
        kind: ReplicationController
        name: ${NAME}
      targetCPUUtilizationPercentage: 20
  kind: List
EOF
fi
