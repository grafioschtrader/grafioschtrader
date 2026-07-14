#!/bin/bash
. ~/gtvar.sh
sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
# Remove GT artifacts accumulated in the local Maven repository by former "mvn install" runs
# (one ~150 MB fat jar per version); the reactor build below does not need them.
rm -rf ~/.m2/repository/grafioschtrader
mvn clean package -Dmaven.test.skip=true
rm -f ~/grafioschtrader*.jar
cp grafioschtrader-server/target/grafioschtrader*.jar ~/.
sudo systemctl start grafioschtrader.service
