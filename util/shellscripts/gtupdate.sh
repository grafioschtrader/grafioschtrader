#!/bin/bash
. ~/gtvar.sh
./checkversion.sh
if [ $? -ne 0 ]; then
  exit 1
fi
sudo systemctl stop grafioschtrader.service
cd $builddir
cp grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties .
cd grafioschtrader
rm -fr frontend
git reset --hard origin/master
git pull --rebase
cd $builddir
if [ -f application.properties ]; then
        mv grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties application.properties.new
        ~/merger.sh -i application.properties -s application.properties.new -o grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties
fi
cd ~
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh .
cp $builddir/grafioschtrader/util/shellscripts/checkversion.sh .
~/gtupfrontback.sh
