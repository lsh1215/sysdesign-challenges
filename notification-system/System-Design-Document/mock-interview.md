# Notification System — System Design (Mock Interview Style)

> **이 템플릿은 무엇인가**
> 시스템 디자인 모의 인터뷰(45–60분) 시뮬레이션용 골격. ByteByteGo / Hello Interview / Donne Martin system-design-primer가 공통적으로 권장하는 3단계(Clarifying → High Level → Drill Down) + 선택적 Wrap-Up으로 구성. 인터뷰 화이트보드 흐름을 그대로 따라가며 의사결정 근거를 남기는 데 목적이 있다.
>
> **사용법**
> 1. 이 파일을 `<topic>/System-Design-Document/mock-interview.md`로 복사
> 2. 중괄호(`{...}`)와 안내문(`> _Note:_ ...`)을 자기 답으로 대체
> 3. 작성 시간 가이드: Clarifying 5분 / High Level 10–15분 / Drill Down 20–25분 / Wrap-Up 5분

---

## 0. 메타데이터 (선택)

| 항목 | 값 |
|---|---|
| Topic | Notification System (Alex Xu Vol.1 Ch.10) |
| Interviewer Persona | {예: Big Tech L5 / Staff Engineer} |
| Time Budget | 45 / 60 min |
| Date | 2026-05-13 |

---

## 1. Clarifying Questions

> _Why this section exists_
> 문제 범위를 좁히고 인터뷰어와 공통 전제를 맞춘다. **너무 빨리 설계로 넘어가는 것이 가장 흔한 실패 원인.** 5분 이내, 3–5개 질문이 적정.

### 1.1 Functional Requirements
> _What goes here_
> "유저는 …할 수 있어야 한다" 형태로 핵심 기능 3개에 집중. 부가 기능은 나중에.

- [x] FR-1: 클라이언트 애플리케이션 또는 서버측 스케줄러가 알림을 발송할 수 있다 (발송 주체 2종)
- [x] FR-2: 푸시(iOS/Android), SMS, 이메일 3개 채널로 알림을 전송할 수 있다
- [x] FR-3: 사용자가 채널별로 알림 수신 거부(opt-out)를 설정할 수 있다
- [x] FR-4: 한 사용자가 여러 기기에 로그인한 경우 모든 기기에 알림을 보낸다 (multi-device)

### 1.2 Non-Functional Requirements (정량화 필수)
> _What goes here_
> "낮은 지연" 같은 형용사가 아니라 숫자로. availability / consistency / latency / scalability / durability 중 시스템에 맞는 top 3–5개를 골라 수치 명시.

- [x] NFR-1: 연성 실시간(soft real-time) — strict latency SLA 없음, 부하 시 약간의 지연 허용
- [x] NFR-2: 처리량 — 평균 185 req/sec sustained, 버스트 요청 흡수 가능
- [x] NFR-3: SPOF 없음 — 단일 컴포넌트 장애가 전체 알림 시스템 중단으로 번지지 않을 것
- [x] NFR-4: 확장성 — 새 3자 서비스 채널 추가 시 기존 시스템 재설계 불필요
- [x] NFR-5: 데이터 유실 최소화 — at-least-once + idempotency로 사실상 once에 근접

### 1.3 Scale & Constraints
> _What goes here_
> 트래픽 규모와 데이터 특성. 이 숫자가 뒤에 나올 추정·샤딩·캐시 결정의 근거가 된다.

| 항목 | 값 | 근거 |
|---|---|---|
| 일일 알림 총량 | 16M / day | Push 10M + SMS 1M + Email 5M (Alex Xu p.166) |
| 평균 페이로드 크기 | 500 KB | 이메일 HTML 템플릿 고려, 이미지 제외 |
| 데이터 보존 기간 | 14 days (알림 로그) | RAW 로그 기준, 비즈니스 가공 필요 시 별도 |
| DAU | {- 논의되지 않음 -} | |
| 글로벌 여부 | {- 논의되지 않음 -} | |

### 1.4 Out of Scope (명시적 제외)
> _Why_
> 인터뷰어와 합의해 시간을 사야 한다. "인증/결제/관리자 도구는 다음 단계로 두자"고 못 박는다.

- [x] Strict ordering — soft real-time + AP 시스템이라 메시지 간 순서 보장 X
- [x] Exactly-once delivery — 네트워크 단절로 ack 누락 시 중복 불가피, at-least-once + idempotency로 대체
- [x] Sub-100ms latency SLA — soft real-time 시스템이라 latency SLA 자체를 정의하지 않음
- [x] In-app notification (앱 내 알림센터) — 별도 시스템으로 분리
- [x] 알림 템플릿 시스템 깊이 — 별도 서비스로 분리 가능 정도만 언급
- [x] 보안 — 우선순위 낮음으로 본 회의에서 제외

