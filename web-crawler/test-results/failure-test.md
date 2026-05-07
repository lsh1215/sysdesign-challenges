# Failure Test Results

| Test | NFR/Risk | Expected | Observed | Pass/Fail | Date |
|---|---|---|---|---|---|
| redis-restart | Bloom AOF persistence | `BF.EXISTS=1` after `docker compose restart redis-stack` | TBD | TBD | TBD |
| postgres-down | Graceful degradation | Worker `/actuator/health` reports DOWN; no stack-trace leak in error responses | TBD | TBD | TBD |

## How to run

```bash
cd web-crawler/source && docker compose up -d
bash ../test-results/scripts/failure/redis-restart-test.sh
bash ../test-results/scripts/failure/postgres-down-test.sh
```

> Failure scripts mutate the running stack (restart/stop containers). Run them last
> or in a disposable environment.
