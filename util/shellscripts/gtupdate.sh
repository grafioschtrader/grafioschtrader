#!/bin/bash
sudo systemctl stop grafioschtrader.service
. ~/gtvar.sh
cd $builddir
cp grafioschtrader/backend/grafioschtrader-server/src/main/resources/application.properties .
cd grafioschtrader
# git reset --hard HEAD
git pull
echo Build backend and frontend, only output of frontend is shown
cd ~
{
~/gtupfrontend.sh
~/gtupbackend.sh &> $builddir/backbuild.log
} &
wait
cat $builddir/backbuild.log





