pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    // Requires the GitHub plugin + a webhook registered on the repo pointing at
    // <public-url>/github-webhook/ (see README). Jenkins only starts listening for
    // this trigger AFTER the job has run at least once -- run "Build Now" manually first.
    triggers {
        githubPush()
    }

    environment {
        // Secret text credential holding a Slack incoming webhook URL.
        // Manage Jenkins > Credentials > (global) > Add Credentials > Secret text, id: slack-webhook-url
        SLACK_WEBHOOK_URL = credentials('slack-webhook-url')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B dependency:go-offline'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
        }

        stage('Report') {
            steps {
                sh 'mvn -B allure:report'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                    publishHTML(target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/allure-maven-plugin',
                        reportFiles: 'index.html',
                        reportName: 'Allure Report'
                    ])
                }
            }
        }
    }

    post {
        success {
            notifySlack(":white_check_mark: *SUCCESS* — ${env.JOB_NAME} #${env.BUILD_NUMBER}\\n${env.BUILD_URL}")
        }
        failure {
            notifySlack(":x: *FAILURE* — ${env.JOB_NAME} #${env.BUILD_NUMBER}\\n${env.BUILD_URL}")
        }
    }
}

void notifySlack(String message) {
    sh """
        curl -sS -X POST -H 'Content-type: application/json' \
            --data '{"text": "${message}"}' \
            "\$SLACK_WEBHOOK_URL"
    """
}
