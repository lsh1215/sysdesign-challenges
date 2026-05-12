# Notification System — Software Design Document

> **이 템플릿은 무엇인가**
> 엔지니어링 팀이 실무에서 쓰는 정통 Software Design Document(SDD) 골격. IEEE 1016-2009 학술 구조 + Atlassian 실무 가이드 + CMS 엔터프라이즈 항목 + Notion 모던 메타데이터를 통합한 형태. 모의 인터뷰 스타일이 "사고 흐름 기록"이라면, 이 문서는 **결정의 근거와 추적 가능성**을 남기는 "팀 자산"이다.
>
> **사용법**
> 1. 이 파일을 `<topic>/System-Design-Document/sdd.md`로 복사
> 2. 중괄호(`{...}`)와 안내문(`> _Note:_ ...`)을 자기 답으로 대체
> 3. 작성 순서 권장: Metadata → Introduction → Goals/Non-Goals → Constraints → Architecture → Data → Component → Interface → NFR → Cross-cutting → Decisions → Alternatives → Risk → Traceability → Testing → Rollout → Glossary
> 4. 너무 격식이 부담되면 Risk Register / Traceability / Appendix는 생략 가능 (※ "선택" 표시 섹션)

---

## 0. Document Metadata

| 항목 | 값 |
|---|---|
| Document Title | Notification System SDD |
| Version | 0.1 (Draft) |
| Status | Draft |
| Author(s) | 작성자 (스터디 진행) |
| Reviewer(s) | {- 미지정 -} |
| Participants (회의) | 종민, 보성, 윤기, 민규 |
| Last Updated | 2026-05-13 |
| Related Documents | `mock-interview.md`, `../study-notes/meeting-summary.md`, `../study-notes/meeting-transcript.md`, `../conversation-log/2026-05-12.log` |

### 0.1 Revision History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1 | 2026-05-13 | 작성자 | 회의 결과 + 사전 학습 정리 기반 초안 |

---

## 1. Introduction

### 1.1 Purpose

본 문서는 **다채널 알림 시스템(Notification System)** 의 설계를 기술한다. 다양한 내부 클라이언트 서비스(결제/배송/쇼핑 등)가 사용자에게 푸시(iOS/Android), SMS, 이메일 알림을 안정적으로 전송할 수 있도록 한다. 대상 독자는 backend 엔지니어, SRE, 알림 시스템을 호출하는 도메인 서비스 개발자.

### 1.2 Scope

- **In scope**: 알림 서버, 채널별 Kafka 토픽, 채널별 워커, 알림 로그 DB, 알림 서버 DB (User/Device), Rate Limit, 재시도/DLT 흐름, opt-out 처리
- **Out of scope**:
  - In-app notification (앱 내 알림센터)
  - 알림 템플릿 시스템 상세 (별도 서비스로 분리 가능)
  - 보안 layer (인증/인가, TLS, 페이로드 암호화) — 본 회의 우선순위 외
  - 글로벌/멀티 리전, 타임존별 발송 시각 처리
  - Strict ordering 보장 (별도 trading-infra 도메인)
  - Exactly-once delivery (at-least-once + idempotency로 대체)
  - API 설계 상세 — 추후 보강

### 1.3 References

- Alex Xu, *System Design Interview Vol.1*, Ch.10 — Notification System (pp.165–184)
- 사전 학습 로그: `../conversation-log/2026-05-12.log` (Q1~Q11 사전 정리)
- 회의록 / 정리: `../study-notes/meeting-{transcript,summary}.md`
- mock-interview: `mock-interview.md`

---

## 2. System Overview

본 시스템은 내부 클라이언트 서비스로부터 알림 요청을 받아 푸시(iOS/Android), SMS, 이메일 채널로 안정적으로 전송한다. 책의 용어로는 "알림 제공자(notification provider)"가 결제/배송/쇼핑 같은 클라이언트 마이크로서비스를 의미하고, 본 시스템은 이들이 보낸 알림 요청을 받아 외부 제3자 서비스(APNs/FCM/Twilio/SendGrid)로 라우팅하는 미들 레이어 역할을 한다. 채널별 디커플링과 Kafka 기반 비동기 전송으로 단일 채널 장애가 전체 시스템으로 번지지 않도록 격리한다.

```
┌─ External (내부 클라이언트) ─┐    ┌─ This System ──────┐    ┌─ External (3rd party) ─┐
│ 결제 / 배송 / 쇼핑 서비스    │ →  │ Notification System │ →  │ APNs / FCM /           │
│ (책의 "알림 제공자")          │    │                     │    │ Twilio / SendGrid      │
└──────────────────────────────┘    └─────────────────────┘    └────────────────────────┘
                                                                          │
                                                                          ▼
                                                                   [클라이언트 기기]
```

---

## 3. Goals and Non-Goals

### 3.1 Goals

