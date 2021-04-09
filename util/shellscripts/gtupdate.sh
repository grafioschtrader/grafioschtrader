#!/bin/bash
sudo systemctl stop grafioschtrader.service
. ~/gtvar.sh
cd $builddir
cp grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties .
cd grafioschtrader
git reset --hard origin/master
git pull
cd ..
mv grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties application.properties.new
~/merger.sh -i application.properties -s application.properties.new -o grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties
echo Build backend and frontend, only output of frontend is shown
cd ~
~/gtupfrontend.sh &
~/gtupbackend.sh &> $builddir/backbuild.log &
wait
cat $builddir/backbuild.log





