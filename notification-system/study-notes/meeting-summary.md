# 알림 시스템 설계 — 스터디 회의 정리

**진행:** 작성자
**참석:** 종민, 보성, 윤기, 민규 외
**다룬 챕터:** Alex Xu Vol.1 Ch.10 — 알림 시스템 설계
**원본 트랜스크립트:** `meeting-transcript.md`
**사전 학습 로그:** `../conversation-log/2026-05-12.log` (회의 전 retry/DLT/ordering 사전 정리)
**이 문서:** AI 정리·요약본 (구조 + 핵심 결론 위주, 발화 흐름은 transcript 참조)

---

## 0. 회의 진행 방식

작성자가 진행. 책 기준 요구사항 정리 → capacity 추정 → 컴포넌트 식별 → 안정성/Drill Down 순서. 회의 전 사전 학습 (notification log + retry, Kafka commit, DLT, 순서 보장, CAP)을 conversation-log에 정리해뒀고, 회의에서 그 결론을 컴포넌트 설계에 적용.

---

## 1. 문제 정의 (Clarifying)

### 1.1 책에 나온 요구사항 (정성적)

| 항목 | 내용 |
| --- | --- |
| 실시간성 | 연성 실시간(soft real-time) — 부하 시 약간의 지연 허용 |
| 알림 종류 | 푸시(Push) / SMS / Email |
| 지원 기기 | iOS, Android, 랩탑, 데스크탑 |
| 발송 주체 | 클라이언트 애플리케이션 + 서버측 스케줄링 |
| Opt-out | 사용자가 알림 수신 거부 설정 가능 |
| 안정성 | SPOF 없을 것, 데이터 유실 최소화 |
| 확장성 | 새 채널(3자 서비스) 추가 시 시스템 재설계 X, 기존 서비스에 연동 용이 |
| 성능 병목 | 네트워크 / 워커 과부하 / 버스트 요청 대응 |

### 1.2 추정 정보 (Capacity Estimation, 정량적)

| Metric | 계산 | 값 |
| --- | --- | --- |
| 일일 알림 총량 | 10M (Push) + 1M (SMS) + 5M (Email) | **16M / day** |
| QPS (avg) | 16,000,000 / 86,400 (= 60×60×24) | **≈ 185 / sec** |
| 평균 메시지 크기 | 이메일 HTML 템플릿 고려, 이미지는 제외 | **500 KB / 건** |
| 보관 기간 | 1년은 과함, 1주~1달 사이 → **2주** 결정 (raw 알림 로그) | 14 days |
| 저장량 | 16M × 500KB × 14days ≈ | **≈ 100~112 TB** |

> 보관 기간 논의 (작성자): "비즈니스가 데이터 가공해서 재활용한다면 1년, 단순 raw 로그면 1주~1달 정도. 카카오뱅크도 일정 기간만 남긴다."
> 저장량은 마이크로서비스로 DB 양이 늘면 되니까 크게 부담 X.

### 1.3 책에 없는데 직접 추가한 NFR

- **버스트 요청 대응** (Burst): 짧은 시간 폭발적 트래픽 흡수 가능해야 함 — 후술하는 Kafka 큐가 버퍼 역할.
- **새 컨퍼런스(3자 서비스) 일축 가능** — 확장성의 구체 표현.

---

## 2. 컴포넌트 식별 (개략적 설계)

### 2.1 책 용어 혼동 정리 — "알림 제공자(Notification Provider)"가 뭔가?

회의 초반 핵심 혼동이 있어 정리.

| 잘못된 이해 (회의 중간) | 책의 진짜 의미 |
| --- | --- |
| "알림 제공자 = 워커 서버" (3자 서비스에 직접 호출하는 우리 측 컴포넌트) | "알림 제공자 = 알림 요청을 만들어 보내는 외부/내부 서비스" (= 클라이언트 마이크로서비스, 결제/배송/쇼핑 등) |

민규가 책 그림 다시 확인 후 정정 — **알림 제공자는 알림 시스템 외부의 요청 주체**고, "알림 제공자가 페이로드 만들어서 알림 시스템에 보내면, 알림 시스템이 3자 서비스(FCM/APNs/Twilio/SendGrid)로 라우팅"하는 구조.

