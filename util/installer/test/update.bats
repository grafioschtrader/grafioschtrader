#!/usr/bin/env bats
load helpers

setup() {
  setup_installation
}

teardown() {
  chmod u+w "$docroot"
}

@test "backend success replaces the old JAR and starts the service" {
  run bash "$HOME/gtupbackend.sh"
  [ "$status" -eq 0 ]
  [ ! -e "$HOME/grafioschtrader-server-old.jar" ]
  [ "$(cat "$HOME/grafioschtrader-server-new.jar")" = 'new jar' ]
  [ "$(cat "$SERVICE")" = running ]
  assert_no_staging
}

@test "installer backend build never starts or stops a service on success or failure" {
  export GT_INSTALL_BUILD_ONLY=1
  run bash "$HOME/gtupbackend.sh"
  [ "$status" -eq 0 ]
  ! grep -q 'sudo systemctl' "$EVENTS"
  export FAIL=mvn
  run bash "$HOME/gtupbackend.sh"
  [ "$status" -ne 0 ]
  ! grep -q 'sudo systemctl' "$EVENTS"
}

@test "installer frontend build leaves the service alone in the memory stop branch" {
  export GT_INSTALL_BUILD_ONLY=1 MEMORY=3800
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  ! grep -q 'sudo systemctl' "$EVENTS"
  export FAIL=ng
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -ne 0 ]
  ! grep -q 'sudo systemctl' "$EVENTS"
}

@test "Maven failure preserves the old JAR and recovers the service" {
  export FAIL=mvn
  run bash "$HOME/gtupbackend.sh"
  [ "$status" -ne 0 ]
  assert_old_backend
}

@test "missing, multiple and empty build JARs are rejected" {
  for mode in missing multiple empty; do
    export JAR_MODE=$mode
    run bash "$HOME/gtupbackend.sh"
    [ "$status" -ne 0 ]
    assert_old_backend
  done
}

@test "backend copy or publish failure preserves the old JAR" {
  for failure in cp publish-back; do
    export FAIL=$failure
    run bash "$HOME/gtupbackend.sh"
    [ "$status" -ne 0 ]
    assert_old_backend
    assert_no_staging
  done
}

@test "service start failure is reported without claiming migration rollback" {
  export FAIL=service-start
  run bash "$HOME/gtupbackend.sh"
  [ "$status" -ne 0 ]
  [[ "$output" == *'does not roll back database migrations'* ]]
}

@test "frontend success replaces only GT content and removes stale assets" {
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  [ "$(cat "$docroot/grafioschtrader/index.html")" = 'new frontend' ]
  [ ! -e "$docroot/grafioschtrader/.old-asset" ]
  [ "$(cat "$docroot/other-site/index.html")" = 'other site' ]
  assert_no_staging
}

@test "frontend is created when its target directory is absent" {
  rm -rf "$docroot/grafioschtrader"
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  [ -s "$docroot/grafioschtrader/index.html" ]
}

@test "downloaded frontend is deployed from a fresh staging directory" {
  export MEMORY=2000
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  [ "$(cat "$docroot/grafioschtrader/index.html")" = 'new frontend' ]
  assert_no_staging
}

@test "GT can update when the shared document root is not writable" {
  [ "$(id -u)" -ne 0 ] || skip 'Run as a regular user to check directory permissions'
  chmod a-w "$docroot"
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  [ "$(cat "$docroot/grafioschtrader/index.html")" = 'new frontend' ]
  [ "$(cat "$docroot/other-site/index.html")" = 'other site' ]
  assert_no_staging
}

@test "partial publication also recovers inside a non-writable document root" {
  [ "$(id -u)" -ne 0 ] || skip 'Run as a regular user to check directory permissions'
  chmod a-w "$docroot"
  export FAIL=publish-front
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -ne 0 ]
  assert_old_frontend
  assert_no_staging
}

@test "standalone frontend build restarts a service it stopped for memory" {
  export MEMORY=3800
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -eq 0 ]
  grep -Fx 'sudo systemctl stop grafioschtrader.service' "$EVENTS"
  grep -Fx 'sudo systemctl start grafioschtrader.service' "$EVENTS"
  [ "$(cat "$SERVICE")" = running ]
}

@test "frontend command failures preserve deployed content in both build modes" {
  for failure in npm node ng wget tar; do
    export FAIL=$failure MEMORY=3800
    if [[ "$failure" == wget || "$failure" == tar ]]; then export MEMORY=2000; fi
    run bash "$HOME/gtupfrontend.sh"
    [ "$status" -ne 0 ]
    assert_old_frontend
    [ "$(cat "$SERVICE")" = running ]
    assert_no_staging
  done
}

@test "missing or empty frontend output never reuses stale build output" {
  for mode in missing empty; do
    export OUTPUT_MODE=$mode
    run bash "$HOME/gtupfrontend.sh"
    [ "$status" -ne 0 ]
    assert_old_frontend
    assert_no_staging
  done
}

@test "partial frontend publication is rolled back" {
  export FAIL=publish-front
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -ne 0 ]
  assert_old_frontend
  assert_no_staging
}

