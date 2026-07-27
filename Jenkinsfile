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
                    script {
                        def results = junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                        env.TEST_SUMMARY = "${results.totalCount} run, ${results.failCount} failed, ${results.skipCount} skipped"
                    }
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
            notifySlack(
                ":white_check_mark: *SUCCESS* — `${env.JOB_NAME}` #${env.BUILD_NUMBER}\\n" +
                "*Branch:* `${env.GIT_BRANCH}`  *Commit:* `${env.GIT_COMMIT?.take(7)}`\\n" +
                "*Tests:* ${env.TEST_SUMMARY}\\n" +
                "*Duration:* ${currentBuild.durationString}\\n" +
                "<${env.BUILD_URL}|Build> | <${env.BUILD_URL}Allure_20Report/|Allure Report>"
            )
        }
        failure {
            notifySlack(
                ":x: *FAILURE* — `${env.JOB_NAME}` #${env.BUILD_NUMBER}\\n" +
                "*Branch:* `${env.GIT_BRANCH}`  *Commit:* `${env.GIT_COMMIT?.take(7)}`\\n" +
                "*Tests:* ${env.TEST_SUMMARY ?: 'suite did not complete'}\\n" +
                "*Duration:* ${currentBuild.durationString}\\n" +
                "<${env.BUILD_URL}console|Console Output> | <${env.BUILD_URL}|Build>"
            )
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
