# News Feed System — System Design (Mock Interview Style)

---

## 0. 메타데이터 (선택)

| 항목 | 값 |
|---|---|
| Topic | News Feed System (Alex Xu Vol.1 Ch.11) |
| Interviewer Persona | Big Tech L5 / Staff Engineer |
| Time Budget | 45 / 60 min |
| Date | 2026-05-20 |
| Source Notes | `../study-notes/meeting-summary.md`, `../study-notes/meeting-transcript.md` |

---

## 1. Clarifying Questions

> _Why this section exists_
> 문제 범위를 좁히고 인터뷰어와 공통 전제를 맞춘다. **너무 빨리 설계로 넘어가는 것이 가장 흔한 실패 원인.** 5분 이내, 3–5개 질문이 적정.

### 1.1 Functional Requirements

> _What goes here_
> "유저는 …할 수 있어야 한다" 형태로 핵심 기능 3개에 집중. 부가 기능은 나중에.

- [x] FR-1: 사용자가 게시글을 발행할 수 있다 (텍스트, 이미지, 비디오)
- [x] FR-2: 사용자가 친구/followee의 게시글로 구성된 뉴스 피드를 볼 수 있다
- [x] FR-3: 피드는 기본적으로 reverse-chronological order로 제공한다
- [x] FR-4: 모바일과 웹 클라이언트를 모두 지원한다

### 1.2 Non-Functional Requirements (정량화 필수)

> _What goes here_
> "낮은 지연" 같은 형용사가 아니라 숫자로. availability / consistency / latency / scalability / durability 중 시스템에 맞는 top 3–5개를 골라 수치 명시.

- [x] NFR-1 (확장성): 10M DAU, 사용자당 최대 5,000 friends/followees를 처리한다
- [x] NFR-2 (읽기 성능): 뉴스 피드는 읽기 트래픽이 많으므로 read path를 빠르게 유지한다
- [x] NFR-3 (미디어): 이미지/비디오는 객체 저장소 + CDN 경로로 분리한다
- [x] NFR-4 (일관성): 피드 조회는 약간 stale한 데이터를 허용할 수 있다
- [ ] NFR-5 (피드 조회 latency): {p99 목표 미정 — 회의에서 숫자 미논의}

### 1.3 Scale & Constraints

> _What goes here_
> 트래픽 규모와 데이터 특성. 이 숫자가 뒤에 나올 추정·샤딩·캐시 결정의 근거가 된다.

| 항목 | 값 | 근거 |
|---|---|---|
| DAU | 10M | 회의 합의 / Alex Xu Ch.11 기본 가정 |
| 친구/followee 수 | 최대 5,000 / user | 회의 합의 / Alex Xu Ch.11 기본 가정 |
| 게시글 작성 빈도 | 활성 사용자당 1 post/day | 회의 중 계산 편의를 위해 합의 |
| 게시글 미디어 비율 | 10% | 회의 합의 |
| 평균 미디어 크기 | 1MB | 회의 합의 |
| 미디어 보존 기간 | 5년 | 회의 합의 |
| 정렬 기준 | reverse chronological order | 회의 합의 |
| 읽기:쓰기 비율 | High read-to-write | 뉴스 피드 특성으로 논의 |
| 평균 follower 수 | {- 회의 미논의 -} | fanout insert 수 산정에 필요 |
| 사용자당 일일 feed read 횟수 | {- 회의 미논의 -} | read QPS 산정에 필요 |
| 글로벌 여부 | {- 회의 미논의 -} | |

### 1.4 Out of Scope (명시적 제외)

> _Why_
> 인터뷰어와 합의해 시간을 사야 한다. "인증/결제/관리자 도구는 다음 단계로 두자"고 못 박는다.

- [ ] 사용자 인증/회원가입 — {- 명시 논의 없음, 기존 시스템 존재 가정 필요 -}
- [ ] 랭킹/추천 ML — 기본 reverse-chronological feed만 논의
- [ ] 광고/수익화 — {- 회의 미논의 -}
- [ ] 관리자 대시보드/콘텐츠 모더레이션 — {- 회의 미논의 -}
- [ ] 댓글/좋아요/공유 상세 기능 — cache namespace 예시로만 언급, 기능 설계는 미논의

---

## 2. High Level Design

> _Why this section exists_
> 전체 시스템의 뼈대를 스케치하고 인터뷰어의 동의를 얻는다. 박스/화살표가 없으면 안 된다 — 말로만 설명하지 말 것.