- G-1: 일일 16M 알림(Push 10M + SMS 1M + Email 5M) 전송 — avg ≈ 185 req/sec, peak ≈ 370 req/sec (×2)
- G-2: 채널별 격리 — 한 채널(3자 서비스)의 장애가 다른 채널로 전파되지 않음
- G-3: 데이터 유실 최소화 — at-least-once 전송 + idempotency로 사실상 once 보장
- G-4: 채널 확장성 — 새 3자 서비스 추가 시 기존 시스템 재설계 불필요
- G-5: 사용자 단/디바이스 단/채널 단 opt-out 제어

### 3.2 Non-Goals

- NG-1: Strict ordering 보장 — soft real-time + AP 시스템으로, 메시지 간 순서는 보장하지 않음
- NG-2: Exactly-once delivery — 네트워크 단절로 ack 누락 시 중복 불가피, 통제 불가
- NG-3: Sub-100ms latency SLA — soft real-time이라 latency SLA 자체를 정의하지 않음
- NG-4: In-app notification — 별도 시스템 영역
- NG-5: 알림 템플릿 정교화 — 별도 서비스로 분리

---

## 4. Constraints

### 4.1 Technical Constraints

- 외부 3자 서비스(APNs/FCM/Twilio/SendGrid)의 **rate limit이 채널마다 제각각** — 우리가 통제 불가
- 외부 3자 서비스 자체가 본질적으로 unordered delivery (특히 APNs/FCM)

### 4.2 Organizational / Business Constraints

{- 본 회의에서 논의되지 않음 -}

### 4.3 Regulatory / Compliance Constraints

{- 본 회의에서 논의되지 않음 -}

---

## 5. System Architecture

### 5.1 Architectural Style

**비동기 이벤트 기반 아키텍처 (Kafka 중심)** + **채널별 컴포넌트 분리**.

- 클라이언트 서비스 → 알림 서버: {- 프로토콜 회의에서 생략 ("키가 많지 않아서 생략", trans. line 311) -}
- 알림 서버 → 워커: **Kafka 토픽** (채널별 분리)
- 워커 → 3자 서비스: 각 3자 서비스 API 호출 (구체 프로토콜/SDK는 회의에서 논의되지 않음)

**선택 근거**:
- 디커플링: 알림 서버는 알림 요청만 받으면 빨리 응답, 실제 전송은 워커가 비동기로
- 재시도 가능: Kafka retry topic 활용
- 채널별 격리: 한 채널 장애가 다른 채널로 전파되지 않음
- 데이터 손실 방지: 인메모리 큐가 아닌 영속 토픽

### 5.2 Component Diagram

```
[클라이언트 마이크로서비스]      ← 책의 "알림 제공자"
   (결제, 배송, 쇼핑 등)
        │ HTTP POST (알림 요청 + payload)
        ▼
┌─────────────────────────────────┐
│  알림 서버 (Notification Server) │ ← scale-out
│  ├─ Rate Limit (token bucket)    │
│  ├─ opt-out 체크                  │
│  └─ payload → Kafka produce      │
└─────────────────────────────────┘
        │
        │ (참조)
        ▼
[알림 서버 DB]  ┐
   User        │   [Redis 캐시]  (도입 가능성 — 회의 미확정)
   Device      │   - device token 조회 가속
               │   - rate limit 카운터
               ▼
        │
        ▼ produce
[Kafka — 채널별 토픽]
   ├─ push-ios
   ├─ push-android
   ├─ sms
   ├─ email
   ├─ {channel}-retry-{backoff}    ← 재시도 토픽
   └─ {channel}-dlt                  ← Dead Letter Topic
        │
        ▼ consume
┌─────────────────────────────────┐
│  Worker (채널별 분리)             │
│  ├─ notification_log INSERT/UPDATE│
│  ├─ 3자 서비스 호출                │
│  └─ ack / retry / DLT 라우팅      │
└─────────────────────────────────┘
   ├─ APNs Worker
   ├─ FCM Worker
   ├─ Twilio Worker
   └─ SendGrid Worker
        │
        │ (기록)
        ▼
[알림 로그 DB (NotificationLog)]
        │
        ▼ HTTP
[제3자 서비스]
   ├─ APNs (iOS)
   ├─ FCM (Android)
   ├─ Twilio (SMS)
   └─ SendGrid (Email)
        │
        ▼
[클라이언트 기기]
```

### 5.3 Deployment Topology

{- Runtime / Region / Network 등 배포 토폴로지는 본 회의에서 논의되지 않음. 향후 Constraints 확정 후 보강 -}

---

## 6. Data Design

### 6.1 Data Model / ERD

