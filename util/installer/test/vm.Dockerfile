FROM ubuntu:24.04
RUN apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    qemu-system-x86 qemu-utils cloud-image-utils openssh-client curl ca-certificates \
    git python3 bats shellcheck
WORKDIR /work