---

## 2. High Level Design

> _Why this section exists_
> 전체 시스템의 뼈대를 스케치하고 인터뷰어의 동의를 얻는다. 박스/화살표가 없으면 안 된다 — 말로만 설명하지 말 것.

### 2.1 Back-of-the-Envelope Estimation
> _What goes here_
> 설계 결정을 정당화하는 숫자. 5분 초과 금지. 단위 표기 필수.

| Metric | 계산 | 값 |
|---|---|---|
| QPS (avg) | 16,000,000 ÷ 86,400 (= 60×60×24) | ≈ 185 / sec |
| QPS (peak) | {- peak 배수 논의되지 않음 -} | {-} |
| 알림 로그 14일 저장량 | 16M × 500KB × 14 days | ≈ 100~112 TB |
| Device 테이블 row | DAU 수준 × 평균 디바이스 수 | {- 정량적으로 논의되지 않음 -} |

### 2.2 Core Entities (Data Model 초안)
> _What goes here_
> 핵심 명사만 나열. 컬럼은 5개 이내로 시작. 풀 스키마는 Drill Down에서.

- **User**: id, opt_in_global, (최소 정보)
- **Device**: id, user_id (FK), device_token, opt_in_per_channel
- **NotificationLog**: id, payload (JSON, device_token 포함), status (PENDING/SENT/FAILED/DEAD)

### 2.3 API Contract
> _What goes here_
> Functional Requirement 1:1 매핑. REST 기본. 인증 토큰은 헤더.

{- 회의에서 "키가 많지 않을 것 같아서 생략"으로 패스됨. 향후 SDD §8에서 보강 예정 -}

### 2.4 Architecture Diagram
> _What goes here_
> 클라이언트 → LB → API → 캐시/DB/큐의 박스+화살표. ASCII가 가장 빠르다.

```
[클라이언트 마이크로서비스]     ← 책의 "알림 제공자" (결제/배송/쇼핑 등)
        │ 알림 요청 (페이로드)
        ▼
[알림 서버 (scale-out)]         ← Rate Limit, opt-out 체크
        │   ├── User DB / Device DB / 토큰 정보
        │   └── (Redis 캐시 — 도입 가능)
        ▼
[Kafka — 채널별 토픽]
        ├── push-ios
        ├── push-android
        ├── sms
        └── email
        │
        ▼
[Worker — 채널별 분리]          ← 알림 로그 DB 기록, ack/nack
        ├── APNs Worker
        ├── FCM Worker
        ├── Twilio Worker
        └── SendGrid Worker
        │
        ▼
[제3자 서비스]
        ├── APNs (iOS)
        ├── FCM (Android)
        ├── Twilio (SMS)
        └── SendGrid (Email)
        │
        ▼
[클라이언트 기기]
```

---

## 3. Drill Down

> _Why this section exists_
> Non-functional requirement 중 High Level이 아직 충족 못한 것을 메우는 단계. **시니어는 인터뷰어 힌트를 기다리지 않고 직접 병목을 찾아 제안한다.** 모든 영역을 고르게 파지 말고 가장 중요한 1–2개를 깊게.

### 3.1 Database
> _Decisions to make_
> SQL vs NoSQL, 샤딩 전략, 복제 토폴로지, 인덱스, 파티셔닝.

**알림 서버 DB (User / Device)**
- **선택**: RDB (정규화 관계형, OLTP)
- **선택 근거**: device token 조회는 read-heavy + 사용자 데이터 영구 보관, 캐시 앞단 가능
- **관계**: User : Device = 1 : N (한 사용자가 여러 기기 로그인 가능, Instagram 시나리오)

**알림 로그 DB (NotificationLog)**
- **선택**: NoSQL 가능 (회의 합의 — "로그는 NoSQL로 가도 되고", trans. line 209)
- **선택 근거**: write-heavy + append-like + TTL 14일 + 대용량 (16M row/day)
- **샤딩 키 / 구체 제품**: 회의 미합의. Cassandra(partition key=user_id, clustering=created_at desc, TTL native)는 사전학습 §Q3-2 예시.

**스키마 (논의된 필드)**
```
User
  id              PK
  opt_in_global   BOOLEAN     ← 알림 전체 수신 여부
  (최소 정보)

Device
  id              PK
  user_id         FK → User.id
  device_token    VARCHAR     ← 3자 서비스 호출용 토큰
  opt_in_per_channel          ← push/sms/email별 정밀 제어

NotificationLog
  id              UUID PK     ← idempotency key 겸용
  payload         JSON        ← 본문 + 부가정보 (device_token 포함)
  status          ENUM        ← PENDING / SENT / FAILED / DEAD
```

