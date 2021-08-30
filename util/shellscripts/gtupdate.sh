#!/bin/bash
./checkversion.sh
if [ $? -ne 0 ]; then
  exit 1
fi
sudo systemctl stop grafioschtrader.service
. ~/gtvar.sh
cd $builddir
cp grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties .
cd grafioschtrader
git reset --hard origin/master
git pull
cd $builddir
if [ -f application.properties ]; then
        mv grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties application.properties.new
        ~/merger.sh -i application.properties -s application.properties.new -o grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties
fi
cd ~
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh .
~/gtupfrontback.sh
