# Set readiness back to ready
CONTAINER_ID=$1
if [ "${CONTAINER_ID}" == "" ]; then
  docker ps  --format "{{.ID}} {{.Names}} {{.Image}}" | grep demo_demo
  read -e -p "Which CONTAINER_ID ? " VALUE
  CONTAINER_ID=`echo ${VALUE}`
fi

read -e -p "GET 'readiness/ready' or 'stress/stop' ? " VALUE
URL_PATH=`echo ${VALUE}`

docker exec ${CONTAINER_ID} curl localhost:8080/${URL_PATH}
