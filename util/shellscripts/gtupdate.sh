#!/bin/bash
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
        ~/merger.sh -i application.properties -s application.properties.new -o grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.propert
fi
cd ~
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh .
~/gtupfrontback.sh
