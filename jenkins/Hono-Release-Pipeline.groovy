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
 * Jenkins pipeline script for Hono release.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')), parameters([
            string(defaultValue: '',
                    description: "The branch to build and release from.\nExamples: \n master\n1.0.x\n1.x",
                    name: 'BRANCH',
                    trim: true),
            string(defaultValue: '',
                    description: "The version identifier to use for the artifacts built and released by this job. \nExamples:\n1.0.0-M6\n1.0.0-RC1\n2.1.0",
                    name: 'RELEASE_VERSION',
                    trim: true),
            string(defaultValue: '',
                    description: "The version identifier to use during development of the next version.\nExamples:\n2.0.0-SNAPSHOT\n1.1.0-SNAPSHOT",
                    name: 'NEXT_VERSION',
                    trim: true),
            booleanParam(defaultValue: true,
                    description: "Deploy documentation for this release to web site.\nDisable for milestone release.",
                    name: 'DEPLOY_DOCUMENTATION'),
            booleanParam(defaultValue: true,
                    description: "Set the documentation for this release as the new stable version.\nDisable for milestone release.",
                    name: 'STABLE_DOCUMENTATION'),
            credentials(credentialType: 'com.cloudbees.plugins.credentials.common.StandardCredentials',
                    defaultValue: '',
                    description: 'The credentials to use during checkout from git',
                    name: 'CREDENTIALS_ID',
                    required: true)])])
    try {
        checkOut()
        setReleaseVersionAndBuild(utils)
        setVersionForDocumentation()
        commitAndTag()
        setNextVersion(utils)
        commitAndPush()
        copyArtifacts()
        utils.publishJavaDoc('target/site/apidocs', true)
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
 * Checks out the specified branch from git repo
 *
 */
def checkOut() {
    stage('Checkout') {
        echo "Check out branch: origin/${params.BRANCH}"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "origin/${params.BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [[$class: 'WipeWorkspace'],
                                                      [$class: 'LocalBranch']],
                  userRemoteConfigs                : [[credentialsId: "${params.CREDENTIALS_ID}", url: 'ssh://git@github.com/eclipse/hono.git']]])
    }
}

/**
 * Set version to RELEASE_VERSION and build using maven
 *
 * @param utils An instance of the Hono-PipelineUtils containing utility methods to build pipelines.
 */
def setReleaseVersionAndBuild(def utils) {
    stage('Build') {
        withMaven(maven: utils.getMavenVersion(), jdk: utils.getJDKVersion(), options: [artifactsPublisher(disabled: true)]) {
            sh "mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${RELEASE_VERSION}"
            sh 'mvn clean install javadoc:aggregate -Dmaven.test.failure.ignore=false -DenableEclipseJarSigner=true -DsnapshotDependencyAllowed=false -Ddocker.skip.build=true'
        }
    }
}

/**
 * Store version as supported version, if enabled:
 * - Add to list of supported versions (used by the pipeline that builds the web site)
 * - Set as the new stable version, if enabled
 */
def setVersionForDocumentation() {
    stage("Add version for documentation") {
        if (params.DEPLOY_DOCUMENTATION ==~ /(?i)(T|TRUE)/) {
            echo "add to supported versions"
            sh ''' 
               MAJOR="${RELEASE_VERSION%%.*}" # before first dot
               rest="${RELEASE_VERSION#*.}" # after first dot
               MINOR="${rest%%.*}"  # before first dot of rest
               echo "${MAJOR};${MINOR};${RELEASE_VERSION}" >> site/documentation/versions_supported.csv
               git add site/documentation/versions_supported.csv
               '''
            if (params.STABLE_DOCUMENTATION ==~ /(?i)(T|TRUE)/) {
                echo "set as stable version"
                sh ''' 
                   echo "${RELEASE_VERSION}" > site/documentation/tag_stable.txt
                   git add site/documentation/tag_stable.txt
                   '''
            }
        } else {
            echo "skip release of documentation"
        }
    }
}

/**
 * Commit and tag to RELEASE_VERSION
 *
 */
def commitAndTag() {
    stage("Commit and tag release version") {
        sh ''' 
            git add pom.xml \\*/pom.xml
            git commit -m "Release ${RELEASE_VERSION}"
            git tag ${RELEASE_VERSION}
           '''
    }

}

/**
 * Set version to NEXT_VERSION using maven
 *
 * @param utils An instance of the Hono-PipelineUtils containing utility methods to build pipelines.
 */
def setNextVersion(def utils) {
    stage("Set next version") {
        withMaven(maven: utils.getMavenVersion(), jdk: utils.getJDKVersion(), options: [artifactsPublisher(disabled: true)]) {
            sh "mvn versions:set -DallowSnapshots=true -DgenerateBackupPoms=false -DnewVersion=${NEXT_VERSION}"
        }
    }
}

/**
 * Commit and push to the Hono repo
 *
 */
def commitAndPush() {
    stage("Commit and push") {
        sh ''' 
            git add pom.xml \\*/pom.xml
            git commit -m "Bump version to ${NEXT_VERSION}"
            git push --all ssh://git@github.com/eclipse/hono.git && git push --tags ssh://git@github.com/eclipse/hono.git
           '''
    }
}

/**
 * Copy the artifacts so that the binaries are available for download
 *
 */
def copyArtifacts() {
    stage("Copy Artifacts") {
        sh ''' 
            chmod +r deploy/target/eclipse-hono-${RELEASE_VERSION}-*.tar.gz
            cp deploy/target/eclipse-hono-${RELEASE_VERSION}-*.tar.gz /home/data/httpd/download.eclipse.org/hono/
            chmod +r cli/target/hono-cli-${RELEASE_VERSION}-exec.jar
            cp cli/target/hono-cli-${RELEASE_VERSION}-exec.jar /home/data/httpd/download.eclipse.org/hono/
           '''
    }

}
