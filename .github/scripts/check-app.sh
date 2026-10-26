#!/usr/bin/env bash
# Checks the parts of a running instance that the health endpoint does not cover: the SPA is served
# at the root and on a client-side route, and the readiness group is UP.
# Usage: bash .github/scripts/check-app.sh [base-url]
set -euo pipefail

base_url="${1:-http://localhost:8080}"

check_spa() {
  local path="$1" html
  html=$(curl -fsS --max-time 10 "${base_url}${path}")
  # The Quasar entry point is the mount node of the app; its absence means the SPA was not packaged.
  # The minified build writes the attribute without quotes, so the quotes are optional in the pattern.
  grep -Eq "id=[\"']?q-app" <<<"${html}" || { echo "No SPA shell at ${base_url}${path}" >&2; exit 1; }
  echo "SPA shell served at ${path}"
}

check_spa /
check_spa /login

readiness=$(curl -fsS --max-time 5 "${base_url}/actuator/health/readiness")
grep -q '"status":"UP"' <<<"${readiness}" || { echo "Readiness is not UP: ${readiness}" >&2; exit 1; }
echo "Readiness is UP"