@test "unsafe base paths are rejected before running builds or ownership changes" {
  for value in '' / ../other-site . 'grafioschtrader/..' 'grafioschtrader*'; do
    printf 'export basehref=%q\n' "$value" >> "$HOME/gtvar.sh"
    run bash "$HOME/gtupfrontend.sh"
    [ "$status" -ne 0 ]
    cd "$HOME"
    run bash "$REPO/util/shellscripts/installroot.sh"
    [ "$status" -ne 0 ]
    assert_old_frontend
  done
  [ ! -s "$EVENTS" ]
}

@test "symlink escaping docroot is rejected" {
  ln -s "$HOME" "$docroot/escape"
  printf 'export basehref=escape/\n' >> "$HOME/gtvar.sh"
  run bash "$HOME/gtupfrontend.sh"
  [ "$status" -ne 0 ]
  [ ! -s "$EVENTS" ]
}

@test "installroot changes ownership only of GT and its log" {
  cd "$HOME"
  run bash "$REPO/util/shellscripts/installroot.sh"
  [ "$status" -eq 0 ]
  grep -Fx "chown -R grafioschtrader:grafioschtrader $docroot/grafioschtrader" "$EVENTS"
  ! grep -Fx "chown -R grafioschtrader:grafioschtrader $docroot" "$EVENTS"
}

@test "sequential frontend failure prevents backend build and recovers old service" {
  export MEMORY=3800 FAIL=ng
  run bash "$HOME/gtupfrontback.sh"
  [ "$status" -ne 0 ]
  ! grep -q '^mvn ' "$EVENTS"
  assert_old_frontend
  assert_old_backend
}

@test "parallel frontend failure is propagated after waiting for backend" {
  export FAIL=ng
  run bash "$HOME/gtupfrontback.sh"
  [ "$status" -ne 0 ]
  assert_old_frontend
  [ -s "$HOME/grafioschtrader-server-new.jar" ]
  [ "$(cat "$SERVICE")" = running ]
}

@test "Maven failures propagate through sequential and parallel builds" {
  for memory in 3800 8000; do
    export MEMORY=$memory FAIL=mvn
    run bash "$HOME/gtupfrontback.sh"
    [ "$status" -ne 0 ]
    assert_old_backend
  done
}

@test "all frontend command failures propagate through both coordination branches" {
  for memory in 3800 8000; do
    for failure in npm node ng wget tar; do
      export MEMORY=$memory FRONT_MEMORY=3800 FAIL=$failure
      if [[ "$failure" == wget || "$failure" == tar ]]; then export FRONT_MEMORY=2000; fi
      run bash "$HOME/gtupfrontback.sh"
      [ "$status" -ne 0 ]
      assert_old_frontend
      [ "$(cat "$SERVICE")" = running ]
      assert_no_staging
    done
  done
}

@test "successful sequential and parallel builds return zero" {
  for memory in 3800 8000; do
    export MEMORY=$memory
    run bash "$HOME/gtupfrontback.sh"
    [ "$status" -eq 0 ]
    [ -s "$HOME/grafioschtrader-server-new.jar" ]
    [ "$(cat "$docroot/grafioschtrader/index.html")" = 'new frontend' ]
    [ "$(cat "$SERVICE")" = running ]
    assert_no_staging
  done
}

@test "parallel log pipeline failure cannot be reported as success" {
  export FAIL=tee
  run bash "$HOME/gtupfrontback.sh"
  [ "$status" -ne 0 ]
  [ "$(cat "$SERVICE")" = running ]
}

@test "gtupdate returns a failed build status through its normal entry point" {
  # gtupdate's historical configuration-copy phase expects paths without spaces.
  export TEST_FIXTURE_NAME=entry-point
  setup_installation
  ln -s "$FIXTURE/bin/stub-command" "$FIXTURE/bin/git"
  scripts="$builddir/grafioschtrader/util/shellscripts"
  cp "$HOME/"gtup*.sh "$scripts/"
  # Match a Linux checkout's LF line endings, even when this test runs from a Windows working tree.
  sed 's/\r$//' "$REPO/util/shellscripts/gtupdate.sh" > "$HOME/gtupdate.sh"
  cp "$HOME/gtupdate.sh" "$scripts/gtupdate.sh"
  for script in merger.sh gt_to_g_rename.sh; do
    sed 's/\r$//' "$REPO/util/shellscripts/$script" > "$scripts/$script"
  done
  printf '#!/bin/bash\nexit 0\n' > "$scripts/checkversion.sh"
  chmod +x "$scripts/"*.sh
  resource_dir="$builddir/grafioschtrader/backend/grafioschtrader-server/src/main/resources"
  mkdir -p "$resource_dir"
  printf 'spring.datasource.hikari.connection-init-sql=SET time_zone = '\''+00:00'\''\n' \
    > "$resource_dir/application.properties"
  export MEMORY=3800 FAIL=mvn
  run bash "$HOME/gtupdate.sh"
  [ "$status" -ne 0 ]
  grep -Fx 'mvn clean package -DskipTests' "$EVENTS"
  assert_old_backend
}