```
┌──────────────┐  1   N  ┌──────────────────┐
│    User      │ ────── │      Device       │
│──────────────│         │───────────────────│
│ id (PK)      │         │ id (PK)           │
│ opt_in_global│         │ user_id (FK)      │
│ (최소 정보)   │         │ device_token      │
└──────────────┘         │ opt_in_per_channel│
                         └──────────────────┘

┌────────────────────────┐
│   NotificationLog       │   (분리된 storage, 별도 서비스 책임)
│─────────────────────────│
│ id (UUID PK)            │  ← idempotency key 겸용
│ payload (JSON)          │  ← device_token, 메시지 본문, 부가정보 포함
│ status (ENUM)           │  ← PENDING / SENT / FAILED / DEAD
└────────────────────────┘
```

### 6.2 Data Dictionary

| Entity | Field | Type | Constraint | Description |
|---|---|---|---|---|
| User | id | (PK) | NOT NULL | 사용자 식별자 |
| User | opt_in_global | BOOLEAN | | 전체 알림 수신 여부 |
| Device | id | (PK) | NOT NULL | 디바이스 식별자 |
| Device | user_id | FK → User.id | NOT NULL | |
| Device | device_token | VARCHAR | NOT NULL | 3자 서비스 호출용 토큰 |
| Device | opt_in_per_channel | BOOLEAN/JSON | | push/sms/email 채널별 수신 설정 |
| NotificationLog | id | UUID | PK | idempotency key 겸용 |
| NotificationLog | payload | JSON | NOT NULL | 본문 + 부가정보 (device_token, channel 포함) |
| NotificationLog | status | ENUM | NOT NULL | PENDING / SENT / FAILED / DEAD |

> _Note:_ User의 `email` / `name` 등 일반 사용자 정보 필드는 본 회의에서 "최소 정보, 시스템 설계라 깊이 X"로 정리되어 명시 안 됨.

### 6.3 Data Lifecycle

- **Creation**:
  - User / Device: 사용자 가입 / 디바이스 로그인 시점 (외부 인증 시스템 책임)
  - NotificationLog: 워커가 메시지 수신 시 PENDING 상태로 INSERT
- **Retention**:
  - User / Device: 영구 (사용자 데이터)
  - NotificationLog: **14일** — 회의 reasoning: "비즈니스가 데이터 가공해서 재활용한다면 1년, 단순 raw 로그면 1주~1달. 1년은 과함, 2주(14일)로 결정" (trans. line 51-67). NoSQL 선택 시 TTL native 지원 활용 가능.
- **Archival**: {- 논의되지 않음 -}
- **Deletion**: NotificationLog는 TTL 만료 시 자동 삭제

### 6.4 Data Flow

#### Flow 1: 정상 알림 전송 (happy path)
```
[클라이언트 서비스]
    │ POST 알림 요청
    ▼
[알림 서버]
    │ ① Rate Limit 체크 (Redis 토큰 버킷)
    │ ② Device DB 조회 (device_token, opt-out 체크)
    │ ③ Kafka 채널별 토픽 produce
    ▼
[Kafka push-ios 토픽]
    │ consume
    ▼
[APNs Worker]
    │ ① NotificationLog INSERT (PENDING, notification_id)
    │ ② APNs 호출
    │ ③ 성공 → NotificationLog UPDATE (SENT)
    │ ④ Kafka offset commit (= ack)
    ▼
[APNs]
    │
    ▼
[iOS 기기]
```

#### Flow 2: 실패 + 재시도
```
[APNs Worker]
    │ APNs 호출 실패
    │ ① NotificationLog UPDATE (FAILED, retry_count++)
    │ ② {channel}-retry-{backoff} 로 produce
    │ ③ 원본 offset commit
    ▼
[{channel}-retry-{backoff}] → (5초 후 consume) → 재시도
    │ 또 실패
    ▼
[{channel}-retry-{backoff_N}] → 재시도
    │ retry_count > 임계치
    ▼
[{channel}-dlt]    ← 자동 처리 중단, 모니터링/운영자가 처리
    │
    └─ NotificationLog UPDATE (DEAD)
```

---

## 7. Component Design

### 7.1 Component: Notification Server (알림 서버)

- **Responsibility**: 클라이언트 서비스로부터 알림 요청을 받아 채널별 Kafka 토픽으로 라우팅. 회의에서 합의된 책임:
  - Rate Limit 적용 (§7.4 참조)
  - User / Device 정보 조회 (opt-out 확인, device token 사용)
  - 채널별 Kafka 토픽 produce
- **Inputs**: 클라이언트 서비스의 알림 요청 (request schema는 §8.1 미정)
- **Outputs**: Kafka produce to `push-ios` / `push-android` / `sms` / `email` 중 적절한 토픽
- **Dependencies**:
  - User / Device DB (opt-out 체크, device_token 조회)
  - Redis (Rate Limit 카운터, 캐시 — 도입 가능성으로 회의 언급, 확정은 X)
  - Kafka producer
