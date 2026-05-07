# Load Test Results

| Scenario | NFR | Target | Observed | Pass/Fail | Date |
|---|---|---|---|---|---|
| Frontier dequeue | NFR-1 | 100 RPS, p95 < 200ms | TBD | TBD | TBD |
| Frontier enqueue | NFR-2 | 50 RPS, p95 < 300ms | TBD | TBD | TBD |

## How to run

```bash
cd web-crawler/source && docker compose up -d
k6 run ../test-results/scripts/load/load-frontier.js
k6 run ../test-results/scripts/load/load-enqueue.js
```

## MVI scaling note

SDD §9 NFR-1 calls for 400 pages/sec average / 800 peak. MVI runs single-node Postgres
and a single crawler-worker; the load test is scaled down to 100 RPS dequeue / 50 RPS
enqueue to validate functional correctness rather than absolute throughput. Production
sizing requires horizontal scaling of `crawler-worker` plus Postgres read replicas / partitioning.
