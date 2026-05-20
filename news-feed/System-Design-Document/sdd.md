# News Feed System — Software Design Document

---

## 0. Document Metadata

> _Why_
> 변경 이력과 책임자를 명시해 문서를 "팀 자산"으로 만든다. 메타데이터가 없는 SDD는 시간이 지나면 신뢰도를 잃는다.

| 항목 | 값 |
|---|---|
| Document Title | News Feed System SDD |
| Version | 0.1 (Draft) |
| Status | Draft |
| Author(s) | {이름} |
| Reviewer(s) | {이름} |
| Last Updated | 2026-05-20 |
| Related Documents | `mock-interview.md`, `../study-notes/meeting-summary.md`, `../study-notes/meeting-transcript.md` |

### 0.1 Revision History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1 | 2026-05-20 | {이름} | Initial draft from study meeting notes |

---

## 1. Introduction

> _Why_
> 독자가 본문을 읽기 전에 "이 문서가 무엇이고 왜 존재하는지"를 30초 안에 파악하게 한다. PRD가 "무엇을 만들 것인가"라면 SDD는 "어떻게 만들 것인가"의 시작점.

### 1.1 Purpose

> _What goes here_
> 이 SDD가 다루는 시스템 / 모듈, 작성 목적, 대상 독자 (예: backend 팀, SRE, 보안 검토자).

이 문서는 10M DAU 규모의 뉴스 피드 시스템 설계를 기술한다. 대상 독자는 시스템 디자인 스터디 참여자와 backend/SRE 관점에서 피드 발행, fanout, 피드 조회, 캐시 구조를 검토할 사람이다.

### 1.2 Scope

> _What goes here_
> 문서가 커버하는 범위. "이 SDD는 X를 다루며, Y는 별도 문서에서 다룬다."

- **In scope**: 게시글 발행, 뉴스 피드 조회, fanout 전략, News Feed Cache 데이터 모델, Post/User hydration 흐름, 미디어 저장소/CDN 분리
- **Out of scope**: 랭킹/추천 ML, 광고, 관리자 도구, 콘텐츠 모더레이션, 인증/회원가입, 댓글/좋아요/공유 상세 기능

### 1.3 References

> _What goes here_
> PRD, ADR, 외부 표준, 관련 RFC.

- Alex Xu — System Design Interview Vol.1 Ch.11, News Feed System Design
- `../study-notes/meeting-summary.md`
- `../study-notes/meeting-transcript.md`
- `mock-interview.md`

---

## 2. System Overview

> _Why_
> 본격 설계 진입 전 "시스템이 무엇을 하고 어디에 위치하는지" 2–3문단으로 압축. 다이어그램 1개 권장 (context diagram).

본 시스템은 사용자가 작성한 게시글을 저장하고, follower/friend 관계를 바탕으로 각 사용자의 뉴스 피드를 구성한다. 기본 피드는 reverse chronological order를 따른다.

게시글 발행 경로는 Post Service, Message Queue, Fanout Worker, Graph DB/Follow Store, User Cache, News Feed Cache로 이어진다. 피드 조회 경로는 Feed Service가 News Feed Cache에서 `post_id` 목록을 읽고, Post Cache와 User Cache를 통해 feed item을 hydrate해 응답한다.

```
Mobile/Web -> LB -> Web/API Server
                    ├─ write: Post Service -> Post DB -> MQ -> Fanout Worker -> Feed Cache
                    └─ read:  Feed Service -> Feed Cache -> Post/User Cache -> Response

Media: Object Storage -> CDN
```

---

## 3. Goals and Non-Goals

> _Why_
> Atlassian / Notion / Google 디자인 독의 공통 항목. **무엇을 하지 않을지 명시하는 것이 무엇을 할지 명시하는 것만큼 중요하다.**

### 3.1 Goals

> _What goes here_
> 측정 가능한 목표. "빠르게" 같은 형용사 대신 "p99 < 100ms"처럼.

