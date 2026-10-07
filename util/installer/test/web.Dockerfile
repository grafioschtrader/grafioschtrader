FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    nginx apache2 certbot curl python3 python3-aiosmtpd iproute2 bats shellcheck git openssl mariadb-client sudo dnsutils
WORKDIR /repo
