# Web Crawler — System Design (Mock Interview Style)

---

## 0. 메타데이터 (선택)

| 항목                | 값                                     |
| ------------------- | -------------------------------------- |
| Topic               | Web Crawler (Alex Xu Vol.1 Ch.9)       |
| Interviewer Persona | Design Partner (sysdesign-design 스킬) |
| Time Budget         | 60 min                                 |
| Date                | 2026-05-05                             |

---

## 1. Clarifying Questions

> _Why this section exists_
> 문제 범위를 좁히고 인터뷰어와 공통 전제를 맞춘다. **너무 빨리 설계로 넘어가는 것이 가장 흔한 실패 원인.** 5분 이내, 3–5개 질문이 적정.

### 1.1 Functional Requirements

> _What goes here_
> "유저는 …할 수 있어야 한다" 형태로 핵심 기능 3개에 집중. 부가 기능은 나중에.

- [x] FR-1: 시스템은 seed URL 묶음에서 시작해 HTML을 다운로드하고 추가 URL을 추출한다 (BFS 트래버설)
- [x] FR-2: 가져온 페이지를 **검색엔진 인덱싱**용으로 저장한다
- [x] FR-3: **새로 등장한 페이지** + **수정된 기존 페이지**를 모두 발견·저장한다 (재방문 필요)
- [x] FR-4: **중복 컨텐츠는 저장하지 않는다** (URL 중복 / 본문 중복 둘 다 — 전략 §1.2 참조)
- [x] FR-5: 새로운 컨텐츠 형태(이미지, PDF 등) 추가가 **시스템 재설계 없이** 가능해야 한다 (extensibility)

### 1.2 Non-Functional Requirements (정량화 필수)

> _What goes here_
> "낮은 지연" 같은 형용사가 아니라 숫자로. availability / consistency / latency / scalability / durability 중 시스템에 맞는 top 3–5개를 골라 수치 명시.

- [x] NFR-1 (처리량): 평균 **400 pages/sec**, peak **800 pages/sec** (×2 multiplier)
- [x] NFR-2 (확장성·병행성): 다중 노드 병렬 크롤링, 새 worker 노드 추가가 hot reshuffle 없이 가능
- [ ] NFR-3 (예절·rate limit): 도메인당 **? req/sec** (책 기본 1 req/sec/domain — 사용자 확인 필요)
- [x] NFR-4 (안정성): 악성 입력 / spider trap / 응답 없는 서버 / 거대 페이지 대응 — graceful degradation
- [x] NFR-5 (확장성·콘텐츠 형태): 새 컨텐츠 타입 추가 시 모듈 추가만으로 가능 (parser plug-in 패턴)
- [ ] NFR-6 (신선도): 재방문 주기 **?** (1주? 1달? 페이지 인기도에 따라 동적? — 사용자 확인 필요)

### 1.3 Scale & Constraints

> _What goes here_
> 트래픽 규모와 데이터 특성. 이 숫자가 뒤에 나올 추정·샤딩·캐시 결정의 근거가 된다.

| 항목             | 값                           | 근거                                                              |
| ---------------- | ---------------------------- | ----------------------------------------------------------------- |
| 처리 목표        | 1B pages / month             | 사용자 합의 (책 동일)                                             |
| 평균 페이지 크기 | 500 KB                       | 책 §Ch.9 기본 가정 (HTML + inline CSS/JS 평균) — 사용자 확인 필요 |
| 데이터 보존 기간 | 5년                          | 사용자 합의                                                       |
| 월간 raw 저장량  | 500 TB                       | 1B × 500 KB                                                       |
| 5년 누적 저장량  | **30 PB**                    | 500 TB × 60 month                                                 |
| 글로벌 여부      | Global (검색엔진용이라 가정) | 사용자 확인 필요                                                  |
| 재방문 비율      | (FR-3에 묶임)                | 신선도 NFR-6 결정 후 산정                                         |

### 1.4 Out of Scope (명시적 제외)

> _Why_
> 인터뷰어와 합의해 시간을 사야 한다. "인증/결제/관리자 도구는 다음 단계로 두자"고 못 박는다.

> _다음 답변에서 명시적으로 정할 것 — 책 기본은 아래 후보들:_

- [ ] (후보) JavaScript 렌더링 — 정적 HTML만 다룸 (headless browser 안 씀)
- [ ] (후보) 인증 필요한 페이지 / login wall
- [ ] (후보) 검색 인덱스 자체 (저장만 하고 indexing은 별도 시스템)
- [ ] (후보) 실시간 크롤링 (배치 기반)

