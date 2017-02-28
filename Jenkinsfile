node {

    def mvnHome = tool 'M3'

    stage ('Checkout') {
        git poll: true, url: 'https://products.bosch-si.com/stash/scm/iothub/eclipse-hono.git', branch: 'develop'
    }

    stage('Compile') {
        sh "${mvnHome}/bin/mvn clean compile"
    }

}