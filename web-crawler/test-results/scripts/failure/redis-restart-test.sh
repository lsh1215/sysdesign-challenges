#!/usr/bin/env bash
# Redis restart test: Bloom filter survives container restart via AOF persistence.
set -euo pipefail

TEST_KEY="bloom-restart-test-$(date +%s)"

docker compose exec -T redis-stack redis-cli BF.ADD url:seen "$TEST_KEY" > /dev/null
before=$(docker compose exec -T redis-stack redis-cli BF.EXISTS url:seen "$TEST_KEY" | tr -d '[:space:]\r')
echo "before restart: BF.EXISTS=$before"
[ "$before" = "1" ] || { echo "FAIL: bloom not marked before restart"; exit 1; }

docker compose restart redis-stack > /dev/null

# Wait for redis-stack health to come back
for i in $(seq 1 30); do
  health=$(docker inspect -f '{{.State.Health.Status}}' web-crawler-redis 2>/dev/null || echo "starting")
  [ "$health" = "healthy" ] && break
  sleep 1
done
echo "redis-stack health after restart: ${health:-unknown}"

after=$(docker compose exec -T redis-stack redis-cli BF.EXISTS url:seen "$TEST_KEY" | tr -d '[:space:]\r')
echo "after restart:  BF.EXISTS=$after"
[ "$after" = "1" ] || { echo "FAIL: bloom not persisted across restart (AOF)"; exit 1; }

echo "PASS: bloom persisted via AOF"