### 2.1 Back-of-the-Envelope Estimation

> _What goes here_
> 설계 결정을 정당화하는 숫자. 5분 초과 금지. 단위 표기 필수.

| Metric | 계산 | 값 |
|---|---|---|
| Write QPS (avg) | 10M posts/day ÷ 86,400 sec/day | **≈ 116/sec** |
| Write QPS (peak) | avg × 2 | **≈ 230/sec** |
| 일일 미디어 저장량 | 10M posts/day × 10% × 1MB | **≈ 1TB/day** |
| 5년 미디어 저장량 | 1TB/day × 365 × 5 | **≈ 1.8PB**, rough cut **≈ 2PB** |
| 일반 사용자 fanout write | post 1개 × follower 수 | 최대 5,000 feed-cache insert |
| Read QPS (avg) | 10M DAU × 일일 feed read 횟수 ÷ 86,400 | {- 일일 read 횟수 미정 -} |
| 일일 feed-cache insert | 10M posts/day × 평균 follower 수 | {- 평균 follower 수 미정 -} |

> _Note:_ 회의에서 실제 계산한 값은 write QPS와 media storage다. read QPS와 평균 follower 수는 hybrid fanout을 정당화하는 핵심 산정값이지만, 이번 회의에서는 숫자를 확정하지 않았다.

### 2.2 Core Entities (Data Model 초안)

> _What goes here_
> 핵심 명사만 나열. 컬럼은 5개 이내로 시작. 풀 스키마는 Drill Down에서.

- **User**: id, profile, privacy_settings, created_at
- **Post**: id, author_id, text, media_refs, created_at
- **FollowEdge / FriendEdge**: follower_id, followee_id, created_at
- **FeedItem / Feed Cache Entry**: reader_user_id, post_id, created_at
- **MediaObject**: id, storage_url, cdn_url, type, metadata

### 2.3 API Contract

> _What goes here_
> Functional Requirement 1:1 매핑. REST 기본. 인증 토큰은 헤더.

| Method | Path | Purpose | Auth |
|---|---|---|---|
| `POST` | `/v1/me/feed` | 게시글 발행 | Bearer |
| `GET` | `/v1/me/feed?cursor=&limit=` | 내 뉴스 피드 조회 | Bearer |
| `GET` | `/v1/posts/{postId}` | 게시글 상세 조회 | Bearer |

### 2.4 Architecture Diagram

> _What goes here_
> 클라이언트 → LB → API → 캐시/DB/큐의 박스+화살표. ASCII가 가장 빠르다.

<img width="500" height="600" alt="image" src="https://github.com/user-attachments/assets/67fb53f1-9218-4383-af90-cb5ff65a2c95" />
<img width="500" height="600" alt="image" src="https://github.com/user-attachments/assets/5f4c7b3f-d17e-46dd-b13c-8406d284fc0c" />

---

## 3. Drill Down

> _Why this section exists_
> Non-functional requirement 중 High Level이 아직 충족 못한 것을 메우는 단계. **시니어는 인터뷰어 힌트를 기다리지 않고 직접 병목을 찾아 제안한다.** 모든 영역을 고르게 파지 말고 가장 중요한 1–2개를 깊게.

### 3.1 게시글 발행 흐름

회의에서 합의한 발행 경로:

1. 사용자가 게시글을 작성한다.
2. Web/API Server가 요청을 받는다.
3. Post Service가 게시글을 Post DB에 저장한다.
4. `post-created` event를 Message Queue에 넣는다.
5. Fanout Worker가 이벤트를 소비한다.
6. Graph DB/Follow Store에서 작성자의 follower 목록을 가져온다.
7. User Cache/User Service에서 privacy, mute, block, close friends 같은 visibility/filter 정보를 확인한다.
8. fanout 대상 사용자의 News Feed Cache에 `post_id`를 추가한다.

> _Note:_ 알림 설정(notification preference)은 feed cache 삽입 여부가 아니라 push notification 발송 여부에 영향을 주는 별도 조건으로 본다.

### 3.2 Fanout Strategy

#### Fanout on Write

게시글이 생성되는 순간 follower들의 피드 캐시에 미리 넣는 방식.

| 장점 | 단점 |
|---|---|
| 피드 조회가 매우 빠름 | follower가 많은 사용자는 write amplification이 큼 |
| read path가 단순함 | celebrity user가 게시글을 쓰면 fanout 비용 폭증 |

