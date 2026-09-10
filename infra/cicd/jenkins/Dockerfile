FROM node:24-bookworm-slim AS node

FROM jenkins/jenkins:lts-jdk21

USER root
COPY --from=node /usr/local/ /usr/local/

USER jenkins
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY --chown=jenkins:jenkins jenkins.yaml /usr/share/jenkins/ref/jenkins.yaml

ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/jenkins.yaml
ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
