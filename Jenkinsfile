#!/usr/bin/env groovy
node {


    def mvnHome = tool 'mvn3.3.9'
    env.JAVA_HOME = tool 'jdk8u74'

    def startTime = new Date()
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
    def buildDate = sdf.format(startTime)
    def buildVersion = "${buildDate}_${BUILD_NUMBER}_BOSCH"

    stage('Checkout') {
        git poll: true, url: 'https://products.bosch-si.com/stash/scm/iothub/eclipse-hono.git', branch: 'develop'
    }

    stage('Deploy') {
        withCredentials([usernamePassword(credentialsId: 'dc987d42-b594-4597-9afc-297a19f82c55', passwordVariable: 'USER_PW', usernameVariable: 'USER_ID')]) {
            configFileProvider(
                    [configFile(fileId: 'd61408c0-d8e7-43c0-bf1b-4ba9f11f7736', variable: 'MAVEN_SETTINGS')]) {
                sh "git config user.email '<balasubramanian.azhagappan@bosch-si.com>'"
                sh "git config user.name '${USER_ID}'"
                sh "git config remote.origin.url 'https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git'"
                sh "git remote set-url origin 'https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git'"
                sh "${mvnHome}/bin/mvn -s ${MAVEN_SETTINGS} clean deploy -Pbuild-docker-image scm:tag -Drevision=${buildVersion} -DskipStaging=true -DconnectionUrl='scm:git:https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git' -Ddocker.host.name=sazvl0062.saz.bosch-si.com -Ddocker.host=tcp://10.56.22.164:2376"

                // deploy documentation to nginx via shared directory
                // TODO uncomment once HUB-244 is done
                //sh "cp -R hono-site/target/public /home/jenkins-slave/docker-share/hono-site"
            }
        }
    }
}