---

## 2. High Level Design

> _Why this section exists_
> 전체 시스템의 뼈대를 스케치하고 인터뷰어의 동의를 얻는다. 박스/화살표가 없으면 안 된다 — 말로만 설명하지 말 것.

### 2.1 Back-of-the-Envelope Estimation

> _What goes here_
> 설계 결정을 정당화하는 숫자. 5분 초과 금지. 단위 표기 필수.
>
> 공식
>
> - QPS = (DAU × 사용자당 일일 요청 수) ÷ 86,400
> - Peak QPS ≈ 평균 × 10
> - Storage = 레코드 수 × 레코드 크기 × 복제 계수
> - Bandwidth = QPS × 평균 응답 크기 × 8 (bps)

| Metric                   | 계산                                                 | 값                   |
| ------------------------ | ---------------------------------------------------- | -------------------- |
| Crawl QPS (avg)          | 1,000,000,000 / (30 × 24 × 60 × 60) = 1B / 2,592,000 | **≈ 400 pages/sec**  |
| Crawl QPS (peak)         | avg × 2 (사용자 선택)                                | **≈ 800 pages/sec**  |
| 월간 raw HTML 저장       | 1B × 500 KB                                          | **≈ 500 TB / month** |
| 5년 누적 저장 (raw)      | 500 TB × 60                                          | **≈ 30 PB**          |
| Ingress Bandwidth (peak) | 800 pages/sec × 500 KB × 8                           | **≈ 3.2 Gbps**       |
| 도메인별 download thread | 1 / domain (책 기본 — NFR-3 확정 후 갱신)            | TBD                  |

> _Note:_ Bandwidth가 책에서 흔히 보는 "egress"가 아니라 **ingress** (외부 웹에서 우리쪽으로 다운로드). 데이터센터 외부 트래픽 비용 + 네트워크 capacity 검토 대상.

### 2.2 Core Entities (Data Model 초안)

> _What goes here_
> 핵심 명사만 나열. 컬럼은 5개 이내로 시작. 풀 스키마는 Drill Down에서.

> _회의 미논의 — 핵심 데이터 모델 (URL / Page / Hash 등) 정의 필요._

### 2.3 Architecture Diagram

**전체 흐름** (회의 §2 컴포넌트 식별):

![Component Flow](./diagrams/component-flow.png)

**MSA 서비스 경계** (Bounded Context 단위):

![Bounded Contexts](./diagrams/bounded-contexts.png)

**URL Frontier 내부 구조** (회의 §3.2):

![URL Frontier Internal](./diagrams/url-frontier-internal.png)

> _Note:_ priority별 큐 / 도메인별 큐 둘 다 **FIFO**. priority는 "어느 큐에 넣을지" 결정에만 쓰임. 큐 라우터의 매핑 테이블은 컴포넌트 다이어그램에서는 별도 표기 생략.

---

## 3. Drill Down

> _Why this section exists_
> Non-functional requirement 중 High Level이 아직 충족 못한 것을 메우는 단계. **시니어는 인터뷰어 힌트를 기다리지 않고 직접 병목을 찾아 제안한다.** 모든 영역을 고르게 파지 말고 가장 중요한 1–2개를 깊게.

### 3.1 Database

> _Decisions to make_
> SQL vs NoSQL, 샤딩 전략, 복제 토폴로지, 인덱스, 파티셔닝.

- **컨텐츠 저장 (raw HTML)**: S3 또는 HDFS — 30 PB 분산 blob 저장소
- **Dedup 인덱스 (content-seen check)**: NoSQL KV (Cassandra / DynamoDB) — `key = SHA-256 of body`, `value = S3 경로`
- **선택 근거**: 1B+ records의 단순 hash lookup 패턴 — 조인/트랜잭션 불필요, write 쓰루풋 우선

#### 워크 예시 — Content dedup 동작

**페이지 1 처리:**

```
URL:  https://news.example.com/article-42
본문: "<html>...오늘의 주요 뉴스...</html>"
```

1. 본문에 SHA-256 → `8f4d2a...91c`
2. KV store에서 `8f4d2a...91c` 조회 → **MISS**
3. 본문을 S3에 업로드 → `s3://crawler/2026/05/05/abc123.html`
4. KV store에 추가:

