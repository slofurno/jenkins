def call(Map params = [:]) {
  def defaults = [
    name: "asdf",
    install: ["echo installed"],
  ]

  params = defaults << params

  pipeline {
    agent any
    stages {
      stage("hello") {

        environment {
          NAME = params["name"]
        }

        steps {
          echo params["name"]
          withCredentials([string(credentialsId: 'mysecret', variable: 'MYSECRET')]) {
            script {
              sh "echo mysecret: $MYSECRET"
              params["install"].each {
                sh it
              }

              sh '''
                echo "project name: ${NAME}"
              '''
            }
          }
        }
      }
    }
  }

}
