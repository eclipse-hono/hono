/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
  - name: jnlp
    volumeMounts:
    - mountPath: "/home/jenkins"
      name: "jenkins-home"
    - mountPath: "/home/jenkins/.ssh"
      name: "volume-known-hosts"
    - mountPath: "/home/jenkins/.m2/toolchains.xml"
      name: "m2-dir"
      readOnly: true
      subPath: "toolchains.xml"
    - mountPath: "/home/jenkins/.mavenrc"
      name: "m2-dir"
      readOnly: true
      subPath: ".mavenrc"
    - mountPath: "/home/jenkins/.m2/repository"
      name: "m2-repo"
      readOnly: false
    - mountPath: "/home/jenkins/.m2/settings.xml"
      name: "m2-secret-dir"
      readOnly: true
      subPath: "settings.xml"
    - mountPath: "/home/jenkins/.m2/settings-security.xml"
      name: "m2-secret-dir"
      readOnly: true
      subPath: "settings-security.xml"
    - mountPath: "/opt/tools"
      name: "tools"
      readOnly: false
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
  - name: "m2-dir"
    configMap:
      name: "m2-dir"
  - name: "m2-secret-dir"
    secret:
      secretName: "m2-secret-dir"
  - name: "volume-known-hosts"
    configMap:
      name: "known-hosts"
  - name: "tools"
    persistentVolumeClaim:
      claimName: "tools-claim-jiro-hono"
      readOnly: false
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

  tools {
    maven 'apache-maven-3.8.4'
    jdk 'temurin-jdk17-latest'
  }

  stages {
    stage("Local environment") {
      steps {
        sh 'ls -al /home/jenkins'
        sh 'ls -al /opt/tools'
        sh 'mvn -v'
      }
    }

    stage('Prepare workspace') {
      steps {
        echo "Checking out tag [refs/tags/${params.RELEASE_VERSION}]"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "refs/tags/${params.RELEASE_VERSION}"]],
                  doGenerateSubmoduleConfigurations: false,
                  userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse/hono.git']]])
      }
    }

    stage('Build and deploy to Maven Central') {
      steps {
          withCredentials([file(credentialsId: 'secret-subkeys.asc', variable: 'KEYRING')]) {
            sh 'gpg --version'
            sh 'gpg --batch --import-options restore --import "${KEYRING}"'
            sh 'gpg --list-secret-keys'
          }
          sh "mvn deploy \
                -DskipTests=true -DnoDocker -DcreateGPGSignature=true -DcreateJavadoc=true -DenableEclipseJarSigner=true \
                -am -pl '\
                  :hono-adapter-amqp,\
                  :hono-adapter-coap,\
                  :hono-adapter-http,\
                  :hono-adapter-lora,\
                  :hono-adapter-mqtt,\
                  :hono-adapter-sigfox,\
                  :hono-cli,\
                  :hono-example,\
                  :hono-service-auth,\
                  :hono-service-command-router,\
                  :hono-service-device-registry-jdbc,\
                  :hono-service-device-registry-mongodb,\
                  '"
      }
    }
  }
}
