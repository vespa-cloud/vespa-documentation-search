#!/bin/bash
set -x

VESPA_CONTAINER_NAME=vespa-documentation-search

podman stop $VESPA_CONTAINER_NAME

podman rm $VESPA_CONTAINER_NAME

podman run --detach \
    --name $VESPA_CONTAINER_NAME \
    --hostname $VESPA_CONTAINER_NAME \
    --publish 8080:8080 \
    --publish 19071:19071 \
    vespaengine/vespa:8.400.15

mvn package

vespa deploy --wait 300 target/application

vespa status --wait 300