#### Fanout on Read

사용자가 피드를 읽는 순간 친구 목록을 가져오고, 친구들의 최근 게시글을 모아 정렬하는 방식.

| 장점 | 단점 |
|---|---|
| write path가 가벼움 | read latency가 큼 |
| celebrity write에 강함 | 매 조회마다 merge/sort 비용 발생 |

#### 회의 결론: Hybrid Fanout

| 사용자 유형 | 전략 | 이유 |
|---|---|---|
| 일반 사용자 | Fanout on Write | follower 수가 제한적이면 read latency를 낮추는 것이 유리 |
| 유명인/셀럽 | Fanout on Read | follower 수가 너무 커서 write fanout 비용이 비현실적 |

즉, 기본은 **push model(fanout on write)** 이고, celebrity user는 **pull model(fanout on read)** 로 예외 처리한다.

### 3.3 뉴스 피드 조회 흐름

1. 사용자가 `GET /v1/me/feed?cursor=&limit=` 요청을 보낸다.
2. Feed Service가 News Feed Cache에서 사용자의 `post_id` 목록을 가져온다.
3. Post Cache/Post DB에서 post body, media reference, author_id를 가져온다.
4. User Cache/User DB에서 작성자 프로필을 가져온다.
5. Feed Service가 hydrated feed item을 구성한다.
6. 미디어는 CDN URL을 통해 클라이언트가 로드한다.

### 3.4 News Feed Cache 데이터 모델

회의 중 가장 중요하게 정리한 결론:

**News Feed Cache의 key가 되는 `user_id`는 게시글 작성자가 아니라 피드를 읽을 사용자, 즉 fanout의 수신자 ID다.**

예시:

```text
작성자 A가 post_1을 발행
A의 follower = B, C, D

fanout 결과:
feed:B -> [post_1, ...]
feed:C -> [post_1, ...]
feed:D -> [post_1, ...]
```

피드 조회 시:

```text
B가 피드 조회
  ↓
News Feed Cache에서 feed:B 조회
  ↓
[post_1, post_7, post_9, ...] 획득
  ↓
Post Cache에서 post 상세 조회
  ↓
post.author_id로 User Cache 조회
  ↓
hydrated feed item 생성
```

| 저장소 | Key | Value | 역할 |
|---|---|---|---|
| News Feed Cache | `feed:{reader_user_id}` | ordered list of `post_id` | 사용자가 볼 게시글 ID 목록 |
| Post Cache | `post:{post_id}` | post body, media refs, author_id, created_at | 게시글 상세 |
| User Cache | `user:{author_id}` | display name, profile image, metadata | 작성자 표시 정보 |

### 3.5 Cache Layer / Cache Namespace

회의에서 완전히 결론내지 못한 부분. 현재 이해는 **물리적 L1/L2 cache hierarchy라기보다 데이터 특성별 cache namespace 분리**에 가깝다.

| Cache 영역 | 저장 데이터 | 분리 이유 |
|---|---|---|
| News Feed | user별 post_id list | 피드 조회 경로 최적화 |
| Content/Post | post body, media refs, author_id | 게시글 상세 hydration |
| Social Graph | follower/followee 관계 | fanout 대상 조회 |
| Action | like/comment/reply 상태 | 사용자별 interaction state |
| Counter | like count, comment count, view count | 매우 빈번한 count read/write |

분리 기준 후보:

- 조회 패턴
- 변경 빈도
- 데이터 크기
- TTL / invalidation 방식
- write/read hotness

> _Follow-up:_ Alex Xu 뉴스 피드 장의 cache layer가 정확히 물리적 계층인지, 논리적 namespace 분리인지 추가 확인 필요.

### 3.6 Database

> _Decisions to make_
> SQL vs NoSQL, 샤딩 전략, 복제 토폴로지, 인덱스, 파티셔닝.

- **Post DB**: 게시글 본문/메타데이터 저장. `post_id -> author_id` 조회가 feed hydration에 필요
- **Graph DB / Follow Store**: follower/followee 관계 조회. fanout 시 follower list를 빠르게 읽어야 함
- **Feed Cache/Store**: `reader_user_id -> ordered post_id list`
- **Media Storage**: 이미지/비디오 원본은 object storage, serving은 CDN
- **샤딩 키 후보**: {- 회의 미논의 -}
- **제품 선택**: {- 회의 미논의 -}

