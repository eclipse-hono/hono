#!/usr/bin/env groovy

/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

/**
 * Jenkins pipeline script for Continuous integration build of Hono master.
 * Checks GitHub repository every minute for changes.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '',
                  artifactNumToKeepStr: '5',
                  daysToKeepStr: '',
                  numToKeepStr: '30')),
                disableConcurrentBuilds(),
                pipelineTriggers([pollSCM('* * * * *')])])
    try {
        utils.checkOutHonoRepoMaster()
        utils.build()
        utils.aggregateJunitResults()
        currentBuild.result = 'SUCCESS'
    } catch (err) {
        currentBuild.result = 'FAILURE'
        echo "Error: ${err}"
    }
    finally {
        echo "Build status: ${currentBuild.result}"
        utils.notifyBuildStatus()
    }
}