```
key   = "8f4d2a...91c"
value = "s3://crawler/2026/05/05/abc123.html"
```

**페이지 2 처리** (URL 다른데 본문 같은 미러 사이트):

```
URL:  https://mirror-site.com/copy-of-article   ← URL 다름
본문: "<html>...오늘의 주요 뉴스...</html>"     ← 본문은 페이지1과 동일
```

1. 본문에 SHA-256 → `8f4d2a...91c` (동일 해시)
2. KV store에서 `8f4d2a...91c` 조회 → **HIT** (기존 value: `s3://crawler/2026/05/05/abc123.html`)
3. S3 업로드 스킵, 페이지 2 폐기

**KV store 안 모습:**

| key (SHA-256 hash) | value (S3 경로)                       |
| ------------------ | ------------------------------------- |
| `8f4d2a...91c`     | `s3://crawler/2026/05/05/abc123.html` |
| `2b9e07...4a3`     | `s3://crawler/2026/05/05/def456.html` |
| `c1f8d0...2e7`     | `s3://crawler/2026/05/05/ghi789.html` |
| ... 1B개 ...       | ...                                   |

핵심: **해시 = 본문 지문, S3 경로 = 실제 파일 위치.**

#### 워크 예시 — URL dedup (Bloom filter on Redis)

KV가 아니라 **bit 배열** 구조. 작은 사이즈(16비트, 해시 함수 3개)로 시각화 — 실제는 ~10B 비트 / 7개 해시.

**초기 상태** (전부 0):

```
bit:    0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15
값:     0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
```

**URL 1 추가**: `https://example.com/page-1`

- hash1(url) % 16 = **3**, hash2 = **7**, hash3 = **11** → bit 3, 7, 11 을 1 로 set

```
bit:    0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15
값:     0  0  0  1  0  0  0  1  0  0  0  1  0  0  0  0
                 ▲           ▲           ▲
```

**URL 2 추가**: `https://news.com/article-A`

- hash1 = **2**, hash2 = **7** (이미 1), hash3 = **14**

```
bit:    0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15
값:     0  0  1  1  0  0  0  1  0  0  0  1  0  0  1  0
              ▲  ▲           ▲           ▲     ▲
```

**체크 — 방문한 URL?**

| 조회 URL                           | 해시 결과 | 비트 검사     | 판정                                                   |
| ---------------------------------- | --------- | ------------- | ------------------------------------------------------ |
| `example.com/page-1` (이미 추가됨) | 3, 7, 11  | 모두 1        | **PROBABLY VISITED** → 큐 enqueue 안 함                |
| `newsite.com/blog` (처음)          | 5, 9, 12  | bit 5 = 0     | **DEFINITELY NEW** → 큐 enqueue + bloom filter 에 추가 |
| `random.com/x` (처음)              | 2, 7, 14  | 우연히 모두 1 | **PROBABLY VISITED (오판)** ← false positive           |

**False positive 의미**: 1% FPR 이면 1B URLs 중 ~10M 가 잘못 visited 판정 → 영원히 안 가져옴. 검색엔진 입장에서 감수 (책 §politeness 기본).

**Redis 명령어**:

```
BF.RESERVE visited 0.01 1000000000
  └─ "visited" bloom filter 생성, capacity 1B, FPR 1% (한 번만 실행)

BF.ADD visited "https://example.com/page-1"  →  1   (새로 추가됨)
BF.ADD visited "https://example.com/page-1"  →  0   (이미 있음)

BF.EXISTS visited "https://newsite.com/blog"     →  0   (확실히 없음 → 큐 enqueue)
BF.EXISTS visited "https://example.com/page-1"   →  1   (아마 있음 → 스킵)
```

bit 배열 자체는 Redis 가 관리, Spring Boot 워커는 `BF.ADD` / `BF.EXISTS` 만 호출.

#### Bloom filter 영속성 (회의 §3.4)

**Q: 배치 작업 끝나면 Redis 메모리 휘발 안 되나?**
**A: 휘발 안 됨.** Redis 자체 영속성으로 디스크에 저장:

| 옵션                       | 동작                                       |
| -------------------------- | ------------------------------------------ |
| **AOF (Append-Only File)** | 매 write 명령을 로그로 append (정확, 느림) |
| **RDB snapshot**           | 일정 주기마다 메모리 → 디스크 dump (빠름)  |