### 3.7 Bottlenecks & Mitigations

> _Decisions to make_
> 어디가 먼저 깨질지 식별하고 완화책 제시.

| Bottleneck | 식별 근거 | 완화 |
|---|---|---|
| Celebrity fanout | follower 수가 큰 사용자는 post 1개가 수십만~수백만 feed-cache write를 유발 | Hybrid fanout: 일반 유저는 push, celebrity는 pull |
| Feed cache bloat | News Feed Cache에 전체 post/user/media 데이터를 넣으면 중복 저장과 invalidation 문제가 커짐 | feed cache에는 `post_id` list만 저장하고 Post/User Cache에서 hydrate |
| Graph lookup pressure | fanout마다 follower list 대량 조회 필요 | Graph DB/Follow Store + Social Graph Cache 후보 |
| Media bandwidth | 이미지/비디오가 feed API를 압도할 수 있음 | Object Storage + CDN 분리 |
| Queue backlog | post-created 이벤트 생산 속도 > Fanout Worker 처리 속도 | {- 회의에서 worker scale-out/lag monitoring 구체 논의 없음 -} |

### 3.8 Load Balancer

> _Decisions to make_
> L4(빠름) vs L7(라우팅 규칙), sticky session 필요성, health check.

{- 회의 미논의 -}

### 3.9 Failure Modes

> _Decisions to make_
> SPOF, 장애 전파, graceful degradation.

{- 회의 미논의 -}

### 3.10 Monitoring & Observability

> _Decisions to make_
> 무엇을 보고 무엇에 알람을 걸 것인가.

{- 회의 미논의. 단, 다음 조사 항목으로 fanout worker lag, feed cache hit ratio, hydration latency가 제안됨 -}

### 3.11 Security (선택)

> _Decisions to make_
> 인증/인가, rate limiting, 입력 검증, abuse 방지.

{- 회의 미논의. 단, visibility/filter 조건으로 block, mute, privacy setting, close friends가 논의됨 -}

---

## 4. Wrap-Up

> _Why this section exists_
> Alex Xu 4단계의 마지막. 3–5분 이내로 압축.

### 4.1 트레이드오프 요약

- **Fanout on Write vs Fanout on Read**: 일반 사용자는 read latency를 낮추기 위해 write 시점 push, celebrity user는 write amplification을 피하기 위해 read 시점 pull.
- **Feed Cache에 full object 저장 vs ID list 저장**: full object는 조회가 단순하지만 캐시 크기와 invalidation 부담이 큼. 회의 결론은 `reader_user_id -> post_id list` 저장 후 Post/User Cache에서 hydrate.
- **Cache layer 해석**: 물리적 계층으로 확정하지 않고, 우선 데이터 도메인별 cache namespace 분리로 이해. 추가 확인 필요.

### 4.2 운영 고려사항

{- 배포 전략, 마이그레이션, 롤백은 회의 미논의 -}

### 4.3 다음 단계 / 확장 방향

- Alex Xu의 cache layer 구조가 물리적 계층인지, 논리적 namespace 분리인지 확인
- 사용자당 일일 feed read 횟수와 평균 follower 수를 가정해 read QPS/fanout insert 수 보강
- 사용자별 feed cache를 Redis List/ZSet 중 무엇으로 모델링할지 검토
- celebrity threshold를 어떤 기준으로 정할지 검토
- block/mute/privacy 변경 시 기존 feed cache invalidation 전략 검토
- fanout worker lag, feed cache hit ratio, hydration latency에 대한 monitoring 지표 정리

---

## 부록: 자주 빠뜨리는 것 체크리스트

- [x] Non-functional requirement를 **숫자로** 적었는가 (DAU 10M, max 5,000 friends/followees, write QPS 116/230, storage 2PB)
- [x] QPS 추정에 **peak 배수**를 곱했는가 (회의 합의 ×2)
- [x] Architecture diagram에 **데이터 흐름 방향**이 있는가
- [x] DB 선택을 **access pattern**으로 정당화했는가 (Post hydration, Graph fanout, Feed ID list)
- [ ] Read QPS 산정에 필요한 사용자당 일일 feed read 횟수를 확정했는가 — *회의 미논의*
- [ ] 평균 follower 수를 확정해 fanout insert 수를 계산했는가 — *회의 미논의*
- [ ] Cache layer의 정확한 의미를 확인했는가 — *follow-up*