- G-1: 10M DAU와 사용자당 최대 5,000 friends/followees를 처리한다.
- G-2: 평균 write QPS 약 116/sec, peak write QPS 약 230/sec를 처리한다.
- G-3: 게시글 중 10%가 평균 1MB 미디어를 포함한다는 가정에서 약 2PB/5년 규모의 미디어 저장소를 설계한다.
- G-4: 일반 사용자는 fanout on write로 빠른 피드 조회를 제공하고, celebrity user는 fanout on read로 write amplification을 제한한다.
- G-5: News Feed Cache에는 전체 게시글 객체가 아니라 `reader_user_id -> post_id list`를 저장한다.

### 3.2 Non-Goals

> _What goes here_
> 명시적으로 다루지 않을 것. 스코프 크리프 방지.

- NG-1: 추천/랭킹 ML 피드 설계는 다루지 않는다. 기본은 reverse chronological order다.
- NG-2: 광고/수익화는 다루지 않는다.
- NG-3: 관리자 도구와 콘텐츠 모더레이션은 다루지 않는다.
- NG-4: 인증/회원가입은 기존 시스템이 제공한다고 가정한다.
- NG-5: 댓글/좋아요/공유 상세 설계는 cache namespace 예시 외에는 다루지 않는다.

---

## 4. Constraints

> _Why_
> 설계 자유도를 제한하는 외부 요인. Constraints는 "선택 사항이 아닌 것"이고 Non-Goals는 "선택했지만 안 하는 것" — 혼동 금지.

### 4.1 Technical Constraints

- DAU는 10M으로 가정한다.
- 사용자당 friends/followees 수는 최대 5,000명으로 가정한다.
- 피드는 reverse chronological order를 기본으로 한다.
- 미디어는 API 응답 본문에 직접 싣지 않고 object storage와 CDN을 통해 제공한다.
- News Feed Cache는 `reader_user_id` 기준으로 post ID 목록을 저장한다.
- Feed read는 Post Cache/User Cache hydration 단계를 포함한다.

### 4.2 Organizational / Business Constraints

- {- 회의 미논의 -}

### 4.3 Regulatory / Compliance Constraints

- {- 회의 미논의 -}

---

## 5. System Architecture

> _Why_
> 시스템의 뼈대. 컴포넌트 분해, 통신 방식, 배포 토폴로지를 한눈에 보이게 한다. 다이어그램 없이는 의미 없음.

### 5.1 Architectural Style

이 설계는 write path와 read path를 분리한 event-driven fanout 구조를 사용한다. 게시글 저장은 동기적으로 처리하고, follower feed cache 업데이트는 Message Queue와 Fanout Worker를 통해 비동기 처리한다.

선택 근거: 게시글 저장과 feed fanout은 부하 특성과 지연 허용 범위가 다르다. 일반 사용자의 read latency를 낮추기 위해 feed cache를 미리 구성하되, celebrity user의 과도한 write amplification은 fanout on read로 우회한다.

### 5.2 Component Diagram

<img width="500" height="600" alt="image" src="https://github.com/user-attachments/assets/67fb53f1-9218-4383-af90-cb5ff65a2c95" />
<img width="500" height="600" alt="image" src="https://github.com/user-attachments/assets/5f4c7b3f-d17e-46dd-b13c-8406d284fc0c" />

### 5.3 Deployment Topology

- **Runtime**: {- 회의 미논의 -}
- **Region**: {- 회의 미논의 -}
- **Network**: {- 회의 미논의 -}

---

## 6. Data Design

> _Why_
> 데이터 모델은 시스템의 척추. 스키마뿐 아니라 **데이터 라이프사이클**(생성 → 보관 → 삭제)까지 포함.

### 6.1 Data Model / ERD

```
User(id, profile, privacy_settings, created_at)
Post(id, author_id, text, media_refs, created_at)
FollowEdge(follower_id, followee_id, created_at)
FeedCacheEntry(reader_user_id, post_id, created_at)
MediaObject(id, storage_url, cdn_url, type, metadata)

User 1 ── N Post
User N ── N User via FollowEdge
User 1 ── N FeedCacheEntry
Post 1 ── N FeedCacheEntry
Post N ── N MediaObject
```

