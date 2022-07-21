#!/bin/bash
. ~/gtvar.sh
cd $builddir/grafioschtrader/frontend
npm install
memorytotal="$(free -m | awk '/^Mem|Speicher/ { print $2}')"
if (($memorytotal>=3700 && memorytotal<=4048))
  then
    sudo systemctl stop grafioschtrader.service
    export NODE_OPTIONS="--max_old_space_size=4071"
  fi
if [ $memorytotal -lt 3700 ]
   then
    cd $builddir/grafioschtrader/frontend
    rm -f latest.tar.gz
    wget https://github.com/grafioschtrader/grafioschtrader/releases/download/Latest/latest.tar.gz
    tar -xf latest.tar.gz
    cd
   else
    echo "n n" | ng build --configuration production --base-href /$basehref
   fi
rm -rf $docroot/${basehref}assets
rm -f $docroot/${basehref}*
cp -r $builddir/grafioschtrader/frontend/dist/* $docroot/$basehref