배치 다음 회차 시작 시 Redis가 디스크에서 복구 → Bloom filter 그대로 살아남음.

→ 따라서 "이미 방문한 URL은 다음 배치에서도 visited 로 인식"됨. 그래서 **신선도 체크가 필요** (아래 참조).

#### 신선도 체크 (회의 §3.3)

**문제**: URL Seen (Bloom filter) 가 막아버려서, 이미 방문한 URL은 다운로드 자체가 안 됨 → content dedup 검사 기회조차 없음 → **본문이 바뀌어도 영영 모름**.

**해결**: 별도 batch job 으로 "X일 지난 URL은 visited 라도 큐에 강제 enqueue":

```
신선도 batch (X일마다):
  for each URL in visited DB where last_crawled <= now - Xday:
      Frontier 큐에 강제 enqueue
          ↓
      다운로드 → SHA-256 비교
          ├─ KV HIT (본문 그대로) → 저장 안 함, last_crawled 갱신
          └─ KV MISS (본문 변경) → 새 버전 저장
```

**책의 최적화 전략** (Ch.9):

- 페이지 변경 이력 기반 (정적/동적 차등)
- 우선순위 기반 (high-priority 자주)
- HTTP `Last-Modified` / `ETag` (304 Not Modified 시 다운로드 스킵)

> _재방문 주기 NFR-6 미확정._ 책 제시 전략 중 어떤 거 채택할지 다음 회차 결정 필요.

### 3.2 Cache

> _Decisions to make_
> 어디에(앱 / CDN / DB), 무엇을(자주 읽고 안 바뀌는 것), 어떤 전략(cache-aside / write-through / write-around), eviction(LRU + TTL).

> _회의 미논의._ DNS 캐싱은 컴포넌트 다이어그램의 DNS Resolver 에 부분 언급 (캐싱 + 타임아웃 + 지역성).

### 3.3 Bottlenecks & Mitigations

> _Decisions to make_
> 어디가 먼저 깨질지 식별하고 완화책 제시.

> _회의 미논의 — §3.5 Monitoring 에서 식별한 모니터링 대상 (블룸 필터 saturation / 큐 적체 / 파싱 에러 / 중복 처리 race) 과 연계해서 다음 회차 정리 필요._

| Bottleneck | 식별 근거 | 완화 |
| ---------- | --------- | ---- |
|            |           |      |

### 3.4 Failure Modes

> _Decisions to make_
> SPOF, 장애 전파, graceful degradation.

> _회의 미논의._ NFR-4 (안정성) 에서 "악성 입력 / spider trap / 응답 없는 서버 / 거대 페이지 — graceful degradation" 만 큰 그림 수준으로 합의됨. 구체 SPOF / 재시도 / 서킷 브레이커 정책은 다음 회차.

### 3.5 Monitoring & Observability

> _Decisions to make_
> 무엇을 보고 무엇에 알람을 걸 것인가.

회의에서 식별된 모니터링 대상 (회의 §4.2):

| 대상                  | 왜 보는가                                                                                                            |
| --------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **블룸 필터 (Redis)** | 메모리 사용량 / saturation 비율 — 누적 시 false positive rate 폭증 위험 (Bloom filter는 한 번 set된 비트 unset 불가) |
| **컨텐츠 파싱 에러**  | 파싱 에러 발생 빈도 — 처음에는 많이 날 것으로 예상, 로그로 잡아내기                                                  |
| **큐 적체**           | 작업 스레드 처리 속도보다 큐 enqueue 속도가 빠른지 — 비정상 신호                                                     |
| **중복 처리 race**    | 같은 URL이 동시에 여러 워커에 처리되는 경우 — 정합성 문제, 로깅으로 측정                                             |
| **DB 일반**           | (당연히 모니터링 대상)                                                                                               |

> _구체 metric 임계값 / 알람 정책 / 추적(tracing) 도구는 회의에서 미결정._
> _참고: 책의 확장 모듈 "웹 모니터" 는 저작권/상표권 침해 모니터링이라 별개 영역._

### 3.6 Security (선택)

> _Decisions to make_
> 인증/인가, rate limiting, 입력 검증, abuse 방지.

> _회의 미논의._ URL Filter 컴포넌트가 부적절/악성 URL 차단 역할은 함 (NFR-4 안정성). 구체 정책 (악성 URL 블록리스트 / robots.txt 위반 사이트 처리 등) 다음 회차.

