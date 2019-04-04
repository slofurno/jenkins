def call(Map params = [:]) {
  def defaults = [
    name: "asdf",
    install: ["echo installed"],
    env: [:]
  ]

  params = defaults << params

  pipeline {
    agent any
    environment {
      REPO="142221083342.dkr.ecr.us-east-1.amazonaws.com"
    }
    stages {
      stage("set env") {
        steps {
          script {
            params.env.each {
              env[it.key] = it.value
            }
          }
        }
      }

      stage("hello") {
        steps {
          echo params["name"]
          withCredentials([string(credentialsId: 'mysecret', variable: 'MYSECRET')]) {
            script {
              sh "env"
              sh "echo mysecret: $MYSECRET"
              params["install"].each {
                sh it
              }

              sh """
                echo "project name: ${params['name']}"
              """
            }
          }
        }
      }
    }
  }

}
