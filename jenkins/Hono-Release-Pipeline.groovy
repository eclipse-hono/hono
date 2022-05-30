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
 * Jenkins pipeline script for Hono release.
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
    image: "maven:3.8.4-eclipse-temurin-17"
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
      name: "BRANCH",
      description: "The branch to build and release from.\nExamples:\n refs/heads/master\nrefs/heads/1.4.x",
      defaultValue: "refs/heads/master",
      trim: true)
    string(
      name: "RELEASE_VERSION",
      description: "The version identifier (tag name) to use for the artifacts built and released by this job.\nExamples:\n1.0.0-M6\n1.3.0-RC1\n1.4.4",
      defaultValue: "",
      trim: true)
    string(
      name: "NEXT_VERSION",
      description: "The version identifier to use during development of the next version.\nExamples:\n2.0.0-SNAPSHOT\n1.6.0-SNAPSHOT",
      defaultValue: "",
      trim: true)
    booleanParam(
      name: "DEPLOY_DOCUMENTATION",
      description: "Deploy documentation for this release to web site.\nDisable for milestone release.",
      defaultValue: true)
    booleanParam(
      name: "STABLE_DOCUMENTATION",
      description: "Set the documentation for this release as the new stable version.\nDisable for milestone release.",
      defaultValue: true)
  }

  stages {

    stage("Check out") {
      steps {
        echo "Checking out branch: ${params.BRANCH}"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "${params.BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false,
                  userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse/hono.git']]])
      }
    }

    stage("Build") {
      steps {
        container("maven") {
          sh "mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${params.RELEASE_VERSION}"
          sh "mvn clean install javadoc:aggregate \
                  -Dmaven.test.failure.ignore=false \
                  -DenableEclipseJarSigner=true \
                  -DsnapshotDependencyAllowed=false \
                  -Ddocker.skip.build=true \
                  -DnoDocker"
        }
      }
    }

    stage("Add version for documentation") {
      steps {
        sh '''
          if [[ ${params.DEPLOY_DOCUMENTATION} =~ (T|TRUE) ]]; then
            echo "add to supported versions"
            MAJOR="${params.RELEASE_VERSION%%.*}" # before first dot
            rest="${params.RELEASE_VERSION#*.}" # after first dot
            MINOR="${rest%%.*}"  # before first dot of rest
            echo "${MAJOR};${MINOR};${params.RELEASE_VERSION}" >> site/documentation/versions_supported.csv
            git add site/documentation/versions_supported.csv
            if [[ ${params.STABLE_DOCUMENTATION} =~ (T|TRUE) ]]; then
              echo "set as stable version"
              echo "${params.RELEASE_VERSION}" > site/documentation/tag_stable.txt
              git add site/documentation/tag_stable.txt
            fi
          else
            echo "skip release of documentation"
          fi
        '''
      }
    }

    stage("Commit and tag release version") {
      steps {
        sh '''
           git config user.email "hono-bot@eclipse.org"
           git config user.name "hono-bot"
           git add pom.xml \\*/pom.xml
           git commit -m "Release ${params.RELEASE_VERSION}"
           git tag ${params.RELEASE_VERSION}
           '''
      }
    }

    stage("Set next version") {
      steps {
        container("maven") {
          sh "mvn versions:set -DallowSnapshots=true -DgenerateBackupPoms=false -DnewVersion=${params.NEXT_VERSION}"
        }
      }
    }

    stage("Commit and push") {
      steps {
        sshagent(credentials: [ "github-bot-ssh" ]) {
          sh '''
             git add pom.xml \\*/pom.xml
             git commit -m "Bump version to ${params.NEXT_VERSION}"
             git push --all ssh://git@github.com/eclipse/hono.git && git push --tags ssh://git@github.com/eclipse/hono.git
             '''
        }
      }
    }

    stage("Copy Artifacts") {
      steps {
        sshagent(credentials: [ "projects-storage.eclipse.org-bot-ssh" ]) {
          sh ''' 
             chmod +r cli/target/hono-cli-${params.RELEASE_VERSION}-exec.jar
             scp cli/target/hono-cli-${params.RELEASE_VERSION}-exec.jar \
                 genie.hono@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/hono/
             '''
        }
      }
    }

  }
}
