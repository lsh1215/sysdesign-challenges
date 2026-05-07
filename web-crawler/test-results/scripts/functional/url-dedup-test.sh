#!/usr/bin/env bash
# URL dedup test (NFR-5): same URL POSTed twice -> 2nd response status=duplicate.
set -euo pipefail

URL="http://wiremock:8080/dedup-test-$(date +%s)"

resp1=$(curl -sf -X POST localhost:8081/urls \
  -H 'Content-Type: application/json' \
  -d "{\"url\":\"$URL\"}")
echo "first response:  $resp1"
echo "$resp1" | grep -q '"status":"queued"' || {
  echo "FAIL: first POST should be 'queued' but was: $resp1"
  exit 1
}

resp2=$(curl -sf -X POST localhost:8081/urls \
  -H 'Content-Type: application/json' \
  -d "{\"url\":\"$URL\"}")
echo "second response: $resp2"
echo "$resp2" | grep -q '"status":"duplicate"' || {
  echo "FAIL: second POST should be 'duplicate' but was: $resp2"
  exit 1
}

echo "PASS"
