# CodeBuild Custom Image with Java 21 + SAM CLI
FROM public.ecr.aws/amazonlinux/amazonlinux:2023

# Install basic dependencies
RUN dnf update -y && \
    dnf install -y \
    git \
    tar \
    gzip \
    unzip \
    which \
    findutils \
    python3 \
    python3-pip \
    docker \
    && dnf clean all

# Install Amazon Corretto 21 (Java 21)
RUN rpm --import https://yum.corretto.aws/corretto.key && \
    curl -L -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo && \
    dnf install -y java-21-amazon-corretto-devel && \
    dnf clean all

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH=$JAVA_HOME/bin:$PATH

# Install AWS CLI v2
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install && \
    rm -rf aws awscliv2.zip

# Install SAM CLI (the main optimization!)
RUN pip3 install aws-sam-cli

# Install Gradle (optional, project uses wrapper)
ENV GRADLE_VERSION=8.5
RUN curl -L https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o gradle.zip && \
    unzip gradle.zip -d /opt && \
    rm gradle.zip && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/bin/gradle

# Verify installations
RUN java -version && \
    aws --version && \
    sam --version && \
    gradle --version

# Set working directory
WORKDIR /codebuild/output/src

# Labels
LABEL maintainer="group2-englishstudy" \
      description="CodeBuild image with Java 21, SAM CLI, and Gradle pre-installed"
