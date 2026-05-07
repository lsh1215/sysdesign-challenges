#!/usr/bin/env bash
# Metrics exposure test (R-2): /actuator/prometheus exposes crawler_pipeline_dropped_total.
set -euo pipefail

curl -sf localhost:8082/actuator/prometheus > /tmp/metrics.txt

if grep -E '^crawler_pipeline_dropped_total' /tmp/metrics.txt > /dev/null; then
  echo "PASS: crawler_pipeline_dropped_total exposed"
  grep -E '^crawler_pipeline_dropped_total' /tmp/metrics.txt | head -5
else
  echo "FAIL: crawler_pipeline_dropped_total metric not found in /actuator/prometheus"
  echo "--- first 50 lines of metrics ---"
  head -50 /tmp/metrics.txt
  exit 1
fi