작성자 결론: "여기서 워커 서버는 책의 '알림 제공자'가 아니라 별도 컴포넌트. 알림 서버가 앞단에 추가로 들어가야 함."

### 2.2 최종 컴포넌트 구조

```
[클라이언트 마이크로서비스]   ← 책의 "알림 제공자"
   (결제, 배송, 쇼핑 등)
        │ 알림 요청 (페이로드)
        ▼
[알림 서버]                    ← 분리 + scale-out (SPOF 방지)
   │   ├─ User DB / Device DB / 토큰 정보
   │   └─ (캐시: Redis — read-heavy 시 도입)
        │
        ▼
[Kafka 큐 — 채널별 분리]
   ├─ push-ios
   ├─ push-android
   ├─ sms
   └─ email
        │
        ▼
[워커 서버 — 채널별 분리]
   ├─ APNs Worker
   ├─ FCM Worker
   ├─ Twilio Worker
   └─ SendGrid Worker
   │   └─ 알림 로그 DB (전송 이력)
        │
        ▼
[제3자 서비스 (3rd party)]
   ├─ APNs (iOS)
   ├─ FCM (Android)
   ├─ Twilio (SMS)
   └─ SendGrid (Email)
        │
        ▼
[클라이언트 기기]
```

### 2.3 채널별 분리 — 왜?

회의에서 가장 길게 논의된 부분. 세 가지 입장이 나옴.

| 발언자 | 입장 |
| --- | --- |
| 종민 | "단일 알림 제공자 → SPOF. 다수로 분리해야." |
| 작성자 | "FCM은 FCM, APNs는 APNs로 컴포넌트 자체를 채널별 분리하자." |
| 보성 | "스케일 아웃만 해도 SPOF 막힐 텐데, 굳이 컴포넌트 분리할 필요가?" |
| 윤기 | "프로바이더별 분리만으로 SPOF 막기엔 부족. 채널 안에서도 scale-out 필요. + 3자 서비스마다 rate limit이 제각각이라 채널별 요청 로직 다르게 가야." |

작성자 결론 (분리 찬성):
1. **1목표는 디커플링** — FCM이 죽으면 FCM 워커만 죽지 다른 채널은 멀쩡. 같은 워커에 다 묶이면 한 채널 장애가 전체 알림 시스템으로 번짐.
2. **2목표는 추적성** — 채널별 분리되어 있으면 "어느 컴포넌트 문제인가" 빨리 식별 가능. 모니터링/로그 분리.
3. **rate limit 차이** (윤기 보강) — APNs / FCM / Twilio / SendGrid 가 각자 다른 rate limit을 걸어두기 때문에 채널마다 요청 throttling 로직이 달라야 함.

→ **채널별 큐 + 채널별 워커**로 결정.

### 2.4 Kafka 큐를 왜 끼우나

워커 서버를 직접 호출하지 않고 중간에 Kafka를 둠. 이유:

- **디커플링** — 알림 서버와 워커 서버 독립적으로 scale
- **재시도(retry)** — 워커가 실패하면 메시지 재처리 가능
- **버스트 흡수** — 큐가 버퍼 역할, 짧은 폭발 트래픽 흘려보냄
- **데이터 손실 방지** — 인메모리 큐 아니라 영속화된 Kafka 토픽 (사전 학습 §6 결론과 일치)

### 2.5 데이터베이스 두 종류

| DB | 책임 | 비고 |
| --- | --- | --- |
| **알림 서버 DB** | User 정보, Device 토큰, opt-out 설정 | OLTP, 정규화 관계형. 캐시 도입 가능 |
| **알림 로그 DB** | 전송 이력 (notification_id, payload, status...) | NoSQL 가능 (Cassandra 등). 2주 TTL |

→ 사전 학습 §3-1 결론과 일치: **DB-per-service 원칙**으로 분리. 워커는 큐 메시지에 토큰이 박혀서 들어오니까 알림 서버 DB 조회 불필요.

---

## 3. 상세 설계 (Drill Down)

### 3.1 안정성 — 알림 유실 / 중복 방지

#### 책 인용
> "알림이 중복으로 발송될 가능성이 있고, 이를 100% 방지하는 것은 불가능하다."

#### 회의에서 도달한 이유 (작성자 + 종민 + 보성 합의)

