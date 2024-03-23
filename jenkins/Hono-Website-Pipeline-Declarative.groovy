/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 * Builds Hono's web site using Hugo every night (between 3 and 4 AM) and publishes to
 * Eclipse www git repository.
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
          - name: "jnlp"
            volumeMounts:
            - mountPath: /home/jenkins/.ssh
              name: volume-known-hosts
            env:
            - name: "HOME"
              value: "/home/jenkins"
            resources:
              limits:
                memory: "512Mi"
                cpu: "1"
              requests:
                memory: "512Mi"
                cpu: "1"
          - name: "hugo"
            image: "cibuilds/hugo:0.102"
            command:
            - cat
            tty: true
            resources:
              limits:
                memory: "512Mi"
                cpu: "1"
              requests:
                memory: "512Mi"
                cpu: "1"
          volumes:
          - configMap:
              name: known-hosts
            name: volume-known-hosts
        """
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '3'))
    disableConcurrentBuilds()
    timeout(time: 15, unit: 'MINUTES')
  }

  triggers {
      cron('TZ=Europe/Berlin\n# every night between 3 and 4 AM\nH 3 * * *')
  }

  environment {
    HONO_HOMEPAGE_DIR="${WORKSPACE}/hono/site/homepage"
    HONO_DOCUMENTATION_DIR="${WORKSPACE}/hono/site/documentation"
    WEBSITE_REPO_DIR="${WORKSPACE}/hono-website"
    DOC_ASSEMBLY_DIR="${WORKSPACE}/hono-documentation-assembly"
  }

  stages {

    stage("Prepare workspace") {
      steps {
        sh '''#!/bin/bash
          echo "cloning Hono repository..."
          git clone --recurse-submodules https://github.com/eclipse-hono/hono.git "${WORKSPACE}/hono"
          echo "copying Documentation directory from master branch..."
          cp -r "${HONO_DOCUMENTATION_DIR}" "${DOC_ASSEMBLY_DIR}"
          mkdir -p "${DOC_ASSEMBLY_DIR}/content_dirs"
        '''
      }
    }

    stage("Clone Hono web site repository") {
      steps {
        sshagent(credentials: [ "github-bot-ssh" ]) {
          sh '''#!/bin/bash
            echo "cloning Hono web site repository..."
            git clone ssh://git@github.com/eclipse-hono/hono-website.git "${WEBSITE_REPO_DIR}"
            echo "scrubbing web site directory..."
            (cd "${WEBSITE_REPO_DIR}"; git rm -r --quiet -- ':!README.md'; cp "${WORKSPACE}/hono/LICENSE" .)
          '''
        }
      }
    }

    stage("Build home page") {
      steps {
        container("hugo") {
          sh '''#!/bin/bash
            cd "${HONO_HOMEPAGE_DIR}"
            echo "using $(hugo version)"
            echo "removing obsolete images that come with theme used for home page ..."
            # we do not need the pictures that come with the theme
            # removing them, so they don't get deployed.
            rm themes/hugo-universal-theme/static/img/*
            echo "building home page..."
            hugo -v -d "${WEBSITE_REPO_DIR}"
          '''
        }
      }
    }

    stage("Build documentation") {
      steps {
        sh '''#!/bin/bash
          echo "determining supported versions of documentation"
          function prepare_stable {
            VERSION="$1.$2"
            {
              echo "  [Languages.stable]"
              echo "    weight = -20000"
              echo "    languageName = \\"stable ($VERSION)\\""
              echo "    contentDir = \\"content_dirs/stable\\""
              echo "    [Languages.stable.params]"
              echo "      honoVersion = \\"stable\\""
            } >> "${DOC_ASSEMBLY_DIR}/config_version.toml"
            cp -r "${HONO_DOCUMENTATION_DIR}/content" "${DOC_ASSEMBLY_DIR}/content_dirs/stable"
          }

          function prepare_docu_version {
            local pad=00
            local minor="${2//[!0-9]/}" # make sure that minor only contains numbers  
            WEIGHT="-$1${pad:${#minor}:${#pad}}${minor}"
            VERSION="$1.$2"
            {
              echo "  [Languages.\\"${VERSION}\\"]"
              echo "    title = \\"Eclipse Hono&trade; Vers.: ${VERSION}\\""
              echo "    weight = ${WEIGHT}"
              echo "    languageName = \\"${VERSION}\\""
              echo "    contentDir = \\"content_dirs/${VERSION}\\""
              echo "    [Languages.\\"${VERSION}\\".params]"
              echo "      honoVersion = \\"$3\\""
            } >> "${DOC_ASSEMBLY_DIR}/config_version.toml"
            cp -r "${HONO_DOCUMENTATION_DIR}/content" "${DOC_ASSEMBLY_DIR}/content_dirs/$3"
          }

          cd "${HONO_DOCUMENTATION_DIR}"

          TAG_STABLE=$(cat "${DOC_ASSEMBLY_DIR}/tag_stable.txt")
          while IFS=";" read -r MAJOR MINOR TAG
          do
            git checkout --recurse-submodules "${TAG}"
            if [[ "${TAG}" == "${TAG_STABLE}" ]]; then
              prepare_stable "${MAJOR}" "${MINOR}"
            fi
            prepare_docu_version "${MAJOR}" "${MINOR}" "${MAJOR}.${MINOR}"
          done < <(tail -n+3 "${DOC_ASSEMBLY_DIR}/versions_supported.csv")  # skip header line and comment
        '''
        container("hugo") {
          sh '''#!/bin/bash
            echo "building documentation..."
            cd "${DOC_ASSEMBLY_DIR}"
            hugo -v -d "${WEBSITE_REPO_DIR}/docs" --config config.toml,config_version.toml
          '''
        }
      }
    }

    stage("Commit and push web site") {
      steps {
        sshagent(credentials: [ "github-bot-ssh" ]) {
          sh '''#!/bin/bash
            cd "${WEBSITE_REPO_DIR}"
            git add -A
            if git diff --cached --quiet; then
              echo "no changes have been detected since last build, nothing to publish"
            else
              echo "changes have been detected, publishing to Hono website repo on GitHub"
              git config user.email "hono-bot@eclipse.org"
              git config user.name "Hono Bot"
              git commit -s -m "Website build ${JOB_NAME}-${BUILD_NUMBER}"
              git push origin HEAD:refs/heads/main
              echo "done" 
            fi
          '''
        }
      }
    }

  }

  post {
    fixed {
      step([$class                  : 'Mailer',
            notifyEveryUnstableBuild: true,
            recipients              : 'hono-dev@eclipse.org',
            sendToIndividuals       : false])
    }
    failure {
      step([$class                  : 'Mailer',
            notifyEveryUnstableBuild: true,
            recipients              : 'hono-dev@eclipse.org',
            sendToIndividuals       : false])
    }
  }
}
