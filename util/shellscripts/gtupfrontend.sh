#!/bin/bash
. ~/gtvar.sh
cd $builddir/grafioschtrader/frontend
npm install
memorytotal="$(free -m | awk '/^Mem|Speicher/ { print $2}')"
if [ $memorytotal -lt 2001 ]
  then
    sudo systemctl stop grafioschtrader.service
    export NODE_OPTIONS="--max_old_space_size=1900"
  fi
ng build --configuration production --base-href /$basehref 
rm -r $docroot/${basehref}assets
rm $docroot/${basehref}*
cp -r $builddir/grafioschtrader/frontend/dist/* $docroot/$basehref
 
