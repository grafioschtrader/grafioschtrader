#!/bin/bash
. ~/gtvar.sh
cd $builddir/grafioschtrader/frontend
npm install
memorytotal="$(free -m | awk '/^Mem/ { print $2}')"
if [ $memorytotal -lt 2001 ]
  then
    sudo systemctl stop grafioschtrader.service
    NODE_OPTIONS=--max_old_space_size=1500
  fi
ng build --prod --base-href /$basehref --deploy-url /$basehref
rm -r $docroot/${basehref}assets
rm $docroot/${basehref}*
cp -r $builddir/grafioschtrader/frontend/dist/* $docroot/$basehref
 