- **Core Logic / 처리 절차**: {- 알림 서버 내부 처리 순서 (인증 → rate limit → opt-out → device fan-out → 토픽 라우팅 등) 는 회의에서 구체적으로 합의되지 않음. 위 책임 항목 수준에서만 정의됨 -}
- **Scale**: scale-out (다수 인스턴스 — SPOF 방지)

### 7.2 Component: Channel Worker (채널별 워커)

- **Responsibility**: Kafka 채널 토픽 consume → 3자 서비스 호출 → 상태 기록 → ack/retry/DLT 라우팅
- **Inputs**: Kafka message (payload, notification_id)
- **Outputs**:
  - 3자 서비스 API 호출
  - NotificationLog DB write (PENDING/SENT/FAILED/DEAD)
  - Kafka produce (retry topic or DLT)
- **Dependencies**:
  - Kafka consumer / producer
  - NotificationLog DB
  - 3자 서비스 클라이언트 (APNs / FCM / Twilio / SendGrid)
- **Core Logic** (회의 합의 단순 흐름):
  ```
  메시지 수신
    → 알림 로그 DB에 상태 기록 (PENDING)        ※ INSERT 시점 — 사전학습 §Q3-3 표준 패턴 차용,
                                                  회의에서 시점은 명시 합의 X
    → 3자 서비스 호출
        ├─ 성공 → 알림 로그 UPDATE(SENT) + Kafka offset commit (ack)
        └─ 실패 → 알림 로그 UPDATE(FAILED, retry_count++)
                  → retry topic으로 produce + 원본 offset commit
    → retry_count 임계치 초과 → 알림 로그 UPDATE(DEAD) → DLT로 produce
  ```
  {- 의사코드 상세 (RetriableException / NonRetriableException 분기 등)은 사전학습 §Q4 (conversation-log)에서 다룬 일반 패턴이며, 회의에서는 단일 실패/재시도/DLT 단계만 합의 -}
- **Ack semantics**: Kafka offset commit = "비즈니스 로직 처리 완료" 의미. 단순 수신 X. (회의 합의, trans. line 245-249)
- **Scale**: 채널별 독립 scale-out

### 7.3 Component: Notification Log Store

- **Responsibility**: 알림 전송 라이프사이클(PENDING → SENT/FAILED/DEAD) 영속화, idempotency / 감사 / 재시도 추적
- **Storage**: NoSQL 가능 (Cassandra 등) — write-heavy, append-like, 14일 TTL
- **Access pattern**:
  - INSERT: 워커가 메시지 수신 직후 PENDING
  - UPDATE: 워커가 3자 서비스 호출 결과에 따라 상태 전이
  - SELECT: "특정 user의 특정 시간대 알림" (감사용), "이 notification_id 이미 처리됐나" (dedup)

### 7.4 Component: Rate Limiter

- **Responsibility**: 알림 서버 단에서 입구 throttling
- **위치**: 알림 서버 내부 (컨슈머 단 X — Kafka 큐가 이미 컨슈머 보호)
- **Strategy**: 토큰 버킷 알고리즘
- **Dimensions**:
  - API별
  - IP별
  - Kubernetes container별
  - 사용자별 (key=user_id, value=전송 횟수 in Redis)
- **사례**: 미스터비스트 시나리오 — 구독자 수억 명 동시 발송이 다른 알림을 막지 않도록 입구 차단

---

## 8. Interface Design

### 8.1 External APIs

{- 본 회의에서 "키가 많지 않아서 생략"으로 패스됨. 다음 항목들은 향후 보강:
  - `POST /api/v1/notifications` (알림 발송)
  - `PUT /api/v1/users/{id}/opt-out` (수신 거부 설정)
  - request/response schema, 에러 코드, 인증/인가 -}

### 8.2 Internal Service Interfaces

**Kafka 토픽 (채널별)**

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `push-ios` | Notification Server | APNs Worker | iOS 푸시 알림 메인 |
| `push-android` | Notification Server | FCM Worker | Android 푸시 알림 메인 |
| `sms` | Notification Server | Twilio Worker | SMS 메인 |
| `email` | Notification Server | SendGrid Worker | 이메일 메인 |
| `{channel}-retry-{backoff}` | Worker (실패 시) | Worker (재시도) | 백오프 단계별 재시도 (예: 5s/30s/5m) |
| `{channel}-dlt` | Worker (재시도 한계 초과) | 운영자 / Admin Tool | 자동 처리 포기, 수동 재처리 |

**Payload (예시)**
```json
{
  "notification_id": "uuid-...",     // idempotency key
  "user_id": "...",
  "channel": "PUSH_IOS",
  "device_token": "...",
  "content": { ... },
  "metadata": { ... }
}
```

### 8.3 UI Flow

{- 알림 시스템은 UI 없음 -}

---

## 9. Non-Functional Requirements

