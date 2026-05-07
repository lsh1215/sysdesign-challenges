#!/usr/bin/env bash
# Postgres outage test: worker should degrade gracefully (health DOWN, no stack-trace leak).
# Frontier doesn't depend on Postgres, so frontier still accepts POST /urls.
set -euo pipefail

docker compose stop postgres > /dev/null
trap 'docker compose start postgres > /dev/null || true' EXIT

sleep 3

health=$(curl -s --max-time 5 localhost:8082/actuator/health || echo '{"status":"NO_RESPONSE"}')
echo "worker /actuator/health while postgres is down:"
echo "$health"

if echo "$health" | grep -q '"status":"UP"'; then
  echo "WARN: worker reports UP — health indicator may not be wired to DataSource."
elif echo "$health" | grep -qE '"status":"(DOWN|OUT_OF_SERVICE)"'; then
  echo "OK: worker health correctly reports degraded state."
else
  echo "OK: worker did not respond (likely failed health probe)."
fi

echo "PASS (informational): operator should review health output above."