### 6.2 Data Dictionary

| Entity | Field | Type | Constraint | Description |
|---|---|---|---|---|
| User | id | {- 미정 -} | PK | 사용자 ID |
| User | profile | JSON / document | | 표시 이름, 프로필 이미지 등 |
| User | privacy_settings | JSON / document | | block, mute, close friends 등 visibility 판단용 설정 |
| Post | id | {- 미정 -} | PK | 게시글 ID |
| Post | author_id | {- 미정 -} | FK -> User.id | 게시글 작성자 |
| Post | text | TEXT | | 게시글 텍스트 |
| Post | media_refs | JSON / array | | Object Storage/CDN 참조 |
| Post | created_at | TIMESTAMP | indexed candidate | reverse chronological order 기준 |
| FollowEdge | follower_id | {- 미정 -} | | follower |
| FollowEdge | followee_id | {- 미정 -} | | followee |
| FeedCacheEntry | reader_user_id | {- 미정 -} | cache key | 피드를 읽을 사용자 ID |
| FeedCacheEntry | post_id | {- 미정 -} | | 사용자가 볼 게시글 ID |
| MediaObject | cdn_url | URL | | 클라이언트 미디어 로드 경로 |

### 6.3 Data Lifecycle

- **Creation**:
  - Post는 사용자가 `POST /v1/me/feed` 요청 시 생성된다.
  - FeedCacheEntry는 Fanout Worker가 follower 대상에게 `post_id`를 push할 때 생성된다.
  - MediaObject는 게시글의 이미지/비디오 업로드 시 생성된다.
- **Retention**:
  - 미디어는 회의 가정상 5년 보존 기준으로 약 2PB rough cut.
  - post/feed cache retention은 {- 회의 미논의 -}.
- **Archival**: {- 회의 미논의 -}
- **Deletion**: {- 회의 미논의 -}

### 6.4 Data Flow

#### 게시글 발행

```
Client -> Web/API -> Post Service -> Post DB
Post Service -> Message Queue(post-created)
Fanout Worker -> Graph DB(follower list)
Fanout Worker -> User Cache(visibility filter)
Fanout Worker -> News Feed Cache(feed:{reader_user_id} append post_id)
```

#### 뉴스 피드 조회

```
Client -> Web/API -> Feed Service
Feed Service -> News Feed Cache(feed:{reader_user_id}) -> [post_id...]
Feed Service -> Post Cache/Post DB -> post body, media_refs, author_id
Feed Service -> User Cache/User DB -> author profile
Feed Service -> Client(JSON feed)
Client -> CDN -> media
```

---

## 7. Component Design

> _Why_
> 각 모듈을 구현자가 그대로 코드로 옮길 수 있는 수준까지 명세. 추상적이면 가치 없음.

### 7.1 Component Post Service

- **Responsibility**: 사용자의 게시글을 저장하고 `post-created` 이벤트를 발행한다.
- **Inputs**: `POST /v1/me/feed` 요청
- **Outputs**: Post DB row/document, Message Queue event
- **Dependencies**: Post DB, Message Queue, Media/Object Storage 후보
- **Core Logic**:
  1. 요청 사용자와 게시글 본문/미디어 참조를 검증한다.
  2. Post DB에 `Post`를 저장한다.
  3. `post-created` event를 Message Queue에 publish한다.

### 7.2 Component Fanout Worker

- **Responsibility**: 새 게시글을 볼 수 있는 follower들의 News Feed Cache에 `post_id`를 반영한다.
- **Inputs**: `post-created` event
- **Outputs**: `feed:{reader_user_id}` cache update
- **Dependencies**: Graph DB/Follow Store, User Cache/User Service, News Feed Cache
- **Core Logic**:
  1. event에서 `post_id`, `author_id`를 읽는다.
  2. Graph DB/Follow Store에서 follower list를 조회한다.
  3. User Cache/User Service에서 block, mute, privacy setting, close friends 조건을 확인한다.
  4. 일반 사용자 follower의 `feed:{reader_user_id}`에 `post_id`를 push한다.
  5. celebrity user는 fanout on read 대상으로 처리한다.