| Category | Requirement | Acceptance Criteria |
|---|---|---|
| Performance | Throughput | ≥ 185 req/sec sustained (avg), peak ≈ 370 req/sec (avg × 2), 버스트 흡수 가능 |
| Scalability | 채널 확장 | 새 3자 서비스 채널 추가 시 기존 컴포넌트 변경 불필요 (토픽 + 워커만 추가) |
| Availability | SPOF | 단일 컴포넌트 장애가 전체 알림 시스템 중단을 유발하지 않음 |
| Reliability | Delivery | at-least-once + idempotency (notification_id) → 사실상 once |
| Consistency | CAP | **AP** — soft real-time, strict ordering 불요 |
| Storage | 알림 로그 | 14일 보관 (≈ 100~112 TB) |
| Performance | Latency | Soft real-time (구체 SLA 미정의) |
| Security | 인증 / 암호화 | {- 본 회의에서 우선순위 외, 미정의 -} |
| Observability | 모니터링 | 큐 적체량 추적 (소비 < 생산 지속 시 알람) |
| Observability | 이벤트 추적 | sent / delivered / failed 이벤트 (분석 파이프라인) — 상세 미정의 |

---

## 10. Cross-Cutting Concerns

### 10.1 Security

{- 본 회의에서 우선순위 낮음으로 제외. 향후 보강 영역:
  - 클라이언트 서비스 ↔ 알림 서버 인증/인가
  - 3자 서비스 인증 키 관리 (KMS / Vault)
  - 페이로드 PII 처리, 저장 시 암호화
  - TLS 전송 암호화 -}

### 10.2 Observability

- **큐 모니터링**: Kafka 채널별 토픽 적체량 — 일시적 버스트 적체는 OK, **지속적 생산 > 소비 시 alarm**
- **이벤트 추적**: sent / delivered / failed 이벤트 분석 파이프라인 (책 후반 항목, 구체 도구 미정)
- **DLT 모니터링**: DLT 토픽 lag / message count — 임계치 초과 시 운영자 alert (구체 도구는 회의 미정. 사전학습 §Q6 예시: PagerDuty)
- **Metrics / Logs / Traces / Alerts 구체 임계값**: {- 본 회의에서 미정의 -}

### 10.3 Resilience

- **재시도 전략**: Kafka retry topic + 백오프 (회의에서 "5번/3번 정도" 재시도 횟수만 합의, 백오프 시간 단계는 미정 — 사전학습 §Q5/Q6 예시 5s/30s/5m 참고)
- **DLT**: 재시도 한계 초과 메시지 보관 → 자동 처리 중단, 수동 재처리
- **이중 보장**: Kafka retry/DLT (transport layer) + NotificationLog status (business layer)
- **Idempotency**: notification_id 기반 dedup — 워커가 PENDING INSERT 시 conflict 처리
- **Poison pill 방지** (사전학습 §Q4 — 회의에선 "리트라이 5번/3번 정도 후 DLT 이관" 수준으로 합의): 무한 in-place retry 금지 — max attempts 초과 시 DLT로 양보
- **Graceful degradation**: 한 채널(예: FCM) 장애 시 다른 채널은 정상 동작 (채널별 분리 덕)
- **서킷 브레이커 / bulkhead 구체 정책**: {- 본 회의에서 미정의 -}

### 10.4 Privacy

{- 본 회의에서 논의되지 않음. 알림 페이로드에 PII가 포함될 수 있으므로 향후 보강 필요 -}

---

## 11. Architecture Decisions (ADR)

### ADR-001: 채널별 Kafka 토픽 + 채널별 워커 분리

- **Status**: Accepted
- **Context**: 외부 3자 서비스(APNs/FCM/Twilio/SendGrid)는 우리가 통제 불가하고 rate limit이 채널마다 제각각이다. 단일 워커에 다 묶으면 한 채널 장애가 전체 알림 시스템으로 번질 수 있다. 회의에서 4명이 다른 입장으로 길게 논의함:

  | 발언자 | 입장 |
  |---|---|
  | 종민 | "단일 알림 제공자 → SPOF. 다수로 분리해야." |
  | 작성자 | "FCM은 FCM, APNs는 APNs로 컴포넌트 자체를 채널별 분리하자." |
  | 보성 | "스케일 아웃만 해도 SPOF 막힐 텐데, 굳이 컴포넌트 분리할 필요가?" |
  | 윤기 | "프로바이더별 분리만으로 SPOF 막기엔 부족. 채널 안에서도 scale-out 필요. + 3자 서비스마다 rate limit이 제각각이라 채널별 요청 로직 다르게 가야." |

- **Decision**: 채널별로 Kafka 토픽과 워커를 **모두** 분리한다 (push-ios / push-android / sms / email).
- **Consequences**:
  - Positive: 한 채널 장애가 다른 채널로 전파 X (디커플링 1목표), 추적성 개선 (어느 채널 문제인지 즉시 식별, 2목표), 채널별 독립 scale, 3자 서비스별 rate limit 로직 분리 가능
  - Negative: 인프라 복잡도 증가 (토픽 4종 + 워커 4종 운영), 토픽 추가 시 운영 비용
  - Neutral: 회의 결론 — "디커플링이 1목표, 추적성은 2목표"

