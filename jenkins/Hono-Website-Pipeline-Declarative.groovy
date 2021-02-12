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
  - name: jnlp
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
  - name: hugo
    image: eclipsecbi/hugo:0.58.3
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

  stages {

    stage('Prepare workspace') {
      steps {
        echo "Cloning Hono repository..."
        sh '''
          git clone https://github.com/eclipse/hono.git ${WORKSPACE}/hono
        '''
        echo "Copying Documentation directory from master branch..."
        sh ''' 
           cp -r ${WORKSPACE}/hono/site/documentation ${WORKSPACE}/hono-documentation-assembly
           mkdir -p ${WORKSPACE}/hono-documentation-assembly/content_dirs
        '''
      }
    }

    stage('Cloning Hugo themes') {
      steps {
        echo "cloning Hugo Learn theme..."
        sh '''
            git clone https://github.com/matcornic/hugo-theme-learn.git ${WORKSPACE}/hono-documentation-assembly/themes/hugo-theme-learn
            cd ${WORKSPACE}/hono-documentation-assembly/themes/hugo-theme-learn
            git checkout 2.5.0
        '''
        echo "cloning Hugo Universal theme..."
        sh '''
            git clone https://github.com/devcows/hugo-universal-theme.git ${WORKSPACE}/hono/site/homepage/themes/hugo-universal-theme
            cd ${WORKSPACE}/hono/site/homepage/themes/hugo-universal-theme
            git checkout 1.1.1
            echo "Remove images from theme" # We do not need the pictures. Removing them, so they don't get deployed
            rm ${WORKSPACE}/hono/site/homepage/themes/hugo-universal-theme/static/img/*
        '''
      }
    }

    stage('Cloning Hono web site repository') {
      steps {
        sshagent(["git.eclipse.org-bot-ssh"]) {
          sh '''
            echo "cloning Hono web site repository..."
            git clone ssh://genie.hono@git.eclipse.org:29418/www.eclipse.org/hono.git ${WORKSPACE}/hono-web-site
            echo "scrubbing web site target directory..."
            rm -rf ${WORKSPACE}/hono-web-site/*
          '''
        }
      }
    }

    stage('Preparing web site source') {
      steps {
        echo "building documentation for versions"
        sh '''#!/bin/bash
          function prepare_stable {
              VERSION="$1.$2"
cat <<EOS >> $WORKSPACE/hono-documentation-assembly/config_version.toml
  [Languages.stable]
    weight = -20000
    languageName = "stable ($VERSION)"
    contentDir = "content_dirs/stable"
    [Languages.stable.params]
      honoVersion = "stable"
EOS
              git checkout $3
              cp -r $WORKSPACE/hono/site/documentation/content $WORKSPACE/hono-documentation-assembly/content_dirs/stable
          }

          function prepare_docu_version {
              local pad=00
              local minor="${2//[!0-9]/}" # make sure that minor only contains numbers  
              WEIGHT="-$1${pad:${#minor}:${#pad}}${minor}"
              VERSION="$1.$2"

cat <<EOS >> $WORKSPACE/hono-documentation-assembly/config_version.toml
  [Languages."${VERSION}"]
    title = "Eclipse Hono&trade; Vers.: ${VERSION}"
    weight = $WEIGHT
    languageName = "${VERSION}"
    contentDir = "content_dirs/${VERSION}"
    [Languages."${VERSION}".params]
      honoVersion = "$4"
EOS
              git checkout $3
              cp -r $WORKSPACE/hono/site/documentation/content $WORKSPACE/hono-documentation-assembly/content_dirs/${VERSION}
          }

          cd $WORKSPACE/hono/site/documentation

          TAG_STABLE=$(cat "$WORKSPACE/hono-documentation-assembly/tag_stable.txt")
          while IFS=";" read -r MAJOR MINOR TAG
          do
            if [[ "${TAG}" == "${TAG_STABLE}" ]]; then
              prepare_docu_version ${MAJOR} ${MINOR} ${TAG} "stable"
              prepare_stable ${MAJOR} ${MINOR} ${TAG}
            else
              prepare_docu_version ${MAJOR} ${MINOR} ${TAG} "${MAJOR}.${MINOR}"
            fi
          done < <(tail -n+3 $WORKSPACE/hono-documentation-assembly/versions_supported.csv)  # skip header line and comment
        '''
      }
    }

    stage('Building documentation using Hugo') {
      steps {
        container('hugo') {
          sh '''
            cd ${WORKSPACE}/hono-documentation-assembly
            echo "building documentation using Hugo $(hugo version)"
            hugo -v -d ${WORKSPACE}/hono-web-site/docs --config config.toml,config_version.toml
          '''
        }
      }
    }

    stage('Building homepage using Hugo') {
      steps {
        sh '''
          cd ${WORKSPACE}/hono/site/homepage
          git checkout master
        '''
        container('hugo') {
          sh '''
              cd ${WORKSPACE}/hono/site/homepage
              echo "building homepage using Hugo $(hugo version)"
              hugo -v -d ${WORKSPACE}/hono-web-site
              '''
        }
      }
    }

    stage('Commit and push') {
      steps {

        sshagent(["git.eclipse.org-bot-ssh"]) {
            sh '''
              cd ${WORKSPACE}/hono-web-site && 
              git add -A
              if git diff --cached --exit-code; then
                echo "No changes have been detected since last build, nothing to publish"
              else
                echo "Changes have been detected, publishing to repo 'www.eclipse.org/hono'"
                git config user.email "hono-bot@eclipse.org"
                git config user.name "Hono Bot"
                git commit -s -m "Website build ${JOB_NAME}-${BUILD_NUMBER}"
                git log --graph --abbrev-commit --date=relative -n 5
                git push origin HEAD:refs/heads/master
                echo "Done" 
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
