#!/bin/bash
. ~/gtvar.sh
cd $builddir/grafioschtrader/frontend
npm install
memorytotal="$(free -m | awk '/^Mem|Speicher/ { print $2}')"
if (($memorytotal>=4000 && memorytotal<=4048))
  then
    sudo systemctl stop grafioschtrader.service
    export NODE_OPTIONS="--max_old_space_size=4071"
  fi
if [ $memorytotal -lt 4000 ]
   then
    cd $builddir/grafioschtrader/frontend
    wget https://github.com/grafioschtrader/grafioschtrader/releases/download/Latest/latest.tar.gz
    tar -xf latest.tar.gz
    cd
   else
    ng build --configuration production --base-href /$basehref
   fi
rm -r $docroot/${basehref}assets
rm $docroot/${basehref}*