### ADR-002: Kafka retry topic + DLT + NotificationLog 하이브리드 재시도

- **Status**: Accepted
- **Context**: 3자 서비스 호출 실패 시 재시도가 필수. DB 폴링 단독은 10M push/day 스케일에서 DB가 병목이 되고, Kafka retry 단독은 비즈니스 상태 조회/감사가 어렵다 (사전 학습 Q5 결론).
- **Decision**: 재시도 인프라는 Kafka retry topic + DLT, 상태/감사/idempotency는 NotificationLog DB로 분리.
- **Consequences**:
  - Positive: Kafka의 throughput으로 재시도 처리, DB는 상태 update만 부담, 운영자 SQL 조회 가능
  - Negative: 두 시스템 동기화 필요, 운영 복잡도 증가
  - Neutral: 사전 학습 §3-3 결론 그대로 적용

### ADR-003: AP 시스템 선택 (CAP)

- **Status**: Accepted
- **Context**: 알림은 soft real-time 시스템. retry로 인한 순서 깨짐 / 일시 손실 / 중복은 본질적으로 발생 가능. 회의에서 작성자가 "재시도시 순서 보장이 깨진다는 거잖아요"라고 자문한 뒤 본인이 CAP을 명시적으로 매핑 (trans. line 299-309).
- **Decision**: 가용성/확장성 우선 — strict ordering, exactly-once, sub-100ms SLA 모두 포기. 대신 at-least-once + idempotency + timestamp self-contained payload로 만회.
- **Consequences**:
  - Positive: scale-out 자유로움, 채널별 분리 + Kafka retry 사용 가능
  - Negative: strict ordering 필요한 도메인(금융 거래 등)은 별도 인프라(FIX gateway) 책임이라는 명시적 push-back 필요
  - Neutral: 책의 알림 시스템 본질과 일치

### ADR-004: Rate Limit을 알림 서버 단에 배치 (컨슈머 단 X)

- **Status**: Accepted
- **Context**: 버스트 트래픽 대응을 위해 rate limit이 필요. 어디에 거느냐가 논점.
- **Decision**: 알림 서버 입구에 토큰 버킷 기반 rate limit. 컨슈머 단에는 배치하지 않음.
- **Consequences**:
  - Positive: Kafka 큐가 이미 컨슈머 보호 (버퍼 역할) → 입구에서 막는 게 자원 효율적. 사용자 단/IP/컨테이너별 정밀 제어 가능. 미스터비스트 시나리오 (한 채널 폭주가 전체 막음) 방지.
  - Negative: 알림 서버가 무거워짐 (lookup + throttling)
  - Neutral: 회의 합의 (작성자 주도, 보성 의견 반영)

### ADR-005: 알림 서버 DB와 알림 로그 DB 분리 (DB-per-service)

- **Status**: Accepted
- **Context**: 두 DB의 access pattern이 완전히 다름 (read-heavy vs write-heavy/append, 영구 vs TTL).
- **Decision**: 알림 서버 DB는 User/Device (RDB OLTP), 알림 로그 DB는 NotificationLog (NoSQL 가능 — Cassandra 등은 사전학습 §Q3-2 예시. 14일 TTL).
- **Consequences**:
  - Positive: 책임 분리, 독립 scale, 워커는 큐 메시지에 토큰이 박혀 들어와서 알림 서버 DB 조회 불요
  - Negative: 운영 대상 DB가 2종으로 늘어남
  - Neutral: DB-per-service 원칙과 일치

### ADR-006: "알림 제공자(Notification Provider)" 용어 정정

- **Status**: Accepted (회의 중 명료화)
- **Context**: 회의 초반(trans. line 137-169), 작성자가 "알림 제공자(Notification Provider)" 박스를 책 그림 기준으로 그렸을 때 참석자들 사이에 의미 혼동이 발생. 일부는 이를 워커 서버(우리 시스템 내부의 3자 서비스 호출 컴포넌트)로 이해, 일부는 책 표현 그대로 알림 요청을 만드는 클라이언트(외부 마이크로서비스)로 이해.
- **Decision**: 작성자가 화면 공유로 책 그림을 다시 확인하며 정정 (민규 동조) — **"알림 제공자 = 알림 요청을 만들어 보내는 외부/내부 클라이언트 서비스 (결제/배송/쇼핑 등). 워커가 아님."** 본 SDD에서 다이어그램과 본문은 이 정의를 따른다. "워커"는 별도 컴포넌트로 명명.
- **Consequences**:
  - Positive: 책 용어와 일치, 외부 시스템과 내부 시스템 경계 명확
  - Negative: 회의 도중 그려둔 다이어그램 일부 수정 필요했음
  - Neutral: SDD §17 Glossary에 두 용어 별도 정의

