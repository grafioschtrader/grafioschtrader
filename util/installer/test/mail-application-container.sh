#!/bin/bash
# Real Boot launcher, Jasypt configuration and local SMTP; no application context or database.
set -Eeuo pipefail
[[ $EUID == 0 && -f /.dockerenv && ! -e /var/lib/gt-install ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset; gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap 'if [[ -n ${mail_pid:-} ]]; then kill "$mail_pid" 2>/dev/null || :; fi; gt_cleanup' EXIT
useradd --create-home --shell /bin/bash grafioschtrader
bash /repo/util/installer/test/test-certificates.sh
STATE[java_home]=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
STATE[run_id]=00000000000000000000000000000001 STATE[step.app]=complete
fixture_jar=$(find /repo/backend/grafiosch-test-integration/target -maxdepth 1 \
  -name 'grafiosch-test-integration-*.jar' -print -quit)
[[ -n "$fixture_jar" ]]
python3 /repo/util/installer/test/mail-application-fixture.py "$fixture_jar" \
  "$CORE_HOME/grafioschtrader-server-fixture.jar"
chown grafioschtrader:grafioschtrader "$CORE_HOME/grafioschtrader-server-fixture.jar"
ANSWER[SMTP_HOST]=localhost ANSWER[SMTP_PORT]=2525 ANSWER[SMTP_USER]=sender@example.test
ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_TEST]=yes
ANSWER[ADMIN_EMAIL]=admin@example.test ANSWER[SMTP_CONFIGURE]=yes
SECRET[JASYPT_PASSWORD]=fixture-key SECRET[SMTP_PASSWORD]=deliberately-wrong-journal-password
printf %s fixture-smtp-password > "$SCRATCH/password"
python3 /repo/util/installer/test/mail-fixture.py "$SCRATCH" > "$SCRATCH/server.log" 2>&1 &
mail_pid=$!
for ((i=0;i<50;i++)); do [[ ! -e "$SCRATCH/ready" ]] || break; sleep .1; done
[[ -e "$SCRATCH/ready" ]]
gt_mail_probe
grep -qx accepted "$SCRATCH/mail-result"
grep -q '^Date: ' "$SCRATCH/messages"
[[ $(wc -l < "$SCRATCH/count") == 1 ]]
# Valid SMTP credentials in the journal cannot mask a wrong application decryption key.
SECRET[SMTP_PASSWORD]=fixture-smtp-password SECRET[JASYPT_PASSWORD]=wrong-key
if gt_mail_probe; then exit 1; fi
[[ ! -e "$SCRATCH/mail-result" && $(wc -l < "$SCRATCH/count") == 1 ]]
SECRET[JASYPT_PASSWORD]=fixture-key
# External production settings take precedence, just as for the service.
printf 'spring.mail.properties.mail.smtp.starttls.required=false\n' > "$CORE_HOME/application-production.properties"
chmod 644 "$CORE_HOME/application-production.properties"
if gt_mail_probe; then exit 1; fi
[[ $(wc -l < "$SCRATCH/count") == 1 ]]
rm "$CORE_HOME/application-production.properties"
gt_mail_probe no
grep -qx connected "$SCRATCH/mail-result"
[[ $(wc -l < "$SCRATCH/count") == 1 ]]
echo 'PASS: deployed JAR configuration, production override, real decryption, SMTP and no-send; no DB/server startup.'