네트워크 단절로 ack 누락 → 컨슈머는 메시지를 정상 처리했지만 producer 측은 못 받음 → 타임아웃 후 재전송 → 중복 발생. 이 시나리오는 통제 불가.

> "컨슈머가 정상 받았는데 ack가 네트워크 끊겨서 못 돌아가면, 프로바이더는 타임아웃 후 재전송. 그래서 중복은 100% 못 막음." — 작성자 정리

#### 최선의 보장 — 이중 처리

종민 제안 + 작성자 정리한 워커 흐름:

```
[Worker]
  ├─ 메시지 수신 → notification_log INSERT(PENDING)  ← idempotency 체크 겸
  ├─ 3자 서비스 호출
  │     ├─ 성공 → notification_log UPDATE(SENT) → Kafka ack
  │     └─ 실패 → notification_log UPDATE(FAILED, retry_count++)
  │              → Kafka retry topic 으로 produce + 원본 ack
  └─ retry_count > 임계치 → notification_log UPDATE(DEAD) → DLT 로 produce
                                                              ↑
                                                    개발자 수동 처리 / 스케줄러 재시도
```

핵심 포인트 (작성자 정리):
- **Kafka ack = "비즈니스 로직 처리 완료" 의미**. 단순 수신 여부가 아니라 워커가 3자 서비스 호출 성공/실패 여부로 ack 결정. (사전 학습 §4와 일치)
- **알림 로그 DB에 상태(PENDING/SENT/FAILED/DEAD) 기록** → idempotency + 감사
- **페이로드에 notification_id 박아서 dedup 키로 사용** (보성 보강)

#### 결론

> "exactly-once 보장은 거의 불가능하지만, **이중 보장(Kafka retry + 알림 로그 status)** 으로 at-least-once + idempotency 처리해 사실상 once에 근접." — 작성자

### 3.2 순서 보장 문제

#### 의문 (작성자 본인)
> "retry로 재시도하면 메시지 순서가 깨지는데, 이래도 되나?"

#### 회의 결론

**연성 실시간 시스템 + AP 시스템** 이라 허용.

| 시스템 성격 | 결과 |
| --- | --- |
| Soft real-time | 부하 시 약간의 지연/순서 흐트러짐 허용 |
| CAP 중 AP | Consistency(강한 일관성)는 일부 깨도 됨. Availability + Partition tolerance 우선 |

> "CAP 중 AP. 강한 일관성을 조금 깨더라도 확장성/가용성을 더 보장." — 작성자

→ 사전 학습 §11 (CAP 매핑)에서 도출된 결론과 정확히 일치. strict ordering 필요한 도메인 (금융 거래 알림 등)은 **알림 시스템 책임이 아니라 별도 trading infra (FIX gateway 등) 책임** — 회의에선 깊이 안 들어가고 "soft real-time이라 허용"으로 정리.

### 3.3 데이터베이스 설계 상세

#### 알림 서버 DB

**User 테이블**
| 필드 | 타입 | 비고 |
| --- | --- | --- |
| id | PK | 유저 식별자 |
| 최소 정보 (이름 등) | - | 시스템 설계라 깊이 X |
| opt_in_global | BOOLEAN | 알림 전체 수신 여부 (유저 단) |

**Device 테이블**
| 필드 | 타입 | 비고 |
| --- | --- | --- |
| id | PK | 디바이스 식별자 |
| user_id | FK → User.id | |
| device_token | VARCHAR | 3자 서비스 호출용 토큰 |
| opt_in_per_channel | BOOLEAN/JSON | 채널별 opt-out (push/sms/email별 정밀 제어) |

**관계**: User : Device = **1 : N**
> "인스타그램처럼 한 유저가 여러 기기에 로그인하고 알림은 모두 받아야 하니까 1:N." — 종민

#### Opt-out 위치 논의

| 위치 | 의견 |
| --- | --- |
| User 테이블에 글로벌 opt-out | 간단 |
| Device 테이블에 device 단 opt-out | 정밀 제어 |
| **채널 필드 추가** | 채널별 (push만 끄기, email만 끄기 등) 더 정밀 |

→ 결론: **두 레벨 다 두기** (유저 글로벌 + 디바이스/채널 단).

#### 알림 로그 DB

