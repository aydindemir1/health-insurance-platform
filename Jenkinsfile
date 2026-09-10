pipeline {
    agent { label 'java21-node24' }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds(abortPrevious: true)
        skipStagesAfterUnstable()
    }

    environment {
        SONAR_SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend quality') {
            parallel {
                stage('Authorization') {
                    steps { dir('services/authorization-service') { sh './mvnw --batch-mode --no-transfer-progress verify' } }
                    post { always { junit allowEmptyResults: false, testResults: 'services/authorization-service/target/surefire-reports/*.xml' } }
                }
                stage('Policy') {
                    steps { dir('services/policy-service') { sh './mvnw --batch-mode --no-transfer-progress verify' } }
                    post { always { junit allowEmptyResults: false, testResults: 'services/policy-service/target/surefire-reports/*.xml' } }
                }
                stage('Claims and Billing') {
                    steps { dir('services/claims-billing-service') { sh './mvnw --batch-mode --no-transfer-progress verify' } }
                    post { always { junit allowEmptyResults: false, testResults: 'services/claims-billing-service/target/surefire-reports/*.xml' } }
                }
                stage('Notification Worker') {
                    steps { dir('services/notification-worker') { sh './mvnw --batch-mode --no-transfer-progress verify' } }
                    post { always { junit allowEmptyResults: false, testResults: 'services/notification-worker/target/surefire-reports/*.xml' } }
                }
                stage('Search') {
                    steps { dir('services/search-service') { sh './mvnw --batch-mode --no-transfer-progress verify' } }
                    post { always { junit allowEmptyResults: false, testResults: 'services/search-service/target/surefire-reports/*.xml' } }
                }
            }
        }

        stage('Frontend quality') {
            steps {
                dir('apps/operations-portal') {
                    sh 'npm ci'
                    sh 'npm run lint'
                    sh 'npm test'
                    sh 'npm run build'
                }
            }
        }

        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv('health-sonarqube') {
                    sh '''
                        "${SONAR_SCANNER_HOME}/bin/sonar-scanner" \
                          -Dsonar.projectVersion="${BUILD_NUMBER}"
                    '''
                }
            }
        }

        stage('Quality Gate') {
            options { timeout(time: 10, unit: 'MINUTES') }
            steps {
                waitForQualityGate abortPipeline: true
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}
