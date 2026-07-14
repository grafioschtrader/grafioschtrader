#!/bin/sh
# E2E test orchestrator wrapper. Documentation: header of scripts/e2e-test.mjs
exec node "$(dirname "$0")/scripts/e2e-test.mjs" "$@"
