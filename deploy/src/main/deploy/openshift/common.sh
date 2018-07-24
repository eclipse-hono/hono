#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#*******************************************************************************

function waiting_containers_ready {
    ADDR_SPACE=$1
    pods_id=$(oc get pods -n ${ADDR_SPACE} -l app=enmasse | awk 'NR >1 {print $1}')
    for pod_id in ${pods_id}
    do
        ready=$(oc get -o json pod -n ${ADDR_SPACE}  $pod_id -o jsonpath={.status.containerStatuses[0].ready})
        if [ ${ready} == "false" ]
        then
            return 1
        fi
    done
    return 0
}

function wait_for_enmasse {
    EXPECTED_PODS=$1
    ADDRESS_SPACE=$2
    TIMEOUT=600
    NOW=$(date +%s)
    END=$(($NOW + $TIMEOUT))
    while true
    do
        NOW=$(date +%s)
        if [ $NOW -gt $END ]; then
            echo -e "\nTimed out waiting for nodes to come up!"
            pods=`oc get pods -n ${ADDRESS_SPACE}`
            echo "PODS: $pods"
            exit 1
        fi
        num_running=`oc get pods -n ${ADDRESS_SPACE} -l app=enmasse | grep -v deploy | grep -c Running`
        if [ "$num_running" -ge "$EXPECTED_PODS" ]; then
            if ! waiting_containers_ready ${ADDRESS_SPACE}; then
                echo "All pods are up but all containers are not ready yet"
                tput cuu1 && tput civis
            else
                tput el && tput cnorm
                echo "EnMasse is ready!"
                break
            fi
        else
            echo "$num_running/$EXPECTED_PODS up"
            tput cuu1 && tput civis
        fi
        sleep 5
    done
}