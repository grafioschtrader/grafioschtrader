#!/bin/bash

# Source the environment variables
. ~/gtvar.sh

# Required Node.js versions
node_required="^18.19.1 || ^20.11.1 || ^22.0.0"

# Required Angular CLI version
angular_cli_required=19

# Function to check Node.js version manually
check_node_version() {
    local version=$1
    local required=$2

    IFS='.' read -r major minor patch <<<"$version"

    # Check for ^18.19.1 compatibility
    if [[ "$major" -eq 18 && ( "$minor" -gt 19 || ( "$minor" -eq 19 && "$patch" -ge 1 ) ]]; then
        return 0
    fi
    # Check for ^20.11.1 compatibility
    if [[ "$major" -eq 20 && ( "$minor" -gt 11 || ( "$minor" -eq 11 && "$patch" -ge 1 ) ]]; then
        return 0
    fi
    # Check for ^22.0.0 compatibility
    if [[ "$major" -eq 22 ]]; then
        return 0
    fi

    return 1
}

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

# Check for semver or fallback to manual version check
if command -v npx >/dev/null 2>&1 && npx semver "$NODEVERSION" -r "$node_required" >/dev/null 2>&1; then
    echo "Node.js version ($NODEVERSION) meets the required version: $node_required"
else
    # Use manual version check if semver is not installed
    if ! check_node_version "$NODEVERSION" "$node_required"; then
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
