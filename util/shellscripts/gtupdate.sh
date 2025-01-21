#!/bin/bash

# Load environment variables
. ~/gtvar.sh
if [ $? -ne 0 ]; then
  echo "Failed to source ~/gtvar.sh"
  exit 1
fi

# Stop the Grafioschtrader service
sudo systemctl stop grafioschtrader.service

# Navigate to the build directory
cd $builddir

# Manage configuration files
GT_PROF=application.properties
GT_PROF_PROD=application-production.properties
GT_PROF_PATH=grafioschtrader/backend/grafioschtrader-server/src/main/resources
cp $GT_PROF_PATH/$GT_PROF .
if [ -e $GT_PROF_PATH/$GT_PROF_PROD ]; then
   cp $GT_PROF_PATH/$GT_PROF_PROD .
fi

# Update repository
cd grafioschtrader/
rm -fr frontend
git reset --hard origin/master
git pull --rebase

# Check if this script was updated
SCRIPT_PATH="${BASH_SOURCE[0]}"
UPDATED_SCRIPT="$builddir/grafioschtrader/util/shellscripts/$(basename "$SCRIPT_PATH")"

if [ -f "$UPDATED_SCRIPT" ] && ! cmp -s "$SCRIPT_PATH" "$UPDATED_SCRIPT"; then
  echo "The script itself has been updated. Restarting..."
  cp "$UPDATED_SCRIPT" "$SCRIPT_PATH"
  exec "$SCRIPT_PATH"
fi

# Copy and execute checkversion.sh
cp $builddir/grafioschtrader/util/shellscripts/checkversion.sh ~/.
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh ~/.
~/checkversion.sh
if [ $? -ne 0 ]; then
  exit 1
fi

# Merge configuration files if they exist
cd $builddir
if [ -f $GT_PROF ]; then
   mv $GT_PROF_PATH/$GT_PROF ${GT_PROF}.new
   ~/merger.sh -i $GT_PROF -s ${GT_PROF}.new -o $GT_PROF_PATH/$GT_PROF
fi
if [ -f $GT_PROF_PROD ]; then
   cp $GT_PROF_PROD $GT_PROF_PATH/.
fi

# Execute final steps
cd ~
~/gtupfrontback.sh

