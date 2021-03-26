#!/bin/bash
. ~/gtvar.sh
sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
mvn clean install -Dmaven.test.skip=true
mvn package -Dmaven.test.skip=true
cp grafioschtrader-server/target/grafioschtrader*.jar ~/.
sudo systemctl start grafioschtrader.service
