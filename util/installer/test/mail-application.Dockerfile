FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    openjdk-25-jdk-headless python3 python3-aiosmtpd openssl ca-certificates
WORKDIR /repo
