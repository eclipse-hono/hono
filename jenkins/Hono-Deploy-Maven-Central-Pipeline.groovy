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
 * Jenkins pipeline script that checks out ${RELEASE_VERSION}, builds all artifacts to deploy,
 * signs them and creates PGP signatures for them and deploys artifacts to Maven Central's staging repo.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')), parameters([
            string(defaultValue: '',
                    description: "The tag to build and deploy. \nExamples:\n1.0.0-M6\n1.0.0-RC1\n2.1.0",
                    name: 'RELEASE_VERSION',
                    trim: true),
            credentials(credentialType: 'com.cloudbees.plugins.credentials.common.StandardCredentials',
                    defaultValue: '',
                    description: 'The credentials to use during checkout from git',
                    name: 'CREDENTIALS_ID',
                    required: true),
            string(defaultValue: '/opt/public/hipp/homes/genie.hono/.m2/settings-deploy-ossrh.xml',
                    description: 'The path to settings.xml to be used during maven build.',
                    name: 'MAVEN_SETTINGS_FILE',
                    trim: true)
    ])])
    try {
        utils.checkOutRepoWithCredentials("${params.RELEASE_VERSION}", "${params.CREDENTIALS_ID}", "ssh://github.com/eclipse/hono.git")
        buildAndDeploy()
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
 * Build and deploy with maven.
 *
 */
def buildAndDeploy() {
    stage('Build and deploy to maven central') {
        withMaven(maven: 'apache-maven-latest',
                jdk: 'jdk1.8.0-latest',
                mavenLocalRepo: '.repository',
                mavenSettingsFilePath: "${params.MAVEN_SETTINGS_FILE}",
                options: [jacocoPublisher(disabled: true), artifactsPublisher(disabled: true)]) {
            sh "mvn deploy -X -pl :hono-service-auth,:hono-service-messaging,:hono-service-device-registry,:hono-adapter-http-vertx,:hono-adapter-mqtt-vertx,:hono-adapter-kura,:hono-adapter-amqp-vertx,:hono-example,:hono-cli -am -DskipTests=true -DcreateGPGSignature=true -DcreateJavadoc=true -DenableEclipseJarSigner=true"
        }
    }
}