---

## 4. Wrap-Up

> _Why this section exists_
> Alex Xu 4단계의 마지막. 3–5분 이내로 압축.

### 4.1 트레이드오프 요약

회의에서 명시적으로 다룬 트레이드오프:

| 결정                | 채택                                         | 거절                       | 이유                                                                            |
| ------------------- | -------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------- |
| 탐색 알고리즘       | **BFS**                                      | DFS                        | 깊이 무한 파고들 위험 (DFS는 웹 크롤러에 부적합)                                |
| 컨텐츠 저장 분리    | **S3 (raw HTML) + NoSQL KV (hash → S3 URL)** | 단일 RDB                   | 1B+ records의 단순 hash lookup. RDB는 인덱스 부담 + write 쓰루풋 한계           |
| URL dedup           | **RedisBloom (Bloom filter)**                | DB hash set                | 1B URL × hash set ≈ 80GB+, Bloom filter는 1.25GB로 가능 (1% FPR 감수)           |
| URL Frontier 큐     | **priority별 분리 + 각 큐 FIFO**             | 단일 PriorityQueue         | 큐 자체로 priority 분리 → 큐 선택기가 가중치로 뽑음. 큐 안에서 추가 정렬 비용 X |
| Bloom filter 영속성 | **Redis AOF/RDB로 디스크 영속**              | 매 배치마다 휘발 후 재구축 | 1B URL 재구축은 비용 폭주. 영속이라야 신선도 체크가 의미 있음                   |

### 4.2 운영 고려사항

- {배포 전략, 마이그레이션, 롤백}

> _회의에서 미논의._

### 4.3 다음 단계 / 확장 방향

회의에서 추가 검토 항목으로 남긴 것:

- **동적 렌더링 지원** (회의 §4.1, 책 161p) — React 기반 클라이언트 사이드 렌더링 사이트가 많아짐. URL Frontier ↔ HTML Downloader 사이에 _렌더러 (headless browser, 예: Puppeteer)_ 추가 검토 필요
- **DNS Resolver 별도 컴포넌트의 필요성 보강** (회의 §3.5) — OS 레벨 DNS 캐싱이 있는데 굳이 별도 리졸버를 만드는 이유 명확화 필요
- **재방문 (신선도) 주기 결정** (NFR-6 미확정) — 책의 3가지 전략 (변경 이력 기반 / 우선순위 기반 / `Last-Modified`+`ETag`) 중 채택안 결정
- **모니터링 임계값 / 알람 정책** — §3.6에 식별된 대상들의 구체 metric 정의
- **확장 모듈 (parser plug-in)** — HTML / PNG / PDF 추출기를 인터페이스 기반으로 분리. 모니터(저작권 침해)도 모듈화

---

## 부록: 자주 빠뜨리는 것 체크리스트

- [x] Non-functional requirement를 **숫자로** 적었는가 (§1.2 NFR-1, NFR-2, §1.3 Scale)
- [x] QPS 추정에 **peak 배수**(×2 ~ ×10)를 곱했는가 (web crawler: ×2 채택)
- [x] Architecture diagram에 **데이터 흐름 방향**이 있는가 (§2.3 전체 흐름 + URL Frontier 내부)
- [x] DB 선택을 **access pattern**으로 정당화했는가 (§3.1: 1B+ records의 단순 hash lookup)
- [ ] Cache의 **eviction 정책**을 명시했는가 _(§3.2 미논의)_
- [ ] **SPOF**를 적어도 1개 식별했는가 _(§3.4 미논의)_
- [ ] 모니터링에 **알람 임계값**이 있는가 _(§3.5 대상은 식별, 임계값 미확정)_
- [x] Wrap-up에서 **트레이드오프**를 1번 더 환기했는가 (§4.1 — 5개)

---

## 참고 자료

- [ByteByteGo — A Framework for System Design Interviews](https://bytebytego.com/courses/system-design-interview/a-framework-for-system-design-interviews)
- [Hello Interview — Delivery Framework](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery)
- [Donne Martin — system-design-primer](https://github.com/donnemartin/system-design-primer)
- [Avinash Billakurthi — Designing a Scalable URL Shortener](https://www.linkedin.com/pulse/designing-scalable-efficient-url-shortener-system-avinash-billakurthi-kwroe/)
- [interviewing.io — 3-Step Framework](https://interviewing.io/guides/system-design-interview/part-three)
