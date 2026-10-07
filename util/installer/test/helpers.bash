# Temporary installation with fake external commands. No real build or service is invoked.
setup_installation() {
  REPO=$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)
  export FIXTURE="$BATS_TEST_TMPDIR/${TEST_FIXTURE_NAME:-installation with spaces}"
  export HOME="$FIXTURE/home" builddir="$FIXTURE/build" docroot="$FIXTURE/www" basehref=grafioschtrader/
  export EVENTS="$FIXTURE/events" SERVICE="$FIXTURE/service" MEMORY=8000
  export PATH="$FIXTURE/bin:$PATH"
  mkdir -p "$HOME" "$FIXTURE/bin" "$docroot/grafioschtrader" "$docroot/other-site" \
    "$builddir/grafioschtrader/backend/grafioschtrader-server/target" \
    "$builddir/grafioschtrader/frontend/dist/browser" "$builddir/grafioschtrader/util/shellscripts"
  printf 'old jar\n' > "$HOME/grafioschtrader-server-old.jar"
  printf 'old frontend\n' > "$docroot/grafioschtrader/index.html"
  printf 'old hidden asset\n' > "$docroot/grafioschtrader/.old-asset"
  printf 'other site\n' > "$docroot/other-site/index.html"
  printf 'stale output\n' > "$builddir/grafioschtrader/frontend/dist/browser/index.html"
  printf 'running\n' > "$SERVICE"
  : > "$EVENTS"
  printf 'export builddir=%q\nexport docroot=%q\nexport basehref=%q\n' \
    "$builddir" "$docroot" "$basehref" > "$HOME/gtvar.sh"
  cp "$REPO"/util/shellscripts/gtup*.sh "$HOME/"
  chmod +x "$HOME/"*.sh
  printf '#!/bin/bash\nexit 0\n' > "$builddir/grafioschtrader/util/shellscripts/gtcronrandom.sh"
  cp "$BATS_TEST_DIRNAME/stub-command.sh" "$FIXTURE/bin/stub-command"
  chmod +x "$FIXTURE/bin/stub-command"
  local cmd
  for cmd in mvn npm node ng wget tar sudo free cp mv tee chown touch; do
    ln -s "$FIXTURE/bin/stub-command" "$FIXTURE/bin/$cmd"
  done
  # A genuine tarball exercises the download/extraction path without network access.
  mkdir -p "$FIXTURE/archive/dist/browser"
  printf 'new frontend\n' > "$FIXTURE/archive/dist/browser/index.html"
  printf 'new asset\n' > "$FIXTURE/archive/dist/browser/main.js"
  /bin/tar -czf "$FIXTURE/latest.tar.gz" -C "$FIXTURE/archive" dist
}

assert_old_backend() {
  [[ $(cat "$HOME/grafioschtrader-server-old.jar") == 'old jar' ]]
  [[ ! -e "$HOME/grafioschtrader-server-new.jar" ]]
  [[ $(cat "$SERVICE") == running ]]
}

assert_old_frontend() {
  [[ $(cat "$docroot/grafioschtrader/index.html") == 'old frontend' ]]
  [[ -f "$docroot/grafioschtrader/.old-asset" ]]
  [[ ! -f "$docroot/grafioschtrader/main.js" ]]
  [[ $(cat "$docroot/other-site/index.html") == 'other site' ]]
}

assert_no_staging() {
  [[ -z $(find "$HOME" "$docroot" -name '.gt-backend.*' -o -name '.gt-frontend.*') ]]
}