### 7.3 Component Feed Service

- **Responsibility**: 사용자의 뉴스 피드를 조회하고 hydrate해 반환한다.
- **Inputs**: `GET /v1/me/feed?cursor=&limit=`
- **Outputs**: JSON feed response
- **Dependencies**: News Feed Cache, Post Cache/Post DB, User Cache/User DB, CDN URL
- **Core Logic**:
  1. `reader_user_id`로 News Feed Cache에서 `post_id` list를 조회한다.
  2. 각 `post_id`에 대해 Post Cache/Post DB에서 post body, media refs, author_id를 조회한다.
  3. `author_id`로 User Cache/User DB에서 작성자 표시 정보를 조회한다.
  4. hydrated feed item을 구성해 cursor 기반으로 응답한다.

### 7.4 Component News Feed Cache

- **Responsibility**: 사용자별로 볼 게시글 ID 목록을 빠르게 제공한다.
- **Inputs**: Fanout Worker의 append/update, Feed Service의 read
- **Outputs**: ordered list of `post_id`
- **Dependencies**: {- 구현체 미정 -}
- **Core Logic**:
  - key: `feed:{reader_user_id}`
  - value: ordered list of `post_id`
  - 저장 구조 후보: Redis List/ZSet 등 {- 회의 미논의, follow-up -}

### 7.5 Component Graph DB / Follow Store

- **Responsibility**: 작성자의 follower/friend list를 fanout 시점에 제공한다.
- **Inputs**: `author_id`
- **Outputs**: follower/friend ID list
- **Dependencies**: {- 회의 미논의 -}
- **Core Logic**: {- 회의 미논의 -}

---

## 8. Interface Design

> _Why_
> 컴포넌트의 외부 노출면. 한 번 공개되면 변경 비용이 큼 — SDD 단계에서 신중히.

### 8.1 External APIs

| Method | Path | Request | Response | Errors |
|---|---|---|---|---|
| `POST` | `/v1/me/feed` | `{text, media_refs}` | `{post_id, created_at}` | {- 회의 미논의 -} |
| `GET` | `/v1/me/feed?cursor=&limit=` | query params | `{items, next_cursor}` | {- 회의 미논의 -} |
| `GET` | `/v1/posts/{postId}` | path param | `{post}` | {- 회의 미논의 -} |

### 8.2 Internal Service Interfaces

- `post-created` event:

```json
{
  "post_id": "{post_id}",
  "author_id": "{author_id}",
  "created_at": "{timestamp}"
}
```

> _Note:_ 정확한 message schema, topic name, retry policy는 회의 미논의.

### 8.3 UI Flow (해당 시)

{- 회의 미논의 -}

---

## 9. Non-Functional Requirements

> _Why_
> ISO 25010 품질 모델 기반 분류. 인터뷰 스타일과 달리 **각 항목이 검증 가능한 acceptance criteria**를 가져야 한다.

| Category | Requirement | Acceptance Criteria |
|---|---|---|
| Scalability | 10M DAU, 최대 5,000 friends/followees 지원 | capacity model에 DAU/follower 상한 반영 |
| Performance | 평균 write QPS 116/sec, peak 230/sec 처리 | load test에서 write path가 해당 처리량을 sustained 처리 |
| Performance | feed read latency 낮게 유지 | 구체 p99 목표는 {- 회의 미논의 -} |
| Storage | 미디어 5년 약 2PB rough cut 지원 | object storage capacity plan에 반영 |
| Availability | read path는 약간 stale한 데이터를 허용 | 정확한 SLA는 {- 회의 미논의 -} |
| Maintainability | cache namespace를 데이터 특성별로 분리 | News Feed/Post/User/Social Graph/Action/Counter cache 영역 구분 |

