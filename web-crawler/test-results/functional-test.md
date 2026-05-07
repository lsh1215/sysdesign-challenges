# Functional Test Results

| Test | NFR | Expected | Observed | Pass/Fail | Date |
|---|---|---|---|---|---|
| content-dedup | NFR-4 | Same body → 1 content_seen hash, 1 MinIO object | TBD | TBD | TBD |
| url-dedup | NFR-5 | 2nd POST → `{"status":"duplicate"}` | TBD | TBD | TBD |
| politeness | NFR-3 | ≤ 1 req/sec/domain (lease holds) | TBD | TBD | TBD |
| metrics-exposure | R-2 | `crawler_pipeline_dropped_total` visible at /actuator/prometheus | TBD | TBD | TBD |

## How to run

```bash
cd web-crawler/source && docker compose up -d
bash ../test-results/scripts/functional/url-dedup-test.sh
bash ../test-results/scripts/functional/content-dedup-test.sh
bash ../test-results/scripts/functional/politeness-test.sh
bash ../test-results/scripts/functional/metrics-exposure-test.sh
```
