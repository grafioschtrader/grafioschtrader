#!/bin/bash
. ~/gtvar.sh
sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
# Give this installation its own time slot for the daily download of prices and dividends, so
# that not every GT instance world-wide queries the free data providers in the same minute. It
# only fires while the schedule is still untouched, so a time set by hand is never overwritten.
# Run before "mvn package": the properties are packaged into the jar. GT_CRON_RANDOMIZE=off
# disables it. The script is taken from the freshly updated clone rather than from ~, and is
# started through bash because the checkout may not carry the executable bit.
bash "$builddir/grafioschtrader/util/shellscripts/gtcronrandom.sh" \
  --file grafioschtrader-server/src/main/resources/application.properties \
  || echo "WARNING: the cron randomization failed - building with the configured times"
# Remove GT artifacts accumulated in the local Maven repository by former "mvn install" runs
# (one ~150 MB fat jar per version); the reactor build below does not need them.
rm -rf ~/.m2/repository/grafioschtrader
# -DskipTests, not -Dmaven.test.skip=true: grafioschtrader-server depends on the test-jar of
# grafiosch-server-base in test scope. maven.test.skip suppresses that artifact while Maven still
# resolves the test classpath, so the reactor aborts with
# "Could not find artifact grafiosch-server-base:jar:tests". Tests are compiled but not run.
mvn clean package -DskipTests
rm -f ~/grafioschtrader*.jar
cp grafioschtrader-server/target/grafioschtrader*.jar ~/.
sudo systemctl start grafioschtrader.service
