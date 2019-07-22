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
 * Builds Hono's web site using Hugo every night (between 3 and 4 AM) and publishes to Eclipse.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')),
                pipelineTriggers([cron('TZ=Europe/Berlin \n # every night between 3 and 4 AM \n H 3 * * *')])])
    try {
        deleteDir()
        build()
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
 * Build stages for Hono's web site pipeline using Hugo
 *
 */
def build() {

    stage('Cloning Hono repository') {
        echo "Cloning Hono repository..."
        sh ''' 
           echo "cloning Hono repository..."
           git clone https://github.com/eclipse/hono.git $WORKSPACE/hono
           '''
        echo "Copying Documentation directory from master branch..."
        sh ''' 
           cp -r $WORKSPACE/hono/site/documentation $WORKSPACE/hono-documentation-assembly
           mkdir -p $WORKSPACE/hono-documentation-assembly/content_dirs
           '''
    }

    stage('Cloning Hugo themes') {
        echo "cloning Hugo Learn theme..."
        sh '''
            git clone https://github.com/matcornic/hugo-theme-learn.git $WORKSPACE/hono-documentation-assembly/themes/hugo-theme-learn
            cd $WORKSPACE/hono-documentation-assembly/themes/hugo-theme-learn
            git checkout 2.2.0
           '''
        echo "cloning Hugo Universal theme..."
        sh '''
            git clone https://github.com/devcows/hugo-universal-theme.git $WORKSPACE/hono/site/homepage/themes/hugo-universal-theme
            cd $WORKSPACE/hono/site/homepage/themes/hugo-universal-theme
            git checkout 1.0.0
            echo "Remove images from theme" # We do not need the pictures. Removing them, so they don't get deployed
            rm $WORKSPACE/hono/site/homepage/themes/hugo-universal-theme/static/img/*
           '''
    }

    stage('Cloning Hono web site repository') {
        sshagent(['67bd9855-4241-478b-8b98-82e66060f56d']) {
            echo "cloning Hono web site repository..."
            sh ''' 
               echo "cloning Hono web site repository..."
               git clone ssh://genie.hono@git.eclipse.org:29418/www.eclipse.org/hono $WORKSPACE/hono-web-site
               echo "scrubbing web site target directory..."
               rm -rf $WORKSPACE/hono-web-site/* # TODO replace by `rm -rf "$WORKSPACE/hono-web-site/!(copyrighted-logos)"`
               '''
        }
    }

    stage('Building documentation using Hugo') {
      echo "building documentation for versions"
      sh '''#!/bin/bash
            function prepare_stable {
                VERSION="$1.$2"
cat <<EOS >> $WORKSPACE/hono-documentation-assembly/config_version.toml
  [Languages.stable]
    weight = -20000
    languageName = "stable ($VERSION)"
    contentDir = "content_dirs/$VERSION"
    [Languages.stable.params]
      honoVersion = "stable ($VERSION)"
EOS
                git checkout $3
                cp -r $WORKSPACE/hono/site/documentation/content $WORKSPACE/hono-documentation-assembly/content_dirs/${VERSION}
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
      honoVersion = "${VERSION}"
EOS
                git checkout $3
                cp -r $WORKSPACE/hono/site/documentation/content $WORKSPACE/hono-documentation-assembly/content_dirs/${VERSION}
            }
            
            cd $WORKSPACE/hono/site/documentation
            
            TAG_STABLE=$(cat "$WORKSPACE/hono-documentation-assembly/tag_stable.txt")
            while IFS=";" read -r MAJOR MINOR TAG
            do
              if [[ "${TAG}" == "${TAG_STABLE}" ]]; then
                prepare_stable ${MAJOR} ${MINOR} ${TAG}
              else
                prepare_docu_version ${MAJOR} ${MINOR} ${TAG}
              fi
            done < <(tail -n+3 $WORKSPACE/hono-documentation-assembly/versions_supported.csv)  # skip header line and comment
          
          cd $WORKSPACE/hono-documentation-assembly
          echo "building documentation using Hugo `/shared/common/hugo/latest/hugo version`"
          /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site/docs --config config.toml,config_version.toml
          '''
    }

    stage('Building homepage using Hugo') {
        echo "building homepage..."
        sh '''
            cd $WORKSPACE/hono/site/homepage
            git checkout master
            echo "building homepage using Hugo `/shared/common/hugo/latest/hugo version`"
            /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site
            '''
    }

    stage('Commit and push') {
        sshagent(['67bd9855-4241-478b-8b98-82e66060f56d']) {
            sh '''
                cd $WORKSPACE/hono-web-site && 
                git config --global user.email "hono-bot@eclipse.org" &&
                git config --global user.name "hono Bot" &&
                git add -A && 
                git commit -m "latest web site changes" && 
                git push origin HEAD:refs/heads/master
                echo "Done" 
            '''
        }
    }
}
