def call(Map params = [:]) {
  def defaults = [
    name: "asdf",
  ]

  params = defaults << params

  pipeline {
    agent any
    stages {
      stage("hello") {
        steps {
          echo params["name"]
          withCredentials([string(credentialsId: 'mysecret', variable: 'MYSECRET')]) {
            sh "echo mysecret: $MYSECRET"
          }
        }
      }
    }
  }

}
