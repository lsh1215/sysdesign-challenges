# Web Crawler — Test Results

This directory holds NFR test stubs and result tables for the MVI build.
Source-code unit/IT tests live in `web-crawler/source/**/src/test/`.

## NFR ↔ Test Traceability

| SDD §9 NFR / G | Description | Test File |
|---|---|---|
| NFR-1 | Throughput target (scaled) 100 RPS frontier dequeue | `web-crawler/test-results/scripts/load/load-frontier.js` |
| NFR-2 | Enqueue throughput 50 RPS | `web-crawler/test-results/scripts/load/load-enqueue.js` |
| NFR-3 | Per-domain politeness ≤ 1 req/sec/domain | `web-crawler/test-results/scripts/functional/politeness-test.sh` |
| NFR-4 | Content dedup correctness | `web-crawler/test-results/scripts/functional/content-dedup-test.sh` |
| NFR-5 | URL dedup correctness | `web-crawler/test-results/scripts/functional/url-dedup-test.sh` |
| NFR-6 | Freshness threshold respected (default 7d) | `web-crawler/source/freshness-scheduler/src/test/java/com/crawler/freshness/application/FreshnessServiceIT.java` (Phase 6) |
| G-5 | Plug-in extensibility (multiple LinkExtractor) | `web-crawler/source/crawler-worker/src/test/java/com/crawler/worker/PluggableExtractorTest.java` (Phase 5) |
| R-1 | Bloom saturation behavior | documented in §Cross-Phase Risks |
| R-2 | Parser-error rate observable | `crawler.pipeline.dropped{reason=parse}` exposed at `/actuator/prometheus`; assertion in `web-crawler/test-results/scripts/functional/metrics-exposure-test.sh` |
| Robustness | Giant page / spider trap | `web-crawler/source/crawler-worker/src/test/java/com/crawler/worker/GiantPageDownloadIT.java` (Phase 5) |
| Robustness | Redis restart recovery (AOF) | `web-crawler/test-results/scripts/failure/redis-restart-test.sh` |
| Robustness | Postgres outage | `web-crawler/test-results/scripts/failure/postgres-down-test.sh` |

## How to run

Bring the stack up first, then run scripts from this directory:

```bash
cd web-crawler/source && docker compose up -d --build
sleep 30  # wait for app boot

# k6 load tests (host-side; install k6 separately)
k6 run web-crawler/test-results/scripts/load/load-frontier.js
k6 run web-crawler/test-results/scripts/load/load-enqueue.js

# Functional
bash web-crawler/test-results/scripts/functional/url-dedup-test.sh
bash web-crawler/test-results/scripts/functional/content-dedup-test.sh
bash web-crawler/test-results/scripts/functional/politeness-test.sh
bash web-crawler/test-results/scripts/functional/metrics-exposure-test.sh

# Failure (mutates the running stack)
bash web-crawler/test-results/scripts/failure/redis-restart-test.sh
bash web-crawler/test-results/scripts/failure/postgres-down-test.sh
```

Result tables: `load-test.md`, `functional-test.md`, `failure-test.md`.
