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
        steps {
          echo params["name"]
          withCredentials([string(credentialsId: 'mysecret', variable: 'MYSECRET')]) {
            dir(path: SRC_DIR) {
              sh "echo mysecret: $MYSECRET"
              params["install"].each {
                sh it
              }
            }
          }
        }
      }
    }
  }

}