---

## 10. Cross-Cutting Concerns

> _Why_
> 어느 한 컴포넌트의 책임이 아니라 시스템 전체에 걸쳐 있는 관심사. 한 곳에 모아 두지 않으면 누락되기 쉽다.

### 10.1 Security

- visibility/filter 조건으로 block, mute, privacy setting, close friends가 논의됨.
- 인증/인가 모델, 비밀 관리, TLS, 저장 암호화는 {- 회의 미논의 -}.

### 10.2 Observability

- 제안된 follow-up metric:
  - fanout worker lag
  - feed cache hit ratio
  - hydration latency
- 구체 Metrics/Logs/Traces/Alerts 임계값은 {- 회의 미논의 -}.

### 10.3 Resilience

- Message Queue를 통해 Post Service와 Fanout Worker를 비동기 분리한다.
- 재시도, 서킷 브레이커, graceful degradation 정책은 {- 회의 미논의 -}.

### 10.4 Privacy

- block, mute, privacy setting, close friends 조건이 feed fanout filter로 논의됨.
- privacy 변경 시 기존 feed cache invalidation 전략은 follow-up.

---

## 11. Architecture Decisions (ADR)

> _Why_
> Michael Nygard ADR 형식. **"왜 이렇게 했나"가 코드보다 빠르게 잊힌다.** 결정 1건당 1 ADR.

### ADR-001: Hybrid Fanout Strategy

- **Status**: Proposed
- **Context**: Fanout on write는 read latency를 낮추지만 follower가 많은 celebrity user에서 write amplification이 폭증한다. Fanout on read는 write path가 가볍지만 read latency가 커진다.
- **Decision**: 일반 사용자는 fanout on write를 사용하고, celebrity user는 fanout on read로 처리한다.
- **Consequences**:
  - Positive: 일반 사용자의 피드 조회가 빠르고, celebrity write 폭증을 제한한다.
  - Negative: celebrity threshold와 read-time merge 로직이 추가로 필요하다.
  - Neutral: 사용자 유형별 fanout 경로가 달라진다.

### ADR-002: Store Post IDs, Not Full Objects, in News Feed Cache

- **Status**: Proposed
- **Context**: News Feed Cache에 post 본문, 작성자 프로필, 미디어 정보를 모두 저장하면 중복 저장과 invalidation 부담이 커진다.
- **Decision**: News Feed Cache는 `feed:{reader_user_id} -> ordered list of post_id` 형태로 저장하고, Feed Service가 Post Cache와 User Cache에서 hydrate한다.
- **Consequences**:
  - Positive: feed cache 크기를 줄이고, post/user 정보 변경을 별도 cache에서 관리할 수 있다.
  - Negative: feed read 시 Post Cache/User Cache hydration이 필요하다.
  - Neutral: Feed Service가 orchestration 역할을 가진다.

### ADR-003: Treat Cache Layers as Domain Namespaces Pending Further Review

- **Status**: Proposed
- **Context**: 회의에서 Alex Xu의 cache layer 그림이 L1/L2 같은 물리적 계층인지, 데이터 도메인별 namespace인지 논의했으나 확정하지 못했다.
- **Decision**: 현재 문서에서는 News Feed, Content/Post, Social Graph, Action, Counter를 데이터 특성별 cache namespace로 정리한다.
- **Consequences**:
  - Positive: 데이터 조회 패턴/변경 빈도/크기/invalidation 기준으로 캐시 책임을 분리할 수 있다.
  - Negative: 원문 의도 확인 전까지 물리적 배치/계층 구조는 확정할 수 없다.
  - Neutral: follow-up 조사 결과에 따라 수정될 수 있다.

---

## 12. Alternatives Considered

> _Why_
> 채택하지 않은 대안과 그 기각 이유. 6개월 뒤 누군가 "왜 X를 안 썼지?"라고 물을 때 답이 되는 섹션.

