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
 * Provides JDK version to be used for the build, which should be already configured in 'Global Tool Configuration' in Jenkins.
 *
 * @return returns the jdk version to be used for the build.
 */
String getJDKVersion() {
    return "jdk9-latest"
}

/**
 * Provides maven version to be used for the build, which should be already configured in 'Global Tool Configuration' in Jenkins.
 *
 * @return returns the maven version to be used for the build.
 */
String getMavenVersion() {
    return "apache-maven-latest"
}

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
 * Checks out the specified branch from git repo
 *
 * @param branch Branch to be checked out
 * @credentialsId credentialsId Id of stored login credentials
 * @url url of the repository
 *
 */
void checkOutRepoWithCredentials(String branch, String credentialsId, String url) {
    stage('Checkout') {
        echo "Check out branch: [$branch] from repository [$url] with provided credentials"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "$branch"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [[$class: 'WipeWorkspace']],
                  userRemoteConfigs                : [[credentialsId: "$credentialsId",
                                                       name         : 'origin',
                                                       refspec      : '+refs/heads/*:refs/remotes/origin/*',
                                                       url          : "$url"]]])
    }
}

/**
 * Build with maven (with maven and jdk versions defined in {@link #getMavenVersion()} and {@link #getJDKVersion()}.
 *
 */
void build() {
    stage('Build') {
        withMaven(maven: getMavenVersion(), jdk: getJDKVersion(), options: [jacocoPublisher(disabled: true)]) {
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

/**
 * Archive build artifacts.
 *
 * @param fileNamePattern Pattern to use filenames filtering
 *
 */
void archiveArtifacts(String fileNamePattern) {
    stage('Archive Artifacts') {
        step([$class: 'ArtifactArchiver', artifacts: "$fileNamePattern"])
    }
}

/**
 * Publish java documentation.
 *
 */
void publishJavaDoc() {
    stage('Publish Java Documentation') {
        step([$class: 'JavadocArchiver', javadocDir: 'target/site/apidocs'])
    }
}

/**
 * Publish java documentation.
 *
 * @param javaDocDir Javadoc directory in the workspace.
 * @param keepAll  If set to true, retain javadoc for all the successful builds.
 */
void publishJavaDoc(String javadocDir, boolean keepAll){
    stage('Publish Java Documentation') {
        step([$class: 'JavadocArchiver', javadocDir: "$javadocDir", keepAll: keepAll])
    }
}

return this
