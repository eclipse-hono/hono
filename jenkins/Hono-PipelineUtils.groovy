#!/usr/bin/env groovy

/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0checkOutGitRepoMaster
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

/**
 * A collection of utility methods to build pipelines
 *
 */

/**
 * Checks out the specified branch from hono github repo
 *
 * @param branch Branch to be checked out
 */
void checkOutHonoRepo(String branch) {
    stage('Checkout') {
        echo "Check out branch: $branch"
        git branch: "$branch", url: "https://github.com/eclipse/hono.git"
    }
}

/**
 * Checks out the master branch from hono github repo
 *
 */
void checkOutHonoRepoMaster() {
    checkOutHonoRepo("master")
}

/**
 * Build with maven (with jdk1.8.0-latest and apache-maven-latest as configured in 'Global Tool Configuration' in Jenkins).
 *
 */
void build() {
    stage('Build') {
        withMaven(maven: 'apache-maven-latest', jdk: 'jdk1.8.0-latest') {
            sh 'mvn -B clean install'
        }
    }
}

/**
 * Aggregate junit test results.
 *
 */
void aggregateJunitResults() {
    stage('Aggregate Junit Test Results') {
        junit '**/surefire-reports/*.xml'
    }
}

/**
 * Notify build status via email to 'hono-dev@eclipse.org'.
 *
 */
void notifyBuildStatus() {
    try {
        step([$class                  : 'Mailer',
              notifyEveryUnstableBuild: true,
              recipients              : 'hono-dev@eclipse.org',
              sendToIndividuals       : false])
    } catch (error) {
        echo "Error notifying build status via Email"
        echo error.getMessage()
        throw error
    }
}

/**
 * Capture code coverage reports using Jacoco jenkins plugin.
 *
 */
void captureCodeCoverageReport() {
    stage('Capture Code Coverage Report') {
        step([$class       : 'JacocoPublisher',
              execPattern  : '**/**.exec',
              classPattern : '**/classes',
              sourcePattern: '**/src/main/java'
        ])
    }
}

return this