### ADR-007: Opt-out 두 레벨 배치 (User 글로벌 + Device/Channel 단)

- **Status**: Accepted
- **Context**: opt-out 설정을 어디에 둘지 세 선택지 비교 (trans. line 313-323):
  - User 테이블에 글로벌 opt-out — 간단하나 정밀 제어 불가
  - Device 테이블에 device 단 opt-out — 디바이스별 제어 가능
  - 채널 필드 추가 — 채널별(push/sms/email) 정밀 제어
- **Decision**: **두 레벨 다 두기** — User에 글로벌 + Device에 채널별 (`opt_in_per_channel`).
- **Consequences**:
  - Positive: 사용자가 "전부 끄기"부터 "iOS 푸시만 끄기"까지 정밀 제어
  - Negative: 알림 서버 로직에서 두 레벨 모두 체크 필요
  - Neutral: 회의 합의

---

## 12. Alternatives Considered

**회의에서 검토한 대안**:

| Alternative | Pros | Cons | Why Rejected |
|---|---|---|---|
| 단일 워커 + 채널 구분 안 함 | 인프라 단순 | 한 채널 장애가 전체 마비, 추적 어려움, 3자 서비스별 rate limit 처리 불가 | 디커플링 불가, 회의 결론 (ADR-001) |
| 알림 제공자 = 단일 서버 | 단순 | SPOF | 회의 결론 — scale-out으로 다수 서버 (trans. line 99-105) |
| Rate Limit을 컨슈머 단에 | 명시적 backpressure | Kafka 큐가 이미 버퍼인데 중복 | ADR-004 (회의 합의) |
| 인메모리 큐 (Kafka 대체) | 가볍고 빠름 | 워커 다운 시 메시지 증발 | 사전학습 §Q6 — "데이터 손실 방지"와 정면 충돌. 회의에선 직접 비교 안 됐으나 Kafka 사용을 전제로 흐름 합의 |

**사전 학습에서만 검토 (회의 결론 외)**:

| Alternative | Pros | Cons | Why Rejected |
|---|---|---|---|
| DB 폴링 단독 재시도 | 단순, SQL 조회 쉬움 | DB 병목 (10M/day scale에서) | 사전 학습 §Q5 — 100M/day 가면 병목 명확 |
| Kafka retry topic 단독 (NotificationLog 없이) | 인프라 1종 | 비즈니스 상태 조회 / dedup / 감사 어려움 | 사전 학습 §Q3-3 결론 |
| Outbox pattern (notification log 대체) | 트랜잭션 원자성 | 외부 provider 호출 retry는 못 풂 | 책임 레이어가 다름 (사전 학습 §Q1) |
| Strict ordering 보장 (per-user serial queue 등) | 순서 보장 | throughput 급감, head-of-line blocking, 알림 시스템 본질과 불일치 | 사전 학습 §Q7 — 알림 도메인 책임 아님 |

---

## 13. Risk Register (선택)

> _Note:_ 회의에서 식별된 risk만 나열. Likelihood/Impact 등급 부여와 Owner 할당은 회의에서 진행되지 않아 미평가로 둠.

| ID | Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| R-1 | 3자 서비스 외부 장애 (FCM/APNs 다운) | {- 미평가 -} | {- 미평가 -} | 채널별 분리로 격리, retry topic + DLT, NotificationLog 상태 추적 | {- 미지정 -} |
| R-2 | DLT 적체 (자동 처리 한계 초과) | {- 미평가 -} | {- 미평가 -} | 큐 모니터링 (회의 합의), 운영자 수동 재처리는 회의에서 일반 언급 | {- 미지정 -} |
| R-3 | 네트워크 단절로 인한 중복 발송 | {- 미평가 -} | {- 미평가 -} | notification_id 기반 dedup, at-least-once + idempotency 합의 | {- 미지정 -} |
| R-4 | 버스트 트래픽 (미스터비스트 시나리오) | {- 미평가 -} | {- 미평가 -} | 알림 서버 단 토큰 버킷 rate limit, Kafka 버퍼링 | {- 미지정 -} |

---

## 14. Requirements Traceability (선택)

| Req ID | Requirement | Design Section | Test Case |
|---|---|---|---|
| FR-1 | 클라이언트가 알림 발송 | §5.2, §7.1, §8.2 | {- 미정 -} |
| FR-2 | 3채널 (Push/SMS/Email) 전송 | §5.2, §7.2 | {- 미정 -} |
| FR-3 | 채널별 opt-out | §6.2 (Device.opt_in_per_channel), §7.1, ADR-007 | {- 미정 -} |
| FR-4 | 멀티 디바이스 | §6.1 (User:Device = 1:N), §7.1 | {- 미정 -} |
| NFR-1 | Soft real-time | ADR-003 | {- 미정 -} |
| NFR-2 | 185 req/sec | §5, §7.1 (scale-out), §10.2 | {- 미정 -} |
| NFR-3 | SPOF 없음 | §5.2 (채널별 분리, scale-out), ADR-001 | {- 미정 -} |
| NFR-4 | 채널 확장성 | ADR-001 | {- 미정 -} |
| NFR-5 | 데이터 유실 최소화 | §7.2, §10.3, ADR-002 | {- 미정 -} |

