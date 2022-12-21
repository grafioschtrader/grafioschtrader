#!/bin/bash
. ~/gtvar.sh
./checkversion.sh
if [ $? -ne 0 ]; then
  exit 1
fi
sudo systemctl stop grafioschtrader.service
cd $builddir
GT_PROF=application.properties
GT_PROF_PROD=application-production.properties
GT_PROF_PATH=grafioschtrader/backend/grafioschtrader-server/src/main/resources
cp $GT_PROF_PATH/$GT_PROF .
if [ -e  $GT_PROF_PATH/$GT_PROF_PROD ]
   then cp $GT_PROF_PATH/$GT_PROF_PROD .
fi
cd grafioschtrader/
rm -fr frontend
git reset --hard origin/master
git pull --rebase
cd $builddir
if [ -f $GT_PROF ]; then
   mv $GT_PROF_PATH/$GT_PROF ${GT_PROF}.new
   ~/merger.sh -i $GT_PROF -s ${GT_PROF}.new -o $GT_PROF_PATH/$GT_PROF
fi
if [ -f $GT_PROD ]; then
  cp $GT_PROF_PROD $GT_PROF_PATH/.
fi
cd ~
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh .
cp $builddir/grafioschtrader/util/shellscripts/checkversion.sh .
~/gtupfrontback.sh
