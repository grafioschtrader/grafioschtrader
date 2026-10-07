#!/bin/bash
# QEMU controller inside a disposable Docker container; repository is read-only.
set -euo pipefail
[[ -f /.dockerenv && -c /dev/kvm && $EUID == 0 && -d /repo/.git ]]
# Keep the running controller stable while the host checkout is being edited.
if [[ "${BASH_SOURCE[0]}" != /work/vm-host-run.sh ]]; then
  cp -- "${BASH_SOURCE[0]}" /work/vm-host-run.sh
  exec bash /work/vm-host-run.sh "$@"
fi
cd /work
umask 077
GT_VM_WEB=${GT_VM_WEB:-nginx}
GT_VM_DOMAIN=${GT_VM_DOMAIN:-no}
GT_VM_MODE=${GT_VM_MODE:-bootstrap}
GT_VM_OS=${GT_VM_OS:-ubuntu-24.04}
[[ "$GT_VM_WEB" == nginx || "$GT_VM_WEB" == apache2 ]]
[[ "$GT_VM_DOMAIN" == yes || "$GT_VM_DOMAIN" == no ]]
[[ "$GT_VM_MODE" == stages || "$GT_VM_MODE" == bootstrap && "$GT_VM_DOMAIN" == no ]]
case "$GT_VM_OS" in
  ubuntu-24.04)
    image_base=https://cloud-images.ubuntu.com/noble/current image=noble-server-cloudimg-amd64.img
    sums=SHA256SUMS sum_tool=sha256sum guest_user=ubuntu ;;
  # Debian 12 has no APT JDK 25, so the installer must use the vendor archive. The stage driver installs
  # Java/Maven from APT itself; this release therefore runs in bootstrap mode only.
  debian-12)
    [[ "$GT_VM_MODE" == bootstrap ]]
    image_base=https://cloud.debian.org/images/cloud/bookworm/latest image=debian-12-genericcloud-amd64.qcow2
    sums=SHA512SUMS sum_tool=sha512sum guest_user=debian ;;
  *) exit 2 ;;
esac
# A controller keeps one guest disk; another release needs its own controller container.
if [[ -e guest-os && "$(cat guest-os)" != "$GT_VM_OS" ]]; then
  echo "This controller holds a $(cat guest-os) guest; use a new container for $GT_VM_OS." >&2
  exit 2
fi
mkdir -p results
rm -f results/PASS
git -c safe.directory=/repo -C /repo rev-parse HEAD > results/base-commit
ssh_guest() {
  ssh -i /work/guest-key -p 2222 -o BatchMode=yes -o ConnectTimeout=5 \
    -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/work/known-hosts "$guest_user@127.0.0.1" "$@"
}
if [[ ! -e guest.qcow2 ]]; then
  curl --fail --silent --show-error --location --retry 3 --connect-timeout 15 --max-time 60 \
    "$image_base/$sums" -o "$sums"
  curl --fail --silent --show-error --location --retry 3 --retry-all-errors --continue-at - \
    --connect-timeout 15 --speed-limit 1024 --speed-time 60 \
    "$image_base/$image" -o base.img
  awk -v image="$image" '$2 == "*" image || $2 == image {print $1 "  base.img"; found=1} END {exit !found}' \
    "$sums" > base.sum
  "$sum_tool" --check base.sum
  printf '%s\n' "$GT_VM_OS" > guest-os
  qemu-img create -f qcow2 -F qcow2 -b /work/base.img guest.qcow2 60G
  ssh-keygen -q -t ed25519 -N '' -f guest-key
  {
    printf '#cloud-config\nssh_authorized_keys:\n  - %s\n' "$(cat guest-key.pub)"
    printf 'write_files:\n  - path: /etc/gt-installer-acceptance\n    content: disposable-qemu\n'
  } > user-data
  printf 'instance-id: gt-installer-acceptance\nlocal-hostname: gt-installer-acceptance\n' > meta-data
  cloud-localds seed.img user-data meta-data
fi
if [[ ! -e qemu.pid ]] || ! kill -0 "$(cat qemu.pid)" 2>/dev/null; then
  qemu-system-x86_64 -enable-kvm -cpu host -smp 8 -m 12288 -display none \
    -drive file=/work/guest.qcow2,if=virtio,format=qcow2 -drive file=/work/seed.img,format=raw,if=virtio \
    -netdev user,id=net0,hostfwd=tcp:127.0.0.1:2222-:22 -device virtio-net-pci,netdev=net0 \
    -serial file:/work/results/console.log -monitor none -daemonize -pidfile /work/qemu.pid
fi
for ((attempt=0; attempt<120; attempt++)); do
  if ssh_guest true 2>/dev/null; then break; fi
  sleep 3
