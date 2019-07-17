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
    }

    stage('Cloning Hugo themes') {
        echo "cloning Hugo Learn theme..."
        sh '''
            git clone https://github.com/matcornic/hugo-theme-learn.git $WORKSPACE/hono/site/documentation/themes/hugo-theme-learn
            cd $WORKSPACE/hono/site/documentation/themes/hugo-theme-learn
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
        echo "building latest documentation from master..."
        sh '''
            cd $WORKSPACE/hono/site/documentation
            echo "building documentation using Hugo `/shared/common/hugo/latest/hugo version`"
            /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site/docs/latest
            '''
        
        echo "building documentation for versions"
        sh '''#!/bin/bash
            cd $WORKSPACE/hono/site/documentation

            function build_documentation_in_version {
                VERSION="$1.$2"
    
                if [[ "$1" == "stable" ]]; then
                   WEIGHT="-20000"
                   VERSION="$1"
                else
                   local pad=00
                   WEIGHT="-$1${pad:${#2}:${#pad}}${2}"
                fi
                
                echo "Going to check out version ${VERSION} from branch/tag $3"
                git checkout $3
                
cat <<EOS >> $WORKSPACE/hono/site/homepage/menu_main.toml

[[menu.main]]
  parent = "Documentation"
  name = "${VERSION}"
  url  = "/docs/${VERSION}"
  weight = $WEIGHT
EOS

cat <<EOS > config_release_version.toml
baseurl = "https://www.eclipse.org/hono/docs/${VERSION}/"
[params]
  honoVersion = "${VERSION}"
EOS

                echo "Going to build documentation version ${VERSION} in: $WORKSPACE/hono-web-site/docs/${VERSION}"
                /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site/docs/$VERSION --config config.toml,config_release_version.toml
                rm config_release_version.toml
            }
            
            TAG_STABLE=$(cat "$WORKSPACE/hono/site/homepage/tag_stable.txt")
            if [[ -n "$TAG_STABLE" ]]; then
              build_documentation_in_version "stable" "" ${TAG_STABLE}  # build stable version as "stable"  
            fi
            
            while IFS=";" read -r MAJOR MINOR BRANCH_OR_TAG
            do
              build_documentation_in_version ${MAJOR} ${MINOR} ${BRANCH_OR_TAG}
            done < <(tail -n+4 $WORKSPACE/hono/site/homepage/versions_supported.csv)  # skip header line and comment
            
            git checkout master
         '''

        echo "adding redirect from hono/docs/ to version stable"
        sh '''
cat  <<EOS > $WORKSPACE/hono-web-site/docs/index.html
<!DOCTYPE html>
<html>
<head><title>https://www.eclipse.org/hono/docs/stable/</title>
    <link rel="canonical" href="https://www.eclipse.org/hono/docs/stable/"/>
    <meta name="robots" content="noindex">
    <meta charset="utf-8"/>
    <meta http-equiv="refresh" content="0; url=https://www.eclipse.org/hono/docs/stable/"/>
</head>
</html>
EOS
         '''
    }

    stage('Building homepage using Hugo') {
        echo "building homepage..."
        sh '''
            cd $WORKSPACE/hono/site/homepage
            echo "building homepage using Hugo `/shared/common/hugo/latest/hugo version`"
            /shared/common/hugo/latest/hugo -v -d $WORKSPACE/hono-web-site --config config.toml,menu_main.toml
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
