def call(Map params) {
  pipeline {
    agent any
    stages {
      stage("hello") {
        steps {
          echo "hello"
        }
      }
    }
  }

}
