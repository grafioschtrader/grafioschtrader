FROM debian:12
# Debian 12 offers no openjdk-25 in APT, so JDK 25 must come from a verified vendor archive. The shared Java 17
# must keep its system selections.
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    openjdk-17-jdk-headless openssl python3 git curl ca-certificates iproute2 procps dnsutils sudo psmisc
WORKDIR /repo
