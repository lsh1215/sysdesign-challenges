# web-crawler — Source

Minimum-viable implementation of the web-crawler design.

See `web-crawler/System-Design-Document/sdd.md` for the full design.

## Modules

| Module | Type | Purpose |
|---|---|---|
| `common` | java-library | Exception base, `ApiResponse`, `BaseEntity`, JPA auditing + CORS configs. Shared kernel. |
| `crawler-shared-infra` | java-library | JPA entities, repositories, Flyway migrations, RedisBloom + MinIO adapters, `RestFrontierClient`. Filled in Phase 3. |
| `frontier-service` | Spring Boot app | REST queue. Owns its own Redis client. Depends on `common` only. |
| `crawler-worker` | Spring Boot app | Download → parse → dedup → extract pipeline. Depends on `common` + `crawler-shared-infra`. |
| `freshness-scheduler` | Spring Boot app | Cron-driven recrawl. Depends on `common` + `crawler-shared-infra`. |

## Local infrastructure

```bash
docker compose up -d   # postgres + redis-stack + minio
docker compose down
```

## Build

```bash
./gradlew :common:build :crawler-shared-infra:build
```
