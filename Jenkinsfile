pipeline {
    agent { label 'java21-node24-docker' }

    parameters {
        booleanParam(name: 'PUBLISH_ARTIFACTS', defaultValue: false,
            description: 'Publish Maven artifacts and OCI images after every quality gate passes')
        string(name: 'NEXUS_URL', defaultValue: 'http://nexus:8081', description: 'Nexus base URL')
        string(name: 'HARBOR_REGISTRY', defaultValue: 'harbor.example.invalid', description: 'Harbor registry host')
        string(name: 'HARBOR_API_URL', defaultValue: 'https://harbor.example.invalid', description: 'Harbor API origin')
        string(name: 'HARBOR_PROJECT', defaultValue: 'health-insurance', description: 'Harbor project')
        choice(name: 'HARBOR_MAX_ALLOWED_SEVERITY', choices: ['High', 'Medium', 'Low'], description: 'Severity above this value blocks publication')
    }

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

        stage('Prime Maven runtime') {
            steps {
                dir('services/authorization-service') {
                    sh './mvnw --batch-mode --no-transfer-progress --version'
                }
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

        stage('Publish Maven artifacts to Nexus') {
            when {
                allOf {
                    branch 'main'
                    expression { params.PUBLISH_ARTIFACTS }
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'nexus-publisher',
                    usernameVariable: 'NEXUS_USERNAME',
                    passwordVariable: 'NEXUS_PASSWORD'
                )]) {
                    sh '''
                        set +x
                        for service in authorization-service policy-service claims-billing-service notification-worker search-service; do
                          jar=$(find "services/${service}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)
                          test -n "${jar}"
                          version=$("services/${service}/mvnw" --quiet --non-recursive help:evaluate -Dexpression=project.version -DforceStdout)
                          repository=releases
                          case "${version}" in *-SNAPSHOT) repository=snapshots ;; esac
                          "services/${service}/mvnw" --batch-mode --no-transfer-progress \
                            --settings .jenkins/maven-settings.xml \
                            deploy:deploy-file \
                            -DrepositoryId="nexus-${repository}" \
                            -Durl="${NEXUS_URL}/repository/maven-${repository}/" \
                            -Dfile="${jar}" \
                            -DpomFile="services/${service}/pom.xml" \
                            -DgeneratePom=false
                        done
                    '''
                }
            }
        }

        stage('Generate CycloneDX SBOMs') {
            steps {
                sh '''
                    set -eu
                    mkdir -p artifacts/sbom
                    for service in authorization-service policy-service claims-billing-service notification-worker search-service; do
                      (cd "services/${service}" && ./mvnw --batch-mode -DskipTests \
                        org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
                        -DoutputFormat=json -DoutputName=bom)
                      cp "services/${service}/target/bom.json" "artifacts/sbom/${service}.cdx.json"
                    done
                    (cd apps/operations-portal && npm sbom --sbom-format cyclonedx) \
                      > artifacts/sbom/operations-portal.cdx.json
                '''
                archiveArtifacts artifacts: 'artifacts/sbom/*.cdx.json', fingerprint: true
            }
        }

        stage('Publish OCI images to Harbor') {
            when {
                allOf {
                    branch 'main'
                    expression { params.PUBLISH_ARTIFACTS }
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-publisher',
                    usernameVariable: 'HARBOR_USERNAME',
                    passwordVariable: 'HARBOR_PASSWORD'
                )]) {
                    sh '''
                        set +x
                        echo "${HARBOR_PASSWORD}" | docker login "${HARBOR_REGISTRY}" \
                          --username "${HARBOR_USERNAME}" --password-stdin

                        for service in authorization-service policy-service claims-billing-service notification-worker search-service; do
                          image="${HARBOR_REGISTRY}/${HARBOR_PROJECT}/${service}:${GIT_COMMIT}"
                          docker build --file "services/${service}/Dockerfile" \
                            --tag "${image}" "services/${service}"
                          docker push "${image}"
                        done

                        image="${HARBOR_REGISTRY}/${HARBOR_PROJECT}/operations-portal:${GIT_COMMIT}"
                        docker build --file apps/operations-portal/Dockerfile \
                          --tag "${image}" apps/operations-portal
                        docker push "${image}"

                        HARBOR_IMAGE_TAG="${GIT_COMMIT}" \
                        HARBOR_MAX_ALLOWED_SEVERITY="${HARBOR_MAX_ALLOWED_SEVERITY}" \
                          node scripts/verify-harbor-scan.mjs
                        docker logout "${HARBOR_REGISTRY}"
                    '''
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}
