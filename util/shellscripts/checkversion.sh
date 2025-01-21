#!/bin/bash

# Source the environment variables
. ~/gtvar.sh

# Required Node.js versions
node_required="^18.19.1 || ^20.11.1 || ^22.0.0"

# Check Angular CLI version
GTVERSION=$(grep -Eo '"@angular/cli": ".*"' $builddir/grafioschtrader/frontend/package.json | sed -En 's/[^0-9]*([0-9]*).*/\1/p')
CLIVERSION=$(npm list -global --depth 0 | grep "@angular/cli" | cut -d "@" -f3 | cut -d "." -f1)

if [ -z "$GTVERSION" ]; then
    echo "=========================================================="
    echo "GT Client is missing"
    echo "=========================================================="
    exit 1
fi

if [[ -z "$CLIVERSION" || "$CLIVERSION" -lt "$GTVERSION" ]]; then
    tput setaf 1
    echo "=========================================================="
    echo "Run as root to update Angular CLI:"
    echo "  npm uninstall -g @angular/cli"
    echo "  npm install -g @angular/cli@^${GTVERSION}"
    echo "=========================================================="
    tput sgr0
    exit 1
fi

# Check Node.js version
NODEVERSION=$(node -v 2>/dev/null | sed 's/v//')
if [ -z "$NODEVERSION" ]; then
    tput setaf 1
    echo "=========================================================="
    echo "Node.js is not installed or not available in the PATH."
    echo "Run the following commands as root to install Node.js:"
    echo "  npm install -g n"
    echo "  n stable"
    echo "=========================================================="
    tput sgr0
    exit 1
fi

if ! npx semver "$NODEVERSION" -r "$node_required" >/dev/null 2>&1; then
    tput setaf 1
    echo "=========================================================="
    echo "Node.js version ($NODEVERSION) does not meet the required version: $node_required"
    echo "Run the following commands as root to update Node.js:"
    echo "  npm install -g n"
    echo "  n stable"
    echo "=========================================================="
    tput sgr0
    exit 1
fi

echo "All checks passed!"
exit 0
