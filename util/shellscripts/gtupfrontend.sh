#!/bin/bash
. ~/gtvar.sh
cd $builddir/grafioschtrader/frontend
npm install
ng build --prod --base-href /$basehref --deploy-url /$basehref
rm -r $docroot/${basehref}assets
rm $docroot/${basehref}*
cp -r $builddir/grafioschtrader/frontend/dist/* $docroot/$basehref
 

