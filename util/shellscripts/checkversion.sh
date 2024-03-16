. ~/gtvar.sh
GTVERSION=`grep -Eo '"@angular/cli": ".*"' $builddir/grafioschtrader/frontend/package.json | sed -En 's/[^0-9]*([0-9]*).*/\1/p'`
CLIVERSION=`npm list -global --depth 0 | grep "@angular/cli" | cut -d "@" -f3 | cut -d "." -f1`
if [ -z "$GTVERSION" ]
then
    echo ==========================================================
    echo GT Client is missing
    echo ==========================================================
	exit 1
fi
if [[ -z "$CLIVERSION" ||  "$CLIVERSION" -lt "$GTVERSION" ]]
  then
    tput setaf 1
    echo ==========================================================
    echo "Run as root"
    echo "  npm uninstall -g @angular/cli"
    echo "  npm install -g @angular/cli@^${GTVERSION}"
    echo ==========================================================
    tput sgr 0
    exit 1
  fi
exit 0