| Alternative | Pros | Cons | Why Rejected |
|---|---|---|---|
| Fanout on Write only | read path가 가장 단순하고 빠름 | celebrity user write amplification 폭증 | celebrity user에는 부적합 |
| Fanout on Read only | write path가 가벼움 | 모든 feed read가 follower post merge/sort 비용을 부담 | 일반 사용자 read latency가 커질 수 있음 |
| Full object in News Feed Cache | feed read 시 추가 조회 감소 | 캐시 크기 증가, profile/post 변경 invalidation 어려움, 동일 데이터 중복 | 회의 결론은 ID list + hydration |
| News Feed Cache `user_id` = author_id | 작성자 정보 조회 관점에서는 직관적 | reader별 feed 조회가 어려움. fanout 대상 follower 정보를 저장하는 목적과 불일치 | `reader_user_id -> post_id list`가 맞다고 정리 |

---

## 13. Risk Register (선택)

> _Why_
> 엔터프라이즈 SDD의 핵심. 각 위험에 등급과 완화 전략을 매핑.

| ID | Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| R-1 | celebrity fanout으로 feed-cache write 폭증 | High | High | Hybrid fanout, celebrity threshold 정의 | {Owner} |
| R-2 | News Feed Cache에 과도한 데이터 저장 시 메모리 증가와 invalidation 복잡도 증가 | Medium | High | `post_id` list만 저장, Post/User Cache hydration | {Owner} |
| R-3 | privacy/mute/block 변경 후 기존 feed cache와 visibility 불일치 | Medium | Medium | invalidation 전략 follow-up | {Owner} |
| R-4 | read QPS/fanout insert 수 미산정으로 capacity plan 부정확 | Medium | High | 사용자당 feed read 횟수, 평균 follower 수 가정 보강 | {Owner} |

---

## 14. Requirements Traceability (선택)

> _Why_
> IEEE 1016 핵심 항목. 요구사항 번호 ↔ 설계 섹션 매핑으로 커버리지를 증명.

| Req ID | Requirement | Design Section | Test Case |
|---|---|---|---|
| FR-1 | 게시글 발행 | §5, §7.1, §8.1 | {- 미정 -} |
| FR-2 | 뉴스 피드 조회 | §5, §7.3, §8.1 | {- 미정 -} |
| FR-3 | reverse chronological order | §6, §7.4 | {- 미정 -} |
| FR-4 | 모바일/웹 지원 | §2, §5 | {- 미정 -} |
| NFR-1 | 10M DAU / 5,000 friends/followees | §3, §4, §9 | {- 미정 -} |
| NFR-3 | 미디어 object storage + CDN | §5, §6 | {- 미정 -} |

---

## 15. Testing Strategy

> _Why_
> 무엇을 어떤 깊이로 검증할 것인지를 SDD에서 미리 합의. 구현 후 "그게 테스트 가능했나"를 막는다.

- **Unit**: {- 회의 미논의 -}
- **Integration**: {- 회의 미논의 -}
- **E2E**: 게시글 발행 후 대상 follower의 feed cache에 `post_id`가 들어가고, follower가 피드를 조회하면 hydrated feed item을 받는 흐름 후보
- **Load / Performance**:
  - write path 평균 116/sec, peak 230/sec 후보
  - read QPS는 사용자당 일일 feed read 횟수 확정 후 보강
  - fanout insert 수는 평균 follower 수 확정 후 보강
- **Chaos / Failure Injection**: {- 회의 미논의 -}
- **Acceptance**: {- PRD 수용 기준 미정 -}

---

## 16. Rollout / Deployment Plan

> _Why_
> 인터뷰 스타일과 가장 큰 차이점. 시스템은 코드가 아니라 운영되는 동안에만 가치 — 배포 / 마이그레이션 / 롤백을 설계의 일부로.

### 16.1 Phased Rollout

- Phase 1: {dev 환경 검증 — Day 0}
- Phase 2: {internal canary 1% — Day 7}
- Phase 3: {staged rollout 10% -> 50% -> 100% — Day 14–21}

