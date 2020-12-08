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
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')), parameters([
            string(defaultValue: '',
                    description: "The branch to build and release from.\nExamples:\n refs/heads/master\nrefs/heads/1.4.x",
                    name: 'BRANCH',
                    trim: true),
            string(defaultValue: '',
                    description: "The version identifier (tag name) to use for the artifacts built and released by this job. \nExamples:\n1.0.0-M6\n1.3.0-RC1\n1.4.4",
                    name: 'RELEASE_VERSION',
                    trim: true),
            string(defaultValue: '',
                    description: "The version identifier to use during development of the next version.\nExamples:\n2.0.0-SNAPSHOT\n1.6.0-SNAPSHOT",
                    name: 'NEXT_VERSION',
                    trim: true),
            booleanParam(defaultValue: true,
                    description: "Deploy documentation for this release to web site.\nDisable for milestone release.",
                    name: 'DEPLOY_DOCUMENTATION'),
            booleanParam(defaultValue: true,
                    description: "Set the documentation for this release as the new stable version.\nDisable for milestone release.",
                    name: 'STABLE_DOCUMENTATION')])])
    try {
        checkOut()
        def utils = load 'jenkins/Hono-PipelineUtils.groovy'
        setReleaseVersionAndBuild(utils)
        setVersionForDocumentation()
        commitAndTag()
        setNextVersion(utils)
        commitAndPush()
        copyArtifacts()
        currentBuild.result = 'SUCCESS'
    } catch (err) {
        currentBuild.result = 'FAILURE'
        echo "Error: ${err}"
    } finally {
        echo "Build status: ${currentBuild.result}"
        step([$class                  : 'Mailer',
              notifyEveryUnstableBuild: true,
              recipients              : 'hono-dev@eclipse.org',
              sendToIndividuals       : false])
    }
}

/**
 * Checks out the specified branch from git repo
 *
 */
def checkOut() {
    stage('Checkout') {
        echo "Check out branch: ${params.BRANCH}"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "${params.BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [[$class: 'WipeWorkspace'],
                                                      [$class: 'LocalBranch']],
                  userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse/hono.git']]])
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
            git config user.email "hono-bot@eclipse.org"
            git config user.name "hono-bot"
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
      sshagent(credentials: [ 'github-bot-ssh' ]) {
        sh ''' 
            git add pom.xml \\*/pom.xml
            git commit -m "Bump version to ${NEXT_VERSION}"
            git push --all ssh://git@github.com/eclipse/hono.git && git push --tags ssh://git@github.com/eclipse/hono.git
           '''
        }
    }
}

/**
 * Copy the artifacts so that the binaries are available for download
 *
 */
def copyArtifacts() {
    stage("Copy Artifacts") {
      sshagent(credentials: [ 'projects-storage.eclipse.org-bot-ssh' ]) {
        sh ''' 
            chmod +r cli/target/hono-cli-${RELEASE_VERSION}-exec.jar
            scp cli/target/hono-cli-${RELEASE_VERSION}-exec.jar genie.hono@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/hono/
           '''
      }
    }

}
