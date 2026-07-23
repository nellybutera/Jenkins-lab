FROM jenkins/jenkins:lts-jdk17

USER root

# Maven 3.9.16 pinned to match the version this lab's suite was validated against locally.
# Installed as a plain tarball on PATH (not `apt-get install maven`) to avoid Debian's older
# Maven build and the extra JDK apt would pull in as a dependency.
ENV MAVEN_VERSION=3.9.16
ENV MAVEN_HOME=/opt/maven
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz && \
    tar -xzf /tmp/maven.tar.gz -C /opt && \
    mv "/opt/apache-maven-${MAVEN_VERSION}" "${MAVEN_HOME}" && \
    rm /tmp/maven.tar.gz
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

USER jenkins

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
