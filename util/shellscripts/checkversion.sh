#!/bin/bash

# Source the environment variables
. ~/gtvar.sh

# Required Node.js versions
node_required="^18.19.1 || ^20.11.1 || ^22.0.0"

# Required Angular CLI version
angular_cli_required=19

# Function to check and install semver if missing
ensure_semver_installed() {
    if ! command -v npx >/dev/null 2>&1 || ! npx semver --help >/dev/null 2>&1; then
        tput setaf 3
        echo "=========================================================="
        echo "'semver' is not installed. Installing it now..."
        echo "=========================================================="
        tput sgr0
        npm install -g semver
        if [ $? -ne 0 ]; then
            tput setaf 1
            echo "=========================================================="
            echo "Failed to install 'semver'. Please install it manually with:"
            echo "  npm install -g semver"
            echo "=========================================================="
            tput sgr0
            exit 1
        fi
    fi
}

# Ensure semver is installed
ensure_semver_installed

# Check if Node.js is installed
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

# Check Node.js version with semver
if npx semver "$NODEVERSION" -r "$node_required" >/dev/null 2>&1; then
    echo "Node.js version ($NODEVERSION) meets the required version: $node_required"
else
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

# Check Angular CLI version
CLIVERSION=$(npm list -global --depth 0 | grep "@angular/cli" | cut -d "@" -f3 | cut -d "." -f1)

if [ -z "$CLIVERSION" ]; then
    tput setaf 1
    echo "=========================================================="
    echo "Angular CLI is not installed."
    echo "Run as root to install Angular CLI version $angular_cli_required:"
    echo "  npm install -g @angular/cli@$angular_cli_required"
    echo "=========================================================="
    tput sgr0
    exit 1
fi

if [[ "$CLIVERSION" -lt "$angular_cli_required" ]]; then
    tput setaf 1
    echo "=========================================================="
    echo "Angular CLI version ($CLIVERSION) is less than the required version ($angular_cli_required)."
    echo "Run as root to update Angular CLI:"
    echo "  npm uninstall -g @angular/cli"
    echo "  npm install -g @angular/cli@$angular_cli_required"
    echo "=========================================================="
    tput sgr0
    exit 1
fi

echo "All checks passed!"
exit 0
