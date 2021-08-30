CLIVERSION=`npm list -global --depth 0 | grep "@angular/cli" | cut -d "@" -f3 | cut -d "." -f1`
if [[ -z "$CLIVERSION" ||  "$CLIVERSION" -lt 12 ]]
  then
    . ~/gtvar.sh
    rm -rf $builddir/grafioschtrader/frontend/node_modules
    rm -f $builddir/grafioschtrader/frontend/package-lock.json
    tput setaf 1
    echo ==========================================================
    echo "Run as root"
    echo "  npm uninstall -g @angular/cli"
    echo "  npm install -g @angular/cli@^12"
    echo ==========================================================
    tput sgr 0
    exit 1
  fi
exit 0
