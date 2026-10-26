#!/usr/bin/env bash
# Polls the health endpoint until it reports UP, or fails after the timeout.
# Usage: bash .github/scripts/wait-for-health.sh [base-url] [timeout-seconds]
set -euo pipefail

base_url="${1:-http://localhost:8080}"
timeout="${2:-120}"
deadline=$((SECONDS + timeout))
body=""

# A failing curl is expected while the JVM and the database start, so it must not trip errexit.
until body=$(curl -fsS --max-time 5 "${base_url}/actuator/health" 2>/dev/null) && grep -q '"status":"UP"' <<<"${body}"; do
  if ((SECONDS >= deadline)); then
    echo "Health at ${base_url} was not UP after ${timeout}s. Last response: ${body:-none}" >&2
    exit 1
  fi
  sleep 3
done

echo "Health at ${base_url} is UP: ${body}"
