FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    openjdk-25-jdk-headless maven mariadb-client bats shellcheck openssl python3 \
    git curl iproute2 procps dnsutils sudo psmisc
WORKDIR /repo
