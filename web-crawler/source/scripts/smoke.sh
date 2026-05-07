#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[smoke] waiting for frontier-service to accept seed POST..."
seed_ok=0
for i in $(seq 1 60); do
  if curl -sf -X POST localhost:8081/urls \
        -H 'Content-Type: application/json' \
        -d '{"url":"http://wiremock:8080/seed-page"}' >/dev/null 2>&1; then
    seed_ok=1
    break
  fi
  sleep 2
done
[ "$seed_ok" = "1" ] || { echo "[smoke] FAIL: frontier never accepted seed"; exit 1; }
echo "[smoke] seed accepted"

echo "[smoke] waiting for visited_url rows..."
rows=0
for i in $(seq 1 60); do
  rows=$(docker compose exec -T postgres psql -U crawler -tAc \
        "select count(*) from visited_url" 2>/dev/null | tr -d '[:space:]' || echo 0)
  [ "${rows:-0}" -ge 1 ] && break
  sleep 2
done
[ "${rows:-0}" -ge 1 ] || { echo "[smoke] FAIL: no visited_url rows"; exit 1; }
echo "[smoke] visited_url rows=$rows"

content_rows=$(docker compose exec -T postgres psql -U crawler -tAc \
      "select count(*) from content_seen" 2>/dev/null | tr -d '[:space:]' || echo 0)
[ "${content_rows:-0}" -ge 1 ] || { echo "[smoke] FAIL: no content_seen rows"; exit 1; }
echo "[smoke] content_seen rows=$content_rows"

obj_count=$(docker compose exec -T minio sh -c \
      "mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1 && mc ls local/crawler-html 2>/dev/null | wc -l" \
      | tr -d '[:space:]' || echo 0)
[ "${obj_count:-0}" -ge 1 ] || { echo "[smoke] FAIL: no MinIO objects"; exit 1; }
echo "[smoke] minio objects=$obj_count"

bf=$(docker compose exec -T redis-stack redis-cli BF.EXISTS url:seen "http://wiremock:8080/seed-page" \
     | tr -d '[:space:]')
[ "$bf" = "1" ] || { echo "[smoke] FAIL: BF miss for seed (got '$bf')"; exit 1; }
echo "[smoke] BF.EXISTS seed=1"

echo "[smoke] OK"
