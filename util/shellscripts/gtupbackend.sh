#!/bin/bash
. ~/gtvar.sh
sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
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
