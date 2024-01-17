/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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
      yaml '''
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: "jnlp"
            volumeMounts:
            - mountPath: "/home/jenkins/.ssh"
              name: "volume-known-hosts"
            env:
            - name: "HOME"
              value: "/home/jenkins"
          - name: "hono-builder"
            image: "eclipse/hono-builder:2.5.0"
            imagePullPolicy: "Always"
            tty: true
            command:
            - cat
            volumeMounts:
            - mountPath: "/home/jenkins"
              name: "jenkins-home"
            - mountPath: "/home/jenkins/.ssh"
              name: "volume-known-hosts"
            - mountPath: "/home/jenkins/.m2/settings.xml"
              name: "settings-xml"
              subPath: "settings.xml"
              readOnly: true
            - mountPath: "/home/jenkins/.m2/settings-security.xml"
              name: "settings-security-xml"
              subPath: "settings-security.xml"
              readOnly: true
            - mountPath: "/home/jenkins/.m2/repository"
              name: "m2-repo"
            - mountPath: "/home/jenkins/.m2/toolchains.xml"
              name: "toolchains-xml"
              subPath: "toolchains.xml"
              readOnly: true
            env:
            - name: "HOME"
              value: "/home/jenkins"
            resources:
              limits:
                memory: "8Gi"
                cpu: "2"
              requests:
                memory: "8Gi"
                cpu: "2"
          volumes:
          - name: "jenkins-home"
            emptyDir: {}
          - name: "m2-repo"
            emptyDir: {}
          - name: "volume-known-hosts"
            configMap:
              name: "known-hosts"
          - name: "settings-xml"
            secret:
              secretName: "m2-secret-dir"
              items:
              - key: settings.xml
                path: settings.xml
          - name: "settings-security-xml"
            secret:
              secretName: "m2-secret-dir"
              items:
              - key: settings-security.xml
                path: settings-security.xml
          - name: "toolchains-xml"
            configMap:
              name: "m2-dir"
              items:
              - key: toolchains.xml
                path: toolchains.xml
        '''
      defaultContainer 'hono-builder'
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
      description: "The branch to retrieve the pipeline from.\nExamples:\n refs/heads/master\nrefs/heads/2.1.x",
      defaultValue: 'refs/heads/master',
      trim: true)
    string(
      name: 'RELEASE_VERSION',
      description: "The tag to build and deploy.\nExamples:\n2.0.0-M6\n2.1.0-RC1\n2.0.1",
      defaultValue: '',
      trim: true)
  }

  stages {

    stage("Check build environment") {
      steps {
        sh '''#!/bin/bash
          git --version
          mvn --version
          java --version
          gpg --version
          ls -al /home/jenkins
        '''
      }
    }

    stage("Check out project") {
      steps {
        container('maven') {
          echo "Checking out tag [refs/tags/${params.RELEASE_VERSION}]"
          checkout([$class                           : 'GitSCM',
                    branches                         : [[name: "refs/tags/${params.RELEASE_VERSION}"]],
                    doGenerateSubmoduleConfigurations: false,
                    userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse-hono/hono.git']]])
        }
      }
    }

    stage("Build and deploy to Eclipse Repo") {
      steps {
          sh '''#!/bin/bash
            mvn deploy \
                -DskipTests=true -DnoDocker -DcreateJavadoc=true -DenableEclipseJarSigner=true -DskipStaging=true \
                -am -pl "\
                  :hono-adapter-amqp,\
                  :hono-adapter-coap,\
                  :hono-adapter-http,\
                  :hono-adapter-lora,\
                  :hono-adapter-mqtt,\
                  :hono-adapter-sigfox,\
                  :hono-client-amqp-common,\
                  :hono-client-amqp-connection,\
                  :hono-client-application,\
                  :hono-client-application-amqp,\
                  :hono-client-application-kafka,\
                  :hono-client-command,\
                  :hono-client-command-amqp,\
                  :hono-client-command-kafka,\
                  :hono-client-common,\
                  :hono-client-device-amqp,\
                  :hono-client-kafka-common,\
                  :hono-client-notification,\
                  :hono-client-notification-amqp,\
                  :hono-client-notification-kafka,\
                  :hono-client-registry,\
                  :hono-client-registry-amqp,\
                  :hono-client-telemetry,\
                  :hono-client-telemetry-amqp,\
                  :hono-client-telemetry-kafka,\
                  :hono-example,\
                  :hono-service-auth,\
                  :hono-service-command-router,\
                  :hono-service-device-registry-jdbc,\
                  :hono-service-device-registry-mongodb,\
                  "
          '''
      }
    }
  }
}