- **복제**: {- 논의되지 않음 -}
- **인덱스**: {- 논의되지 않음 -}

### 3.2 Cache
> _Decisions to make_
> 어디에(앱 / CDN / DB), 무엇을(자주 읽고 안 바뀌는 것), 어떤 전략(cache-aside / write-through / write-around), eviction(LRU + TTL).

- **레이어**: 회의에서는 "캐시도 넣을 수 있다" 정도의 도입 **가능성**만 언급 (trans. line 209-211, 403-407). Redis는 회의에서 rate limit 예시 발언 시 등장 ("레디스를 쓴다면 키발류로").
- **캐싱 대상 1 (도입 시)**: 알림 서버 DB 조회 가속 — 185 QPS 처리 시 device token / user opt-out lookup
- **캐싱 대상 2 (도입 시)**: Rate Limit 카운터 (key=user_id, value=전송 횟수)
- **전략**: {- cache-aside / write-through 등 구체 전략 논의되지 않음 -}
- **Eviction**: {- 논의되지 않음 -}
- **Invalidation**: {- 논의되지 않음 -}

### 3.3 Load Balancer
> _Decisions to make_
> L4(빠름) vs L7(라우팅 규칙), sticky session 필요성, health check.

{- 본 회의에서 LB 구체 설계는 논의되지 않음 -}

### 3.4 Bottlenecks & Mitigations
> _Decisions to make_
> 어디가 먼저 깨질지 식별하고 완화책 제시.

| Bottleneck | 식별 근거 | 완화 |
|---|---|---|
| 네트워크 / 3자 서비스 외부 통제 불가 | APNs/FCM/Twilio/SendGrid rate limit 제각각, 외부 장애 시 우리 워커까지 영향 | **채널별 Kafka 토픽 + 채널별 Worker 분리** — 한 채널 장애가 다른 채널로 전파 X |
| 워커 과부하 (3자 서비스 다운 시) | FCM 다운 → FCM 워커에 처리량 적체 | 채널 분리로 격리, 큐 모니터링으로 재시도/DLT 라우팅 |
| 버스트 요청 | 짧은 시간 폭발적 알림 (예: 미스터비스트 구독자 수억 명 동시 알림) | Kafka 큐가 버퍼 역할 + 알림 서버 단 Rate Limit |
| 큐 적체 지속 | 소비 속도 < 생산 속도 | 컨슈머 scale-out 또는 알림 서버 Rate Limit 강화 |

### 3.5 Failure Modes
> _Decisions to make_
> SPOF, 장애 전파, graceful degradation.

- **SPOF 방지**:
  - 알림 서버 — scale-out (다수 인스턴스)
  - Kafka 토픽 — 채널별 분리로 한 채널 장애 격리
  - Worker — 채널별 분리, 각각 독립 scale
- **메시지 손실 방지**:
  - 인메모리 큐가 아닌 **Kafka 영속 토픽** 사용 — 워커 다운 시 메시지 보존
  - 알림 로그 DB에 PENDING/SENT/FAILED/DEAD 상태 기록
- **재시도 전략** (Kafka retry topic + DLT):
  ```
  Worker
    ├─ 메시지 수신 → notification_log INSERT(PENDING)   ← idempotency 체크 겸
    ├─ 3자 서비스 호출
    │     ├─ 성공 → notification_log UPDATE(SENT) → Kafka ack (offset commit)
    │     └─ 실패 → notification_log UPDATE(FAILED, retry_count++)
    │              → Kafka retry topic 으로 produce + 원본 offset commit
    └─ retry_count > 임계치 → notification_log UPDATE(DEAD) → DLT 로 produce
                                                                  ↑
                                                          개발자 수동 처리 / 스케줄러 재시도
  ```
- **Kafka ack 의미**: "비즈니스 로직 처리 완료" (3자 서비스 호출 결과 기반). 단순 수신 여부가 아님.
- **중복 방지**: notification_id를 페이로드에 박아서 dedup key로 사용. 알림 로그 DB의 status 확인.
- **이중 보장 결론**: exactly-once는 거의 불가능하지만, Kafka retry/DLT + 알림 로그 status로 **at-least-once + idempotency**를 통해 사실상 once에 근접.

### 3.6 Monitoring & Observability
> _Decisions to make_
> 무엇을 보고 무엇에 알람을 걸 것인가.

