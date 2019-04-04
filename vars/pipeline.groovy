def call(Map params) {
  pipeline {
    agent any
    stages {
      stage("hello") {
        echo "hello"
      }
    }
  }

}