```
notification_log
─────────────────────────────────────
id              UUID PK    ← idempotency key 겸용
payload         JSON       ← 메시지 본문 + 부가정보 (device_token 포함)
status          ENUM       ← PENDING / SENT / FAILED / DEAD
부가정보(device, channel 등은 payload 안에 포함)
```

작성자 정리:
> "부가 정보에 device_token 포함됨. 3자 서비스 호출에 그게 ID로 쓰이니까."

스토리지 선택지 (사전 학습 §3-2 결론 적용):
- 소규모면 RDB
- 대규모면 Cassandra (partition key=user_id, clustering key=created_at desc, TTL native)

### 3.4 Rate Limit — 어디에 거는가?

회의에서 길게 논의. 결론적으로 **알림 서버 단에 건다**.

#### 논의 과정

작성자 의문:
> "Rate limit이 책 그림에 어디 걸려있다는데, 알림 제공자(클라이언트)단에 거는 게 와닿지 않는다. 어떤 사례 때문에 거는가?"

보성 답:
> "rate limit은 알림 서버 내부 로직으로 보는 게 맞을 듯."

작성자 결론 (반박 + 정리):
1. **컨슈머까지 갈 필요 X** — Kafka 큐가 이미 버퍼 역할이라 컨슈머 보호는 큐가 함.
2. **버스트로 서비스 터지려면 Kafka에 메시지가 엄청 쌓여야 함** — 즉 소비 속도 < 생산 속도일 때만 문제.
3. → **알림 서버 단에서 큐 모니터 + rate limit 걸기**가 정답.
4. Rate limit 거는 방식:
   - API별 (특정 클라이언트 서비스에 대한 제한)
   - IP별 (호출 IP 기준 throttling)
   - Container별 (Kubernetes 환경에서)
   - 토큰 버킷 알고리즘 + 유저별 처리량 제한

#### 미스터비스트 예시 (참석자 상상)

> "유튜브 미스터비스트 구독자 몇억 명. 동시에 전 세계 구독자에게 알림 뿌리면, rate limit 안 걸면 그 한 채널의 알림 보내려고 다른 알림이 다 안 갈 수도 있다. → rate limit 필수."

→ 사전 학습에서 "FCM/APNs는 외부 통제 불가, 채널마다 rate limit 다름"이라 정리한 부분과 매끄럽게 연결됨. **외부 3자 서비스의 rate limit을 우리 알림 서버가 미리 throttling**하는 게 핵심.

### 3.5 큐 모니터링

- 버스트 시 순간 적체는 OK (큐가 버퍼)
- **지속적으로 컨슘 속도 < 적체 속도면 위험**
- 대응:
  - 컨슈머 증설 (워커 scale-out)
  - 알림 서버단에서 rate limit 강화 (입구 차단)

### 3.6 알림 템플릿 (간단 언급)

- 책에 있긴 한데 **시스템 설계 깊이로는 우선순위 낮음**.
- 이메일 HTML 템플릿, 푸시 메시지 정형 포맷 등 — 별도 템플릿 서비스로 빼는 정도.
- "있으면 추가, 깊이 안 다룸"으로 정리.

### 3.7 이벤트 추적 / 분석 (간단 언급)

책의 후반에 언급되는 항목:
- 알림 전송 이벤트 (sent / delivered / opened / clicked) 추적
- 분석 파이프라인 (Kafka → Analytics service)
- **보안은 우선순위 낮음** (작성자 입장)

---

## 4. 사전 학습과의 매핑

회의 전 conversation-log에 정리한 11개 Q&A가 회의 중 어디에 활용됐는지:

