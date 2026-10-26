#!/usr/bin/env bash
# Checks the parts of a running instance that the health endpoint does not cover: the SPA is served
# at the root and on a client-side route, the readiness group is UP, and a seeded demo customer can
# sign in through the CSRF-protected API and read their accounts.
# Usage: bash .github/scripts/check-app.sh [base-url]
set -euo pipefail

base_url="${1:-http://localhost:8080}"
demo_user="${DEMO_USER:-thandi}"
demo_password="${APP_DEMO_PASSWORD:-demo-password}"

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

jar=$(mktemp)
trap 'rm -f "${jar}"' EXIT
status=""
# The demo seeder runs just after the server starts accepting requests, so the first sign-in can
# arrive before the user exists. Four attempts stay under the five-failure login lockout.
for attempt in 1 2 3 4; do
  curl -fsS --max-time 10 -c "${jar}" -b "${jar}" -o /dev/null "${base_url}/api/v1/auth/csrf"
  token=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "${jar}" | tail -n 1)
  status=$(curl -sS --max-time 10 -c "${jar}" -b "${jar}" -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: ${token}" \
    --data "{\"username\":\"${demo_user}\",\"password\":\"${demo_password}\"}" \
    "${base_url}/api/v1/auth/login")
  [ "${status}" = "200" ] && break
  echo "Sign-in attempt ${attempt} as ${demo_user} returned ${status}; retrying" >&2
  sleep 3
done
[ "${status}" = "200" ] || { echo "Demo sign-in as ${demo_user} failed with ${status}" >&2; exit 1; }

accounts=$(curl -fsS --max-time 10 -b "${jar}" "${base_url}/api/v1/accounts")
grep -q '"maskedNumber"' <<<"${accounts}" || { echo "No accounts for ${demo_user}: ${accounts}" >&2; exit 1; }
echo "Demo sign-in works and ${demo_user} can read their accounts"
