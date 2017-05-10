#!/bin/sh

BASE_DIR=`dirname $0`
ABS=`cd $BASE_DIR && pwd`
# Assumes that COMMIT, DOCKER_USER and DOCKER_PASS to be set
REPO=eclipsehono/qpid-dispatch
TAG=${qpid-dispatch.version}

echo "building Qpid Dispatch Router $TAG image ..."
docker build -t $REPO:$TAG $ABS

if [ -n "$DOCKER_USER" ]
  then
      echo "pushing Qpid Dispatch Router $TAG image to Docker Hub ..."
      docker login -u $DOCKER_USER -p $DOCKER_PASS || exit 1
      docker push $REPO:$TAG || exit 1
fi

