#!/bin/bash
# DESTRUCTIVE TEST FIXTURE: dedicated, fresh Docker container only; never a real host.
# Test secrets deliberately contain literal shell/Maven variable syntax.
# shellcheck disable=SC2016
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install && ! -e /home/grafioschtrader ]]
export GT_INSTALL_SOURCE_ONLY=1
source util/installer/gt-install.sh
gt_reset; gt_question_model
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
umask 077
mkdir -p /var/lib/gt-install /root/.gt-install
chmod 700 /var/lib/gt-install /root/.gt-install
fixture=$(mktemp -d /tmp/gt-core-source.XXXXXX)
chmod 755 "$fixture"
mkdir -p "$fixture/backend/grafioschtrader-server/src/main/resources"
cp backend/grafioschtrader-server/src/main/resources/application.properties "$fixture/backend/grafioschtrader-server/src/main/resources/"
: > "$fixture/backend/grafioschtrader-server/src/main/resources/application-production.properties"
plugin_version=$(awk '/<artifactId>jasypt-maven-plugin<\/artifactId>/ {getline; sub(/.*<version>/, ""); sub(/<.*/, ""); print; exit}' backend/grafioschtrader-server/pom.xml)
cat > "$fixture/backend/grafioschtrader-server/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>test</groupId><artifactId>installer-crypto</artifactId><version>1</version>
<build><plugins><plugin><groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-maven-plugin</artifactId>
<version>$plugin_version</version></plugin></plugins></build></project>
XML
git -C "$fixture" init -q -b master
git -C "$fixture" add .
git -C "$fixture" -c user.name=InstallerTest -c user.email=installer@example.invalid commit -qm fixture
FACT[source.commit]=$(git -C "$fixture" rev-parse HEAD)
FACT[java.suitable]=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
FACT[maven.path]=$(command -v mvn)
FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent FACT[memory.MemTotal]=2048
ANSWER[ADMIN_EMAIL]=admin@example.org ANSWER[ALLOWED_USERS]=20 ANSWER[WEBSERVER]=nginx ANSWER[BACKEND_PORT]=9090
ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_HOST]=smtp.example.org ANSWER[SMTP_PORT]=587
ANSWER[SMTP_USER]=sender@example.org ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_TEST]=no
ANSWER[BUFFER_POOL]=no ANSWER[DOMAIN]='' ANSWER[JAVA_HEAP]='-Xms128m -Xmx896m' ANSWER[DOCROOT]=/var/www/gt
root_fixture='fake-root-only-" '\'' \ $HOME = tail  '
SECRET[DB_ROOT_PASSWORD]=$root_fixture
SECRET[DB_PASSWORD]=' DEC(fake-db-${env.HOME}) \ " '\'' $HOME = tail  '
SECRET[JASYPT_PASSWORD]='fake-key-only-$HOME-" '\'' \ = tail'
SECRET[SMTP_PASSWORD]=' DEC(fake-mail-${env.PATH}) \ " '\'' = tail  '
SECRET[JWT_SECRET]=$(openssl rand -hex 24)
gt_core_begin
saved_secrets=$(cat /root/.gt-install/secrets)
[[ "$saved_secrets" != *DB_ROOT_PASSWORD* && "$saved_secrets" != *"$root_fixture"* ]]
gt_core_user
chown -R grafioschtrader:grafioschtrader "$fixture"
chmod -R a+rX "$fixture"
CORE_REMOTE=$fixture
gt_core_clone

# Docker has no systemd; only the service adapter is substituted. APT, MariaDB, user
# creation, Git, filesystem permissions and Maven all run for real in this container.
gt_core_run() {
  if [[ "$1" == systemctl ]]; then
    # The package's offline-enabled mariadb.socket never listens without systemd; the server below binds loopback.
    [[ "$*" != 'systemctl disable --now mariadb.socket' ]] || return 0
    [[ "$2" =~ ^(start|restart)$ && "$3" == mariadb.service ]] || return 2
    if ! mariadb --no-defaults -NBe 'SELECT 1' >/dev/null 2>&1; then
      install -d -o mysql -g mysql /run/mysqld
      mariadbd --user=mysql --bind-address=127.0.0.1 > /tmp/gt-mariadb-test.log 2>&1 &
      for ((attempt=0; attempt<60; attempt++)); do
        if mariadb --no-defaults -NBe 'SELECT 1' >/dev/null 2>&1; then return 0; fi
        sleep 1
      done
      return 2
    fi
  else "$@"; fi
}

# Simulate a crash after CREATE USER/GRANT, before committing ownership to the journal.
gt_core_mark() {
  [[ "$1:$2" != resource.account:owned ]] || return 2
  STATE[$1]=$2; gt_state_save
}
if gt_core_database; then echo 'Expected interrupted account step' >&2; exit 1; fi
[[ "$(mariadb --no-defaults -NBe "SELECT COUNT(*) FROM mysql.user WHERE User='grafioschtrader';")" == 1 ]]
SECRET=() STATE=() ANSWER=()
gt_state_load; gt_secrets_load
[[ -z "${SECRET[DB_ROOT_PASSWORD]:-}" && "${STATE[resource.account]}" == intent ]]
gt_core_mark() { STATE[$1]=$2; gt_state_save; }
SECRET[DB_ROOT_PASSWORD]=$root_fixture
gt_core_database
[[ "$(cat /root/.gt-install/secrets)" == "$saved_secrets" ]]
SECRET[DB_ROOT_PASSWORD]='wrong-test-only'
if gt_core_root_auth; then echo 'Incorrect root password was accepted' >&2; exit 1; fi
SECRET[DB_ROOT_PASSWORD]=$root_fixture
gt_core_root_auth
printf 'PASS: MariaDB root password, preserved socket login, wrong-password rejection and interrupted account recovery.\n'

gt_core_configure
gt_core_config_valid
config_hash=$(sha256sum "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties")
gt_core_configure
[[ "$(sha256sum "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties")" == "$config_hash" ]]
for secret_key in DB_PASSWORD JASYPT_PASSWORD SMTP_PASSWORD JWT_SECRET DB_ROOT_PASSWORD; do
  contents=$(cat /var/lib/gt-install/state "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/"application*.properties)
  [[ "$contents" != *"${SECRET[$secret_key]}"* ]]
done
grep -qx 'server.address=127.0.0.1' "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application-production.properties"
grep -qx 'spring.mail.properties.mail.smtp.starttls.required=true' "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application-production.properties"
printf 'PASS: real Jasypt encryption/decryption with literal special characters, protected configuration and idempotent resume.\n'

# Drift and lost secrets must stop recovery without replacing the original values.
echo '# external edit' >> "$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties"
if gt_core_configure; then echo 'Modified configuration was accepted' >&2; exit 1; fi
mv /root/.gt-install/secrets /root/.gt-install/secrets.saved
if gt_secrets_load; then echo 'Missing original secrets were accepted' >&2; exit 1; fi
mv /root/.gt-install/secrets.saved /root/.gt-install/secrets
printf 'PASS: configuration drift and missing secrets block recovery.\n'
