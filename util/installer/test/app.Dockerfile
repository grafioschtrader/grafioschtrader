FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    systemd systemd-sysv logrotate sudo bats shellcheck python3 mariadb-client \
    git curl openssl procps iproute2 wget tzdata
WORKDIR /repo
