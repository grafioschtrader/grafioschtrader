#!/bin/bash
echo Build backend and frontend, only output of frontend is shown
memorytotal="$(free -m | awk '/^Mem|Speicher/ { print $2}')"
. ~/gtvar.sh
cd ~
if [ $memorytotal -gt 6000 ]
  then
    ~/gtupfrontend.sh &
    ~/gtupbackend.sh &> $builddir/backbuild.log &
    wait
    cat $builddir/backbuild.log
  else
    sudo systemctl stop grafioschtrader.service
    ~/gtupfrontend.sh
    ~/gtupbackend.sh
  fi
