/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

pipeline {
  agent {
    kubernetes {
      label 'my-agent-pod'
      yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: "maven:3.8.4-openjdk-11"
    tty: true
    command:
    - cat
    volumeMounts:
    - mountPath: /home/jenkins
      name: "jenkins-home"
    - mountPath: /home/jenkins/.ssh
      name: "volume-known-hosts"
    - name: "settings-xml"
      mountPath: /home/jenkins/.m2/settings.xml
      subPath: settings.xml
      readOnly: true
    - name: "settings-security-xml"
      mountPath: /home/jenkins/.m2/settings-security.xml
      subPath: settings-security.xml
      readOnly: true
    - name: "m2-repo"
      mountPath: /home/jenkins/.m2/repository
    - name: "toolchains-xml"
      mountPath: /home/jenkins/.m2/toolchains.xml
      subPath: toolchains.xml
      readOnly: true
    env:
    - name: "HOME"
      value: "/home/jenkins"
    resources:
      limits:
        memory: "6Gi"
        cpu: "2"
      requests:
        memory: "6Gi"
        cpu: "2"
  volumes:
  - name: "jenkins-home"
    emptyDir: {}
  - name: "m2-repo"
    emptyDir: {}
  - configMap:
      name: known-hosts
    name: "volume-known-hosts"
  - name: "settings-xml"
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings.xml
        path: settings.xml
  - name: "settings-security-xml"
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings-security.xml
        path: settings-security.xml
  - name: "toolchains-xml"
    configMap:
      name: m2-dir
      items:
      - key: toolchains.xml
        path: toolchains.xml
"""
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '3'))
    disableConcurrentBuilds()
    timeout(time: 45, unit: 'MINUTES')
  }

  parameters {
    string(
      name: 'BRANCH',
      description: "The branch to retrieve the pipeline from.\nExamples:\n refs/heads/master\nrefs/heads/1.4.x",
      defaultValue: 'refs/heads/master',
      trim: true)
    string(
      name: 'RELEASE_VERSION',
      description: "The tag to build and deploy.\nExamples:\n1.0.0-M6\n1.0.0-RC1\n2.1.0",
      defaultValue: '',
      trim: true)
  }

  stages {

    stage('Check out project') {
      steps {
        container('maven') {
          echo "Checking out tag [refs/tags/${params.RELEASE_VERSION}]"
          checkout([$class                           : 'GitSCM',
                    branches                         : [[name: "refs/tags/${params.RELEASE_VERSION}"]],
                    doGenerateSubmoduleConfigurations: false,
                    userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse/hono.git']]])
        }
      }
    }

    stage('Build and deploy to Eclipse Repo') {
      steps {
        container('maven') {
          sh "mvn deploy -pl :hono-service-auth,:hono-service-auth-quarkus,:hono-service-device-registry-file,:hono-service-device-registry-jdbc,:hono-service-device-registry-mongodb,:hono-service-device-registry-mongodb-quarkus,:hono-service-command-router,:hono-service-command-router-quarkus,:hono-service-device-connection,:hono-adapter-http-vertx,:hono-adapter-http-vertx-quarkus,:hono-adapter-mqtt-vertx,:hono-adapter-mqtt-vertx-quarkus,:hono-adapter-kura,:hono-adapter-amqp-vertx,:hono-adapter-amqp-vertx-quarkus,:hono-adapter-lora-vertx,:hono-adapter-lora-vertx-quarkus,:hono-adapter-sigfox-vertx,:hono-adapter-coap-vertx,:hono-adapter-coap-vertx-quarkus,:hono-example,:hono-cli -am -DskipTests=true -DnoDocker -DcreateJavadoc=true -DenableEclipseJarSigner=true -DskipStaging=true"
        }
      }
    }
  }
}