done
ssh_guest 'sudo cloud-init status --wait'
if [[ "$GT_VM_MODE" == stages ]] && ! ssh_guest 'test -d /opt/gt-acceptance/source/.git'; then
  mkdir snapshot
  git -c safe.directory=/repo -C /repo archive HEAD | tar -x -C snapshot
  # Only installer-owned changes are overlaid; unrelated work in progress is excluded.
  cp -a /repo/util/installer snapshot/util/
  cp /repo/util/shellscripts/{gtupbackend,gtupfrontend,gtupfrontback,checkversion,grafioschtrader,installroot}.sh snapshot/util/shellscripts/
  cp /repo/backend/grafioschtrader-server/src/main/resources/application.properties snapshot/backend/grafioschtrader-server/src/main/resources/
  git -C snapshot init -q -b master
  git -C snapshot add .
  git -C snapshot -c user.name=InstallerAcceptance -c user.email=installer@example.invalid commit -qm 'Isolated installer acceptance snapshot'
  git -C snapshot rev-parse HEAD > results/source-commit
  git -c safe.directory=/repo -C /repo rev-parse HEAD > results/base-commit
  tar -czf source.tar.gz -C snapshot .
  ssh_guest 'sudo mkdir -p /opt/gt-acceptance/source && sudo chmod 755 /opt/gt-acceptance /opt/gt-acceptance/source'
  ssh_guest 'sudo tar -xzf - -C /opt/gt-acceptance/source && sudo chmod -R a+rX /opt/gt-acceptance/source' < source.tar.gz
fi
ssh_guest 'sudo mkdir -p /opt/gt-acceptance'
# Only the stage mode uses the local source snapshot; bootstrap clones the public repository, and a fresh Debian
# cloud image has no git before the guest driver installs it.
if [[ "$GT_VM_MODE" == stages ]]; then
  ssh_guest 'sudo git config --system --replace-all safe.directory /opt/gt-acceptance/source && sudo git config --system --add safe.directory /opt/gt-acceptance/source/.git'
fi
ssh_guest 'sudo tee /opt/gt-acceptance/installer.sh >/dev/null' < /repo/util/installer/gt-install.sh
sha256sum /repo/util/installer/gt-install.sh > results/installer.sha256
ssh_guest 'sudo tee /opt/gt-acceptance/guest.sh >/dev/null' < /repo/util/installer/test/vm-guest.sh
guest_script='sudo bash /opt/gt-acceptance/guest.sh'
if [[ "$GT_VM_MODE" == bootstrap ]]; then
  ssh_guest 'sudo tee /opt/gt-acceptance/bootstrap.sh >/dev/null' < /repo/util/installer/test/vm-bootstrap.sh
  guest_script='sudo bash /opt/gt-acceptance/bootstrap.sh'
  ssh_guest "$guest_script prepare $GT_VM_WEB" > results/prepare.log 2>&1
  ssh_guest "$guest_script install" >> results/bootstrap.log 2>&1
else
if ! ssh_guest 'sudo grep -qx step.core=complete /var/lib/gt-install/state'; then
  ssh_guest "$guest_script core $GT_VM_WEB $GT_VM_DOMAIN" > results/core.log 2>&1
fi
ssh_guest "$guest_script application" > results/application.log 2>&1
ssh_guest "$guest_script web" > results/web.log 2>&1
fi
ssh_guest 'sudo systemctl reboot' || true
sleep 10
for ((attempt=0; attempt<120; attempt++)); do
  if ssh_guest true 2>/dev/null; then break; fi
  sleep 3
done
ssh_guest "$guest_script reboot-check" > results/reboot.log 2>&1
backend_http=9090
[[ "$GT_VM_WEB" != apache2 ]] || backend_http=8080
ssh_guest "uname -r; systemctl is-active mariadb grafioschtrader $GT_VM_WEB; curl -fsS http://127.0.0.1:$backend_http/api/gtinfo" > results/runtime.txt
ssh_guest 'sudo cat /var/lib/gt-install/app-build.log' > results/build.log
ssh_guest 'sudo journalctl -u grafioschtrader.service --no-pager' > results/service.log
if [[ "$GT_VM_MODE" == bootstrap ]]; then
  ssh_guest 'sudo cat /root/gt-bootstrap-acceptance/initial.log' > results/initial.log
  ssh_guest 'sudo cat /root/gt-bootstrap-acceptance/resumed.log' > results/resumed.log
  ssh_guest 'sudo cat /var/lib/gt-install/result' > results/result.txt
  ssh_guest "sudo sed -n 's/^built_commit=//p' /var/lib/gt-install/state" > results/source-commit
fi
sha256sum base.img > results/cloud-image.sha256
printf '%s\n' "$GT_VM_OS" > results/guest-os
printf 'PASS: %s, mode=%s, real build, %s LAN access, domain TLS=%s, systemd startup, reboot and resume.\n' \
  "$GT_VM_OS" "$GT_VM_MODE" "$GT_VM_WEB" "$GT_VM_DOMAIN" | tee results/PASS
ssh_guest 'sudo systemctl poweroff' || true