---

## 15. Testing Strategy

{- 본 회의에서 구체 테스트 전략은 논의되지 않음. 향후 보강 영역:
  - Unit / Integration / E2E 정책
  - Load test 시나리오 (185 QPS sustained, 버스트)
  - Chaos test (FCM 다운, Kafka broker fail, 워커 kill)
  - Idempotency test (같은 notification_id 두 번 처리 시 SENT 한 번만)
  - Retry / DLT 라우팅 검증 -}

---

## 16. Rollout / Deployment Plan

{- 본 회의에서 배포 전략, 마이그레이션, 롤백 절차는 논의되지 않음. 향후 보강 영역:
  - Phased rollout (dev → canary → staged)
  - Feature flag 전략
  - 기존 알림 시스템 마이그레이션 (dual-write → backfill → cutover)
  - Rollback trigger (error rate, p99, DLT lag) -}

---

## 17. Glossary

| Term | Definition |
|---|---|
| QPS | Queries per second |
| 알림 제공자 (Notification Provider) | 책 용어. 알림 요청을 만들어 알림 시스템에 보내는 내부/외부 클라이언트 서비스 (결제/배송/쇼핑 등). **워커가 아님** (회의 중 혼동 정리됨) |
| Worker | 책의 "알림 제공자"와 헷갈리지 말 것. 본 시스템 내부의 컴포넌트로, Kafka 토픽을 consume 해서 3자 서비스를 호출하는 책임 |
| 3자 서비스 (3rd-party Service) | APNs (Apple Push Notification service), FCM (Firebase Cloud Messaging), Twilio (SMS), SendGrid (Email) 등 외부 알림 전달 서비스 |
| DLT | Dead Letter Topic. 자동 재시도 한계를 초과한 메시지의 최종 보관소. 사람(운영자)이 수동 처리 |
| Idempotency Key | 같은 작업을 여러 번 실행해도 한 번 실행한 것과 동일한 결과를 보장하기 위한 식별자. 본 시스템에서는 `notification_id` |
| At-least-once | 메시지가 최소 한 번은 전달됨을 보장하나, 중복 가능성은 있음. Kafka 기본 의미론 |
| AP (CAP) | Availability + Partition tolerance. Consistency(강한 일관성)는 양보 |
| Soft real-time | 엄격한 latency SLA 없이 "최선 노력" 수준의 실시간성. 부하 시 약간의 지연 허용 |
| Opt-out | 사용자가 알림 수신을 거부하는 설정. 본 시스템에서는 user 단 + device/channel 단 두 레벨 |
| Burst | 짧은 시간 폭발적으로 몰리는 요청 |

---

## 18. Appendix (선택)

### A. 다이어그램 원본
{- 별도 PlantUML / Excalidraw 소스 없음. 본 문서 내 ASCII 사용 -}

### B. 외부 참조 자료 / 벤치마크
- Alex Xu Vol.1 Ch.10 (pp.165–184)
- 사전 학습 11개 Q&A: `../conversation-log/2026-05-12.log`
- 회의록: `../study-notes/meeting-{transcript,summary}.md`

### C. Capacity 추정 상세 계산
- 일일 총량: 10M (Push) + 1M (SMS) + 5M (Email) = **16M / day**
- QPS (avg): 16,000,000 ÷ 86,400 ≈ **185 req/sec**
- QPS (peak): avg × 2 ≈ **370 req/sec**
- 메시지 크기 가정: 500 KB / 건 (이메일 HTML 템플릿 고려, 이미지 제외)
- 14일 저장량: 16M × 500KB × 14 ≈ **100~112 TB**
- Bandwidth / Device 테이블 row 수: {- 본 회의에서 정량적으로 다루지 않음 -}

### D. Open Questions (미해결)
- API contract 상세 (request/response schema, 에러 코드, 인증/인가)
- 캐시 전략 구체 (cache-aside / TTL / invalidation)
- LB 계층 / 알고리즘 / health check
- 모니터링 metrics 임계값, alert 정책
- 알림 템플릿 시스템 분리 방식
- 이벤트 추적 → 분석 파이프라인 도구 선택
- 보안 layer 전반 (인증/인가, TLS, PII 암호화)
- 글로벌 / 멀티 리전, 타임존 처리
- 배포 / 롤백 / 마이그레이션 전략
- 테스트 전략 (load / chaos / idempotency)
