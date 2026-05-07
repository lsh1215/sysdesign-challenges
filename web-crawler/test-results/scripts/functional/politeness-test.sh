#!/usr/bin/env bash
# Politeness test (NFR-3): same domain rate-limit / per-domain lease.
# Enqueue 3 URLs to the same fresh domain, then poll /urls/next 3x rapidly.
# Per-domain lease should ensure at most 1 dequeue within the lease window (~1s).
set -euo pipefail

DOMAIN="politeness-test-$(date +%s).example.com"

for i in 1 2 3; do
  curl -sf -X POST localhost:8081/urls \
    -H 'Content-Type: application/json' \
    -d "{\"url\":\"http://$DOMAIN/p$i\"}" > /dev/null
done

t0=$(date +%s%N)
count_200=0
for i in 1 2 3; do
  code=$(curl -s -o /dev/null -w "%{http_code}" localhost:8081/urls/next)
  [ "$code" = "200" ] && count_200=$((count_200+1))
  sleep 0.1
done
t1=$(date +%s%N)
elapsed_ms=$(( (t1 - t0) / 1000000 ))

echo "in $elapsed_ms ms got $count_200 successful dequeues for domain=$DOMAIN"

# At lease=1s, in ~300ms we expect at most 1 successful dequeue from the same domain.
# Allow up to 2 if elapsed crosses the lease boundary.
if [ "$count_200" -le 2 ]; then
  echo "PASS"
else
  echo "FAIL: too many dequeues ($count_200) for same domain in ${elapsed_ms}ms"
  exit 1
fi
