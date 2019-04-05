def call(Map params = [:]) {
  def defaults = [
    name: "asdf",
    agent: "nodejs",
    image: "",
    build: ["npm install"],
    install: [],
    test: [],
    env: [:]
  ]

  def defaultContainers = [
    "nodejs": [
      "agent": "sidecar-nodejs",
      "build": "nodejs",
    ],
    "go": [
      "agent": "sidecar-go",
      "build": "go",
    ]
  ]

  params = defaults << params
  def containers = defaultContainers[params["agent"]]

  pipeline {
    agent { label containers["agent"] }

    environment {
      PWD = "${env.WORKSPACE}"
      TAG = "${env.GIT_COMMIT}".substring(0, 7)
      GOPATH = "${env.WORKSPACE}"
      AWS_DEFAULT_REGION = "us-east-1"
      IMAGE = "142221083342.dkr.ecr.us-east-1.amazonaws.com/${params['image']}"
    }

    stages {
      stage("set env") {
        steps {
          script {
            params.env.each {
              env[it.key] = it.value
            }
            withCredentials([file(credentialsId: 'netrc', variable: 'FILE')]) {
              sh "mv ${FILE} ~/.netrc"
            }

            def scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
            def pwd = scmUrl.replace('https://', 'src/').replace('.git', '')
            PWD = "${PWD}/${pwd}"

            sh "mkdir -p ${PWD}"
          }
        }
      }

      stage('build') {
        steps {
          container(containers["build"]) {
            script {
              dir(PWD) {
                sh 'echo "pwd: $PWD"'
                echo scm.toString()
                checkout([
                     $class: 'GitSCM',
                     branches: scm.branches,
                     doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                     extensions: scm.extensions,
                     userRemoteConfigs: scm.userRemoteConfigs
                ])
                params["build"].each {
                  sh it
                }
              }
            }
          }
        }
      }

      stage('test') {
        steps {
          container('nodejs') {
            script {
              dir(path: PWD) {
                params["test"].each {
                  sh it
                }
              }
            }
          }
        }
      }

      stage('build image') {
        steps {
          container('kubebuilder') {
            script {
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-creds']]) {
                dir(path: PWD) {
                  sh """
                    \$(aws ecr get-login --no-include-email --registry-id=142221083342 | sed -e 's|docker|docker --config=/|')

                    docker build -t ${IMAGE}:${TAG} .
                    docker --config=/ push ${IMAGE}:${TAG}
                  """
                }
              }
            }
          }
        }
      }

      stage('qa deploy') {
        when { branch "master" }
        steps {
          container('kubebuilder') {
            script {
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-creds']]) {
                dir(path: PWD) {
                  sh "cat deploy/qa/* | envsubst | kubectl apply -f -"
                  sh "kubectl rollout status --timeout=60s -f deploy/qa/deploy.yml"
                }
              }
            }
          }
        }
      }

      stage('prod deploy') {
        when { tag "v*" }
        steps {
          container('kubebuilder') {
            script {
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-creds']]) {
                dir(path: PWD) {
                  kubeEnvSetup("production")
                  sh "cat deploy/production/* | envsubst | kubectl apply -f -"
                  sh "kubectl rollout status --timeout=60s -f deploy/production/deploy.yml"
                }
              }
            }
          }
        }
      }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 15, unit: 'MINUTES')
    }
  }
}
