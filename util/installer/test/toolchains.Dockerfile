FROM ubuntu:24.04
# An older shared JDK must keep its system selections when the installer adds JDK 25.
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    openjdk-17-jdk-headless bats shellcheck openssl python3 mariadb-client \
    git curl iproute2 procps dnsutils sudo psmisc
WORKDIR /repo
