#!/usr/bin/env groovy

/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
 * Builds Hono's web site using Hugo every night (between 3 and 4 AM) and publishes to Eclipse.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')),
                pipelineTriggers([cron('TZ=Europe/Berlin \n # every night between 3 and 4 AM \n H 3 * * *')])])
    try {
        deleteDir()
        build()
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

/**
 * Build stages for Hono's web site pipeline using Hugo
 *
 */
def build() {

    stage('Cloning Hono repository') {
        echo "Cloning Hono repository..."
        sh ''' 
           echo "cloning Hono web site repository..."
           git clone ssh://git@github.com/eclipse/hono.git $WORKSPACE/hono
           '''
    }

    stage('Cloning Hono web site repository') {
        echo "cloning Hono web site repository..."
        sh ''' 
           echo "cloning Hono web site repository..."
           git clone /gitroot/www.eclipse.org/hono.git $WORKSPACE/hono-web-site
           '''
    }

    stage('Cloning Hugo Material Docs theme') {
        echo "cloning Hugo Material Docs theme..."
        sh '''
            #!/bin/sh
            echo "cloning Hugo Material Docs theme..."
            git clone https://github.com/digitalcraftsman/hugo-material-docs.git $WORKSPACE/hono/site/themes/hugo-material-docs
            cd $WORKSPACE/hono/site/themes/hugo-material-docs
            git checkout 194c497216c8389e02e9719381168a668a0ffb05
           '''
    }

    stage('Building web site using Hugo') {
        echo "scrubbing web site target directory..."
        sh '''
            #!/bin/sh
            echo "scrubbing web site target directory..."
            rm -rf $WORKSPACE/hono-web-site/*
            cd $WORKSPACE/hono/site
            echo "building web site using Hugo `/shared/common/hugo/latest/hugo version`"
            /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site
         '''
    }

    stage('Commit and push') {
        sh '''
            #!/bin/sh
            cd $WORKSPACE/hono-web-site && 
            git add -A && 
            git commit -m "latest web site changes" && 
            git push origin HEAD:refs/heads/master
            echo "Done" 
         '''
    }
}