| 사전 학습 Q | 회의 적용 위치 |
| --- | --- |
| Q1. notification log vs outbox | 명시적 언급 없으나, 알림 로그 DB 역할 정의에 깔림 |
| Q2. retry 두 축 (A/B/C 조합) | §3.1 안정성 — "C 하이브리드 (Kafka retry topic + DLT + notification_log)" 채택 |
| Q3. DB 분리 / 알림 로그 스키마 / DLT 보완 | §2.5 + §3.3 데이터베이스 설계에 그대로 적용 |
| Q4. Kafka ack/nack = 비즈니스 결과 | §3.1 워커 흐름에서 ack 정의로 직접 사용 |
| Q5. DB 폴링 vs Kafka retry topic | 회의에선 Kafka retry topic으로 결정 (book 시나리오 10M push/day) |
| Q6. 책의 "재시도 전용 큐" = retry topic | §3.1에서 retry topic + DLT 흐름으로 적용 |
| Q7. retry로 인한 순서 깨짐 | §3.2 순서 보장 문제로 직결 |
| Q8. self-contained + timestamp | 회의에선 깊이 X (out-of-scope 처리) |
| Q9. strict ordering 필요한 도메인 | §3.2에서 "soft real-time이라 X"로 처리 |
| Q10. 주식 매수/매도 dedicated infra | 회의 범위 외, 사전 학습으로만 정리 |
| Q11. CAP 중 AP | §3.2에서 직접 인용 — "CAP 중 AP" |

---

## 5. 결정·답변 한눈에 보기 (Action Items)

| 항목 | 결정 / 결론 |
| --- | --- |
| 시스템 성격 | Soft real-time + AP (CAP) |
| QPS | avg ≈ 185 / sec (16M/day) |
| 알림 로그 보관 | 2주 (≈ 100~112 TB) |
| 메시지 크기 가정 | 500 KB / 건 (이메일 HTML 템플릿 고려) |
| 채널 분리 | Kafka 토픽 + 워커 **둘 다 채널별 분리** (디커플링 1목표, 추적성 2목표) |
| 큐 인프라 | Kafka (in-memory 큐 아님 — 데이터 손실 방지) |
| Retry 전략 | Kafka retry topic + DLT + 알림 로그 status 이중 보장 |
| Ack 정의 | "비즈니스 로직 처리 완료" — 단순 수신 X |
| 중복 방지 | notification_id 페이로드에 박아서 dedup, 알림 로그 status로 idempotency |
| 순서 보장 | AP 시스템이라 허용 (soft real-time) |
| DB 분리 | 알림 서버 DB(User/Device) vs 알림 로그 DB(전송 이력) — DB-per-service |
| User:Device | 1:N |
| Opt-out | 유저 글로벌 + 디바이스/채널별 두 레벨 |
| Rate Limit 위치 | **알림 서버 단** (컨슈머단 X — Kafka가 버퍼 역할) |
| Rate Limit 방식 | API별 / IP별 / Container별 / 토큰 버킷 |
| 알림 템플릿 | 우선순위 낮음, 별도 서비스로 분리 가능 정도만 |
| 이벤트 추적 | 책 후반 항목, 분석 파이프라인 추가 검토 |
| 보안 | 회의에서 깊이 X |

---

## 6. 다루지 못한 / 추가 검토 항목

### 6.1 API 설계
회의에서 잠깐 언급되고 넘어감. "key가 많지 않을 것 같아서 생략."
→ SDD 작성 단계에서 보강 필요:
- `POST /notifications` (알림 발송)
- `PUT /users/{id}/opt-out` (수신 거부 설정)
- 인증/인가, request payload schema

### 6.2 알림 템플릿 시스템
"별도 템플릿 서비스로 뺄 수 있다" 정도만 정리. 실제 어떻게 분리할지는 미정.

### 6.3 이벤트 추적 파이프라인
sent / delivered / opened / clicked 이벤트를 어디에 어떻게 모을지 — Kafka analytics 토픽 + 별도 데이터 웨어하우스 정도로 추정만.

### 6.4 보안
- 알림 페이로드에 민감 정보 들어가면? (TLS, 토큰 암호화)
- 3자 서비스 인증 키 관리
- → 회의에서 "우선순위 낮음"으로 미룸.

### 6.5 글로벌 / 멀티 리전
- 시간대 처리 (사용자 타임존별 발송 시각)
- 리전별 3자 서비스 선택 (한국 사용자 → KakaoTalk Push? 미국 → APNs/FCM?)
- → 회의 범위 밖.

---

## 참고

- 발화 단위 흐름은 `meeting-transcript.md` 참조
- 회의 전 사전 학습 (retry, DLT, Kafka commit, ordering, CAP 매핑)은 `../conversation-log/2026-05-12.log` 참조
- 이 회의 결과를 바탕으로 다음 단계는 **mock-interview.md 작성 → sdd.md 작성** 순서
