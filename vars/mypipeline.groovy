def call(Map params = [:]) {
  def defaults = [
    name: "asdf",
    install: ["echo installed"],
    env: [:],
    tests: [
      [
        name: "test1",
        script: "echo hey",
      ],
    ],
  ]

  params = defaults << params

  pipeline {
    agent any
    environment {
      IMAGE="142221083342.dkr.ecr.us-east-1.amazonaws.com/${params['name']}"
      TAG="asdfghh"
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
                echo "image: $IMAGE/$TAG"
              """
            }
          }
        }
      }

      stage("tests") {
        params.tests.each {
          steps {
            script {
              echo it.name
              sh it
            }
          }
        }
      }
    }
  }

}
