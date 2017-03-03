#!/usr/bin/env groovy
node {

    def mvnHome = tool 'mvn3.3.9'
    env.JAVA_HOME = tool 'jdk8u74'


    stage('Checkout') {
        git poll: true, url: 'https://products.bosch-si.com/stash/scm/iothub/eclipse-hono.git', branch: 'develop'
    }

    stage('Deploy') {
        withCredentials([usernamePassword(credentialsId: 'dc987d42-b594-4597-9afc-297a19f82c55', passwordVariable: 'USER_PW', usernameVariable: 'USER_ID')]) {
            configFileProvider(
                    [configFile(fileId: 'd61408c0-d8e7-43c0-bf1b-4ba9f11f7736', variable: 'MAVEN_SETTINGS')]) {
                sh "${mvnHome}/bin/mvn -s ${MAVEN_SETTINGS} clean deploy -Pbuild-docker-image scm:tag -Drevision=${BUILD_NUMBER}-BOSCH -DskipStaging=true -DconnectionUrl='scm:git:https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git'"
            }
        }
    }
}