### 16.2 Feature Flags

| Flag | Default | Removal Criteria |
|---|---|---|
| `new_engine_enabled` | off | {2주간 100% 트래픽에서 안정 후 제거} |

### 16.3 Data Migration

- 마이그레이션 단계: {dual-write -> backfill -> cutover -> cleanup}
- 롤백 가능 시점: {cutover 이전까지}

### 16.4 Rollback Plan

- 트리거: {error rate > 5% / 5min, p99 > 2× baseline}
- 절차: {flag off -> traffic shift -> 사후 분석}

---

## 17. Glossary

> _Why_
> 도메인 / 약어 통일. 문서 끝이 아니라 처음에 두는 팀도 많음.

| Term | Definition |
|---|---|
| DAU | Daily Active Users |
| QPS | Queries per second |
| Fanout on Write | 게시글 작성 시점에 follower들의 feed cache에 미리 push하는 방식 |
| Fanout on Read | 피드 조회 시점에 followee들의 최신 게시글을 pull/merge하는 방식 |
| Hybrid Fanout | 일반 사용자는 fanout on write, celebrity user는 fanout on read로 처리하는 혼합 전략 |
| News Feed Cache | `reader_user_id -> post_id list`를 저장하는 피드 인덱스 캐시 |
| Hydration | `post_id` 목록을 Post/User Cache 조회로 완전한 feed item으로 만드는 과정 |
| CDN | Content Delivery Network |

---

## 18. Appendix (선택)

- A. 다이어그램 원본 (PlantUML / Excalidraw 소스): {- 미정 -}
- B. 외부 참조 자료 / 벤치마크: Alex Xu Vol.1 Ch.11
- C. Capacity 추정 상세 계산:
  - Write QPS avg = 10,000,000 / 86,400 ≈ 116/sec
  - Write QPS peak = 116 × 2 ≈ 230/sec
  - Media storage/day = 10,000,000 × 10% × 1MB ≈ 1TB/day
  - Media storage/5y = 1TB × 365 × 5 ≈ 1.8PB ≈ 2PB rough cut
- D. 미해결 질문 (Open Questions):
  - [ ] 사용자당 일일 feed read 횟수는 얼마로 둘 것인가?
  - [ ] 평균 follower 수는 얼마로 둘 것인가?
  - [ ] celebrity threshold는 어떤 기준으로 정할 것인가?
  - [ ] News Feed Cache는 Redis List와 ZSet 중 무엇으로 모델링할 것인가?
  - [ ] cache layer는 물리적 계층인가, 논리적 namespace 분리인가?
  - [ ] block/mute/privacy 변경 시 기존 feed cache를 어떻게 invalidation할 것인가?
  - [ ] fanout worker lag, feed cache hit ratio, hydration latency의 알람 임계값은 무엇인가?

---

## 부록: 작성 체크리스트

- [x] **Goals**가 측정 가능한 숫자로 적혔는가
- [x] **Non-Goals**와 **Constraints**를 분리했는가
- [x] Architecture diagram이 텍스트 기반(diff 가능)인가
- [x] Data Lifecycle (생성 -> 삭제)이 명시되었는가
- [x] **Component**가 구현자가 코드로 옮길 수준까지 구체적인가
- [x] **NFR** 각 항목에 acceptance criteria가 있는가
- [x] Cross-cutting concerns에 **Observability**가 포함되었는가
- [x] 주요 결정마다 **ADR**이 1건씩 있는가
- [x] **Alternatives Considered**에 기각 이유가 적혔는가
- [ ] **Rollout Plan**에 롤백 트리거가 있는가 — *템플릿 상태, 회의 미논의*
- [x] Revision History가 갱신되었는가

---

## 참고 자료

- Alex Xu — System Design Interview Vol.1 Ch.11 (News Feed System Design)
- `../study-notes/meeting-summary.md`
- `../study-notes/meeting-transcript.md`
