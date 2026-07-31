#!/bin/bash

# Absolute path of this script, resolved before the first "cd" below.
# ${BASH_SOURCE[0]} is relative when the script is started as ./gtupdate.sh, while the
# self-update further down runs after two directory changes. Resolving it late made the
# self-update write its copy into the build directory instead of the home directory and
# then re-execute that stray copy - and where the stray copy could not be overwritten,
# the restart repeated forever.
SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

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

# Backup nur einmal machen (vor git reset) � Marker verhindert Wiederholung beim Neustart
if [ ! -f "$BACKUP_DONE_MARKER" ]; then
    cp $GT_PROF_PATH/$GT_PROF .
    if [ -e $GT_PROF_PATH/$GT_PROF_PROD ]; then
        cp $GT_PROF_PATH/$GT_PROF_PROD .
    fi
    touch "$BACKUP_DONE_MARKER"
else
    echo "Backup bereits vorhanden (Neustart nach Skript-Update erkannt) � �berspringe Backup-Schritt"
fi

# Update repository
cd grafioschtrader/
if ! git remote get-url origin >/dev/null 2>&1; then
    git remote add origin https://github.com/grafioschtrader/grafioschtrader.git
fi

rm -fr frontend
git fetch origin
git reset --hard origin/master

# Check if this script was updated
UPDATED_SCRIPT="$builddir/grafioschtrader/util/shellscripts/$(basename "$SCRIPT_PATH")"

if [ -f "$UPDATED_SCRIPT" ] && ! cmp -s "$SCRIPT_PATH" "$UPDATED_SCRIPT"; then
  # One restart is enough. If the script still differs afterwards, the copy did not take
  # effect and restarting again would loop, stopping the service and deleting frontend/
  # on every pass.
  if [ -n "${GT_SCRIPT_UPDATED:-}" ]; then
    echo "ERROR: $SCRIPT_PATH weicht nach dem Neustart immer noch von $UPDATED_SCRIPT ab."
    echo "Datei von Hand pruefen und ersetzen:"
    ls -l "$SCRIPT_PATH"
    exit 1
  fi
  echo "The script itself has been updated. Restarting..."
  if ! cp "$UPDATED_SCRIPT" "$SCRIPT_PATH"; then
    echo "ERROR: $SCRIPT_PATH konnte nicht aktualisiert werden - Abbruch statt Neustart."
    ls -l "$SCRIPT_PATH"
    exit 1
  fi
  chmod +x "$SCRIPT_PATH"
  GT_SCRIPT_UPDATED=1 exec "$SCRIPT_PATH"
fi

# Copy and execute checkversion.sh
cp $builddir/grafioschtrader/util/shellscripts/checkversion.sh ~/.
cp $builddir/grafioschtrader/util/shellscripts/merger.sh ~/.
cp $builddir/grafioschtrader/util/shellscripts/gt_to_g_rename.sh ~/.
chmod +x ~/gt_to_g_rename.sh
cp $builddir/grafioschtrader/util/shellscripts/gtup{front,back}*.sh ~/.
~/checkversion.sh
if [ $? -ne 0 ]; then
  exit 1
fi

# Merge configuration files if they exist
cd $builddir

# Rename library-owned deployment properties (gt. -> g., GitHub issue #75) in the
# saved user configuration before it is merged. merger.sh matches keys exactly, so
# without this the user value of a renamed key would be replaced by the template
# default. Stays a no-op as long as the new template still uses the gt.* names.
~/gt_to_g_rename.sh -s "$GT_PROF_PATH/$GT_PROF" -i "$GT_PROF" -i "$GT_PROF_PROD"
if [ $? -ne 0 ]; then
    echo "ERROR: Migration der Konfigurationsschluessel fehlgeschlagen!"
    echo "Die gesicherte Konfiguration wurde nicht veraendert: $builddir/$GT_PROF"
    echo "Konflikt beheben und gtupdate.sh erneut starten."
    exit 1
fi

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
