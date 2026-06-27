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
BACKUP_DONE_MARKER="$builddir/.gt_backup_done"

# Backup nur einmal machen (vor git reset) — Marker verhindert Wiederholung beim Neustart
if [ ! -f "$BACKUP_DONE_MARKER" ]; then
    cp $GT_PROF_PATH/$GT_PROF .
    if [ -e $GT_PROF_PATH/$GT_PROF_PROD ]; then
        cp $GT_PROF_PATH/$GT_PROF_PROD .
    fi
    touch "$BACKUP_DONE_MARKER"
else
    echo "Backup bereits vorhanden (Neustart nach Skript-Update erkannt) — überspringe Backup-Schritt"
fi

# Update repository
cd grafioschtrader/
if ! git remote get-url raspi >/dev/null 2>&1; then
    git remote add raspi git@gt8p1.duckdns.org:/home/git/repos/grafioschtrader.git
fi

rm -fr frontend
git fetch raspi
git reset --hard raspi/master

# Check if this script was updated
SCRIPT_PATH="${BASH_SOURCE[0]}"
UPDATED_SCRIPT="$builddir/grafioschtrader/util/shellscripts/$(basename "$SCRIPT_PATH")"

if [ -f "$UPDATED_SCRIPT" ] && ! cmp -s "$SCRIPT_PATH" "$UPDATED_SCRIPT"; then
  echo "The script itself has been updated. Restarting..."
  cp "$UPDATED_SCRIPT" "$SCRIPT_PATH"
  chmod +x "$SCRIPT_PATH"
  exec "$SCRIPT_PATH"
fi

# Copy and execute checkversion.sh
cp $builddir/grafioschtrader/util/shellscripts/checkversion.sh ~/.
cp $builddir/grafioschtrader/util/shellscripts/merger.sh ~/.
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
   if [ $? -ne 0 ]; then
       echo "ERROR: merger.sh fehlgeschlagen! Stelle Backup wieder her..."
       cp "$builddir/$GT_PROF" "$GT_PROF_PATH/$GT_PROF"
       exit 1
   fi
fi
if [ -f $GT_PROF_PROD ]; then
   cp $GT_PROF_PROD $GT_PROF_PATH/.
fi

# Cleanup: Marker und temporaere Dateien entfernen
rm -f "$BACKUP_DONE_MARKER"
rm -f "$builddir/${GT_PROF}.new"

# Execute final steps
cd ~
~/gtupfrontback.sh
