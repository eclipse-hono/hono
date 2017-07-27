#!/usr/bin/env groovy
node('iothub') {

    def mvnHome = tool 'mvn3.3.9'
    env.JAVA_HOME = tool 'jdk8u74'
    env.DOCKER_HOST = 'tcp://10.56.22.164:2376'
    env.DOCKER_TLS_VERIFY = '1'

    def startTime = new Date()
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
    def buildDate = sdf.format(startTime)
    def buildVersion = "${buildDate}_${BUILD_NUMBER}_BOSCH"

    stage('Checkout') {
        git poll: true, url: 'https://products.bosch-si.com/stash/scm/iothub/eclipse-hono.git', branch: 'develop'
    }

    stage('Deploy') {
        withCredentials([usernamePassword(credentialsId: 'Technical_Bitbucket_User_ID', usernameVariable: 'USER_ID', passwordVariable: 'USER_PW')]) {
            configFileProvider(
                    [configFile(fileId: 'd61408c0-d8e7-43c0-bf1b-4ba9f11f7736', variable: 'MAVEN_SETTINGS')]) {
                sh "git config user.email '<Jenkinscommituser.IoTHub@bosch-si.com>'"
                sh "git config user.name '${USER_ID}'"
                sh "git config remote.origin.url 'https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git'"
                sh "git remote set-url origin 'https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git'"
                withSonarQubeEnv('ECS-Sonar') {
                    sh script: """\\
                        ${mvnHome}/bin/mvn -B -s ${MAVEN_SETTINGS} clean org.jacoco:jacoco-maven-plugin:0.7.9:prepare-agent verify \\
                        org.sonarsource.scanner.maven:sonar-maven-plugin:3.3.0.603:sonar deploy -Pbuild-docker-image,run-tests scm:tag \\
                        -Drevision=${buildVersion} -DskipStaging=true \\
                        -DconnectionUrl='scm:git:https://${USER_ID}:${USER_PW}@products.bosch-si.com/stash/scm/iothub/eclipse-hono.git' \\
                        -Ddocker.host.name=sazvl0062.saz.bosch-si.com\\
                        """.stripIndent()
                }
                // deploy documentation to nginx
                sh "rm -rf /opt/nginx/data/html/hono-site"
                sh "mkdir -p /opt/nginx/data/html/hono-site"
                sh "cp -R site/target/* /opt/nginx/data/html/hono-site"
            }
        }
    }
    stage('Push to Docker Hub') {
        withCredentials([usernamePassword(credentialsId: 'cred-dockerhub-iothubtech', usernameVariable: 'DOCKERHUB_USER_ID', passwordVariable: 'DOCKERHUB_USER_PW')]) {
            // rename/tag images
            sh "docker tag eclipsehono/hono-service-messaging:${buildVersion} bsinno/hono-service-messaging:${buildVersion}"
            sh "docker tag eclipsehono/hono-service-messaging:${buildVersion} bsinno/hono-service-messaging:latest"
            sh "docker tag eclipsehono/hono-service-auth:${buildVersion} bsinno/hono-service-auth:${buildVersion}"
            sh "docker tag eclipsehono/hono-service-auth:${buildVersion} bsinno/hono-service-auth:latest"
            sh "docker tag eclipsehono/hono-adapter-rest-vertx:${buildVersion} bsinno/hono-adapter-rest-vertx:${buildVersion}"
            sh "docker tag eclipsehono/hono-adapter-rest-vertx:${buildVersion} bsinno/hono-adapter-rest-vertx:latest"
            sh "docker tag eclipsehono/hono-adapter-mqtt-vertx:${buildVersion} bsinno/hono-adapter-mqtt-vertx:${buildVersion}"
            sh "docker tag eclipsehono/hono-adapter-mqtt-vertx:${buildVersion} bsinno/hono-adapter-mqtt-vertx:latest"
            sh "docker tag eclipsehono/hono-dispatch-router:${buildVersion} bsinno/hono-dispatch-router:${buildVersion}"
            sh "docker tag eclipsehono/hono-dispatch-router:${buildVersion} bsinno/hono-dispatch-router:latest"
            sh "docker tag eclipsehono/hono-artemis:${buildVersion} bsinno/hono-artemis:${buildVersion}"
            sh "docker tag eclipsehono/hono-artemis:${buildVersion} bsinno/hono-artemis:latest"
            sh "docker tag eclipsehono/grafana:${buildVersion} bsinno/hono-grafana:${buildVersion}"
            sh "docker tag eclipsehono/grafana:${buildVersion} bsinno/hono-grafana:latest"

            // push to dockerhub
            sh "docker login -u $DOCKERHUB_USER_ID -p $DOCKERHUB_USER_PW"
            sh "docker push bsinno/hono-service-messaging:${buildVersion}"
            sh "docker push bsinno/hono-service-messaging:latest"
            sh "docker push bsinno/hono-service-auth:${buildVersion}"
            sh "docker push bsinno/hono-service-auth:latest"
            sh "docker push bsinno/hono-adapter-rest-vertx:${buildVersion}"
            sh "docker push bsinno/hono-adapter-rest-vertx:latest"
            sh "docker push bsinno/hono-adapter-mqtt-vertx:${buildVersion}"
            sh "docker push bsinno/hono-adapter-mqtt-vertx:latest"
            sh "docker push bsinno/hono-dispatch-router:${buildVersion}"
            sh "docker push bsinno/hono-dispatch-router:latest"
            sh "docker push bsinno/hono-artemis:${buildVersion}"
            sh "docker push bsinno/hono-artemis:latest"
            sh "docker push bsinno/hono-grafana:${buildVersion}"
            sh "docker push bsinno/hono-grafana:latest"
        }
    }
}