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

  def testSteps = params.test.collect {
    switch(it) {
    case Map:
      return it
    default:
      return [container: containers["build"], script: it]
    }
  }

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
            TARGET = scmUrl.replace('https://', 'src/').replace('.git', '')
            PWD = "${PWD}/${TARGET}"

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

                def reltarget = [ [$class: 'RelativeTargetDirectory', relativeTargetDir: ""] ]
                checkout([
                  $class: 'GitSCM',
                  branches: scm.branches,
                  doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                  extensions: (scm.extensions + reltarget),
                  userRemoteConfigs: scm.userRemoteConfigs,
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
          script {
            for (int i = 0; i < testSteps.size(); i++) {
              container(testSteps[i].container) {
                dir(path: PWD) {
                  sh testSteps[i].script
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

      stage('staging/qa deploy') {
        when { branch "master" }
        steps {
          container('kubebuilder') {
            script {
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-creds']]) {
                dir(path: PWD) {
                  sh "cat deploy/qa/* | envsubst | kubectl apply -f -"
                  sh "kubectl rollout status -f deploy/qa/deploy.yml"

                  kubeEnvSetup("staging")
                  sh "cat deploy/staging/* | envsubst | kubectl apply -f -"
                  sh "kubectl rollout status -f deploy/staging/deploy.yml"
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
                  sh "kubectl rollout status -f deploy/production/deploy.yml"
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