- **큐 모니터링**: Kafka 토픽별 적체량 — 버스트 시 일시 적체는 OK, **지속적으로 컨슘 속도 < 생산 속도면 위험 신호**
- **이벤트 추적**: sent / delivered / failed 이벤트 추적 (책 후반 언급, 분석 파이프라인으로)
- **Metrics / Tracing / Alerts**: {- 구체 임계값 / OpenTelemetry 등 도구 선택은 논의되지 않음 -}

### 3.7 Security (선택)
> _Decisions to make_
> 인증/인가, rate limiting, 입력 검증, abuse 방지.

**Rate Limit** (보안 영역으로 함께 정리)

- **위치**: **알림 서버 단** (컨슈머 단 X)
  - 근거: Kafka 큐가 이미 버퍼 역할로 컨슈머 보호, 입구에서 막는 게 효율적
- **방식**:
  - API별 (특정 클라이언트 서비스 호출 제한)
  - IP별 (호출 IP throttling, DoS 방지 형태)
  - 컨테이너별 (Kubernetes 환경 시)
  - 토큰 버킷 알고리즘 + 사용자별 처리량 제한 (Redis key-value 카운터)
- **사례 (미스터비스트 시나리오)**: 구독자 수억 명에게 동시 발송 시, 한 채널의 알림이 전체 큐를 점유해 다른 알림을 막을 수 있음 → 입구에서 제한 필수

**기타 보안 (인증/인가, TLS, 페이로드 암호화 등)**: {- 본 회의에서 우선순위 낮음으로 제외 -}

---

## 4. Wrap-Up

> _Why this section exists_
> Alex Xu 4단계의 마지막. 3–5분 이내로 압축.

### 4.1 트레이드오프 요약

- **AP 선택 (vs CP)**: 강한 일관성/순서 보장을 포기하고 가용성/확장성 우선. soft real-time 시스템 본질에 부합. strict ordering 필요 시 별도 인프라(FIX gateway 등) 책임.
- **채널별 분리 (vs 단일 워커 scale-out)**: 인프라 복잡도 증가 (토픽 4종 + 워커 4종) 감수하고 디커플링 + 추적성 확보. 한 채널 장애가 전체로 번지지 않음.
- **Kafka retry topic + DLT + 알림 로그 status 하이브리드 (vs DB 폴링 단독)**: 운영 복잡도 증가, 대신 재시도 throughput 확보 + 감사/idempotency 분리.
- **Rate Limit at 알림 서버 (vs 컨슈머)**: 컨슈머는 Kafka 버퍼 뒤에 있어 직접 보호 불필요, 입구에서 막는 게 자원 절약.

### 4.2 운영 고려사항

{- 배포 전략 / 마이그레이션 / 롤백은 본 회의에서 논의되지 않음. SDD §16에서 보강 예정 -}

### 4.3 다음 단계 / 확장 방향

- API 설계 보강 (회의에서 생략된 부분)
- 알림 템플릿 시스템 분리
- 이벤트 추적 → 분석 파이프라인 (Kafka analytics topic + DW)
- 보안 layer (인증/인가, TLS, 페이로드 암호화)
- 글로벌/멀티 리전, 타임존별 발송 시각 처리

---

## 부록: 자주 빠뜨리는 것 체크리스트

- [x] Non-functional requirement를 **숫자로** 적었는가 (185 QPS, 100TB, 14days)
- [ ] QPS 추정에 **peak 배수**(×10)를 곱했는가 — *논의 안 됨*
- [x] Architecture diagram에 **데이터 흐름 방향**이 있는가
- [x] DB 선택을 **access pattern**으로 정당화했는가 (User/Device read-heavy, Log write-heavy)
- [ ] Cache의 **eviction 정책**을 명시했는가 — *논의 안 됨*
- [x] **SPOF**를 적어도 1개 식별했는가 (알림 서버 단일 → scale-out)
- [ ] 모니터링에 **알람 임계값**이 있는가 — *큐 적체만 정성적 언급*
- [x] Wrap-up에서 **트레이드오프**를 1번 더 환기했는가

---

## 참고 자료

- [ByteByteGo — A Framework for System Design Interviews](https://bytebytego.com/courses/system-design-interview/a-framework-for-system-design-interviews)
- [Hello Interview — Delivery Framework](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery)
- [Donne Martin — system-design-primer](https://github.com/donnemartin/system-design-primer)
- Alex Xu — System Design Interview Vol.1 Ch.10 (Notification System, pp.165–184)
- 사전 학습 로그: `../conversation-log/2026-05-12.log`
- 회의 정리: `../study-notes/meeting-summary.md`
