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
 * Jenkins pipeline script for creating a Hono release including the CLI native executable.
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
      name: "BRANCH",
      description: "The branch to build and release from.\nExamples:\n refs/heads/master\nrefs/heads/2.1.x",
      defaultValue: "refs/heads/master",
      trim: true)
    string(
      name: "RELEASE_VERSION",
      description: "The version identifier (tag name) to use for the artifacts built and released by this job.\nExamples:\n2.0.0-M6\n2.1.0-RC1\n2.0.1",
      defaultValue: "",
      trim: true)
    string(
      name: "NEXT_VERSION",
      description: "The version identifier to use during development of the next version.\nExamples:\n2.2.0-SNAPSHOT",
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
    booleanParam(
      name: "COPY_ARTIFACTS",
      description: "Copy the artifacts created by this build to the Eclipse download server.",
      defaultValue: true)
  }

  stages {

    stage("Check build environment") {
      steps {
        sh '''#!/bin/bash
          git --version
          mvn --version
          java --version
          native-image --version
          upx --version
          ssh -V
          if [[ "${COPY_ARTIFACTS}" =~ (t|true) ]]; then
            echo "will deploy artifacts ..."
          else
            echo "will not deploy artifacts ..."
          fi
          ls -al /home/jenkins
        '''
      }
    }

    stage("Check out") {
      steps {
        echo "Checking out branch: ${params.BRANCH}"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "${params.BRANCH}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [[$class: 'LocalBranch', localBranch: "**"]],
                  userRemoteConfigs                : [[credentialsId: 'github-bot-ssh', url: 'ssh://git@github.com/eclipse-hono/hono.git']]])
      }
    }

    stage("Build") {
      steps {
        sh '''#!/bin/bash
          mkdir artifacts
          mvn versions:set -DgenerateBackupPoms=false -DnewVersion=${RELEASE_VERSION}
          mvn clean install javadoc:aggregate \
                  -Dmaven.test.failure.ignore=false \
                  -DenableEclipseJarSigner=true \
                  -DsnapshotDependencyAllowed=false \
                  -Ddocker.skip.build=true \
                  -DnoDocker
          chmod +r cli/target/hono-cli-${RELEASE_VERSION}-exec.jar
          mv cli/target/hono-cli-${RELEASE_VERSION}-exec.jar artifacts/
          # We need to build the native executable from scratch and without signing the jar files.
          # This is to prevent the native image compiler running into signature errors when it
          # tries to load classes of the same package from different jars having different signatures.
          mvn clean install \
                  -DskipTests \
                  -Ddocker.skip.build=true \
                  -DnoDocker \
                  -Dquarkus.native.remote-container-build=false \
                  -Dquarkus.native.compression.level=9 \
                  -Dquarkus.native.native-image-xmx=6G \
                  -Pbuild-cli-native-executable \
                  -am -pl :hono-cli
          chmod +r cli/target/hono-cli-${RELEASE_VERSION}-exec
          mv cli/target/hono-cli-${RELEASE_VERSION}-exec artifacts/hono-cli-${RELEASE_VERSION}
        '''
      }
    }

    /**
    * The references in the script below do not use the "params." prefix as that
    * does not seem to work in a multi-line script delimited by '''
    */
    stage("Add version for documentation") {
      steps {
        sh '''#!/bin/bash
          echo "DEPLOY_DOCUMENTATION: ${DEPLOY_DOCUMENTATION}"
          echo "STABLE_DOCUMENTATION: ${STABLE_DOCUMENTATION}"
          if [[ "${DEPLOY_DOCUMENTATION}" =~ (t|true) ]]; then
            echo "add to supported versions"
            MAJOR="${RELEASE_VERSION%%.*}" # before first dot
            rest="${RELEASE_VERSION#*.}" # after first dot
            MINOR="${rest%%.*}"  # before first dot of rest
            echo "${MAJOR};${MINOR};${RELEASE_VERSION}" >> site/documentation/versions_supported.csv
            git add site/documentation/versions_supported.csv
            if [[ "${STABLE_DOCUMENTATION}" =~ (t|true) ]]; then
              echo "set as stable version"
              echo "${RELEASE_VERSION}" > site/documentation/tag_stable.txt
              git add site/documentation/tag_stable.txt
            fi
          else
            echo "will not publish documentation for release ..."
          fi
        '''
      }
    }

    stage("Commit and tag release version") {
      steps {
        sh '''#!/bin/bash
           if [[ -n "${NEXT_VERSION}" ]]; then
             git config user.email "hono-bot@eclipse.org"
             git config user.name "hono-bot"
             git add pom.xml \\*/pom.xml
             git commit -m "Release ${RELEASE_VERSION}"
             git tag ${RELEASE_VERSION}
           else
             echo "will not create tag for release ..."
           fi
        '''
      }
    }

    stage("Set next version") {
      steps {
        sh '''#!/bin/bash
           if [[ -n "${NEXT_VERSION}" ]]; then
             mvn versions:set -DallowSnapshots=true -DgenerateBackupPoms=false -DnewVersion=${NEXT_VERSION}
           else
             echo "will not create next version ..."
           fi
        '''
      }
    }

    stage("Commit and push") {
      steps {
        sshagent(credentials: [ "github-bot-ssh" ]) {
          sh '''#!/bin/bash
             if [[ -n "${NEXT_VERSION}" ]]; then
               git add pom.xml \\*/pom.xml
               git commit -m "Bump version to ${NEXT_VERSION}"
               git push --all ssh://git@github.com/eclipse-hono/hono.git && git push --tags ssh://git@github.com/eclipse-hono/hono.git
             else
               echo "will not push newly created tag and next version ..."
             fi
             '''
        }
      }
    }

    stage("Copy Artifacts") {
      steps {
        sshagent(credentials: [ "projects-storage.eclipse.org-bot-ssh" ]) {
          sh '''#!/bin/bash
             echo "artifacts for downloading created by build process:"
             ls -al artifacts
             if [[ "${COPY_ARTIFACTS}" =~ (t|true) ]]; then
               scp artifacts/* genie.hono@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/hono/
             else
               echo "will not copy artifacts to download server ..."
             fi
             '''
        }
      }
    }

  }
}
