#!/bin/bash
. ~/gtvar.sh
sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
sed -i 's/org.hibernate.dialect.MySQL5InnoDBDialect/org.hibernate.dialect.MariaDBDialect/' grafioschtrader-server/src/main/resources/application.properties
mvn clean install -Dmaven.test.skip=true
mvn package -Dmaven.test.skip=true
rm -f ~/grafioschtrader*.jar
cp grafioschtrader-server/target/grafioschtrader*.jar ~/.
sudo systemctl start grafioschtrader.service
