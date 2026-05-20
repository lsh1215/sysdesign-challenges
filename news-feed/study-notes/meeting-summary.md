# 뉴스 피드 설계 — 스터디 회의 정리

**진행:** 작성자  
**참석:** 유경, 종민, 지민 외  
**다룬 챕터:** Alex Xu Vol.1 Ch.11 — 뉴스 피드 시스템 설계  
**원본 트랜스크립트:** `meeting-transcript.md`  
**이 문서:** AI 정리·요약본 (구조 + 핵심 결론 위주, 발화 흐름은 transcript 참조)

---

## 0. 회의 진행 방식

Notion AI/STT로 생성된 회의록을 기반으로, 뉴스 피드 시스템 설계의 핵심 논의만 정리했다. 화면 공유/세션 시작 문제, 반복 발화, 농담, 받아쓰기 오류는 제거했다.

이번 회의의 중심 질문:

- DAU 10M 규모에서 QPS와 저장소 요구량을 어떻게 추정할 것인가
- 게시글 발행과 뉴스 피드 조회 흐름을 어떻게 나눌 것인가
- fanout on write와 fanout on read 중 무엇을 선택할 것인가
- News Feed Cache에는 어떤 데이터를 저장해야 하는가
- 책의 cache layer는 물리적 계층인가, 도메인별 cache namespace인가

---

## 1. 문제 정의 (Clarifying)

### 1.1 기능 요구사항

| 항목 | 내용 |
|---|---|
| 게시글 발행 | 사용자는 텍스트, 이미지, 비디오가 포함된 게시글을 올릴 수 있다 |
| 뉴스 피드 조회 | 사용자는 친구/followee의 게시글을 피드로 볼 수 있다 |
| 정렬 기준 | 기본은 시간 역순(reverse chronological order) |
| 클라이언트 | 모바일과 웹 모두 지원 |

### 1.2 비기능 요구사항 / 제약

| 항목 | 내용 |
|---|---|
| DAU | 10M |
| 친구/followee 수 | 최대 5,000명/user |
| 읽기/쓰기 특성 | 뉴스 피드는 일반적으로 읽기 트래픽이 더 많음 |
| 미디어 | 이미지/비디오는 객체 저장소 + CDN으로 분리 필요 |
| 일관성 | 피드 조회는 약간 stale해도 되지만 빠르게 응답해야 함 |

---

## 2. Back-of-the-Envelope Estimation

회의 중 가장 중요한 정정: **DAU는 이미 daily active user이므로 "50%가 매일 사용" 같은 가정을 다시 곱하지 않는다.**

### 2.1 Write QPS

가정:

- DAU = 10M
- 활성 사용자당 하루 평균 1개 게시글 작성
- 하루 = 86,400초

| Metric | 계산 | 값 |
|---|---|---|
| 평균 write QPS | 10M / 86,400 | **≈ 116 QPS** |
| peak write QPS | 평균 × 2 | **≈ 230 QPS** |

### 2.2 Read QPS / Fanout Rough Cut

회의에서 명시적으로 계산한 값은 write QPS였지만, 뉴스 피드 설계에서는 read path와 fanout amplification도 핵심 산정 대상이다. 따라서 회의 결론을 설계 문서로 쓸 때는 다음 항목을 같이 둔다.

| Metric | 계산 / 가정 | 값 |
|---|---|---|
| 평균 feed read QPS | `DAU × 사용자당 일일 피드 조회 횟수 ÷ 86,400` | 추가 가정 필요 |
| peak feed read QPS | 평균 read QPS × peak factor | 추가 가정 필요 |
| 일반 사용자 fanout write | `post 1개 × follower 수` | 최대 5,000 feed-cache insert |
| 일일 feed-cache insert rough cut | `10M posts/day × 평균 follower 수` | 평균 follower 수 가정 필요 |
| celebrity fanout | follower 수가 수십만~수백만이면 write fanout 부적합 | fanout on read 후보 |

회의에서 평균 follower 수와 사용자당 feed read 횟수는 확정하지 않았다. 다만 이 빈칸이 바로 hybrid fanout 결정을 정당화하는 핵심 질문이다.

### 2.3 Media Storage

가정:

- 게시글 중 10%가 미디어 포함
- 미디어 평균 크기 = 1MB
- 보존 기간 = 5년

| Metric | 계산 | 값 |
|---|---|---|
| 일일 미디어 저장량 | 10M × 10% × 1MB | **≈ 1TB/day** |
| 5년 미디어 저장량 | 1TB × 365 × 5 | **≈ 1.8PB** |
| 설계상 rough cut | 여유 포함 | **≈ 2PB** |

---

## 3. High-Level Architecture

회의 결론상 뉴스 피드는 두 흐름으로 나눠 설명하는 것이 가장 명확하다.

1. **게시글 발행 경로**: post write + fanout
2. **뉴스 피드 조회 경로**: feed ID list read + hydration

```
Mobile/Web
   ↓
Load Balancer
   ↓
Web/API Server
   ├─────────────── 게시글 발행 경로 ───────────────┐
   │                                                ↓
   │                                         Post Service
   │                                                ↓
   │                                           Post DB
   │                                                ↓
   │                                        Message Queue
   │                                                ↓
   │                                         Fanout Worker
   │                                  ┌─────────────┴─────────────┐
   │                                  ↓                           ↓
   │                              Graph DB                  User Cache
   │                         follower list              visibility filter
   │                                  └─────────────┬─────────────┘
   │                                                ↓
   │                                       News Feed Cache
   │
   └─────────────── 뉴스 피드 조회 경로 ───────────────┐
                                                    ↓
                                             Feed Service
                                                    ↓
                                           News Feed Cache
                                                    ↓
                                      Post Cache / User Cache
                                                    ↓
                                              JSON Response

Media path: Object Storage → CDN
```

---

## 4. 게시글 발행 흐름

### 4.1 흐름

1. 사용자가 게시글을 작성한다.
2. Web/API Server가 요청을 받는다.
3. Post Service가 게시글을 Post DB에 저장한다.
4. post-created event를 Message Queue에 넣는다.
5. Fanout Worker가 이벤트를 소비한다.
6. Graph DB/Follow Store에서 작성자의 follower 목록을 가져온다.
7. User Cache/User Service에서 privacy, mute, block, close friends 같은 visibility/filter 정보를 확인한다.
8. fanout 대상 사용자의 News Feed Cache에 `post_id`를 추가한다.

알림 설정(notification preference)은 feed cache 삽입 여부가 아니라 push notification 발송 여부에 영향을 주는 별도 조건으로 본다.

### 4.2 Graph DB와 User Cache가 필요한 이유

| 컴포넌트 | 필요한 이유 |
|---|---|
| Graph DB / Follow Store | 작성자의 follower/friend 목록을 빠르게 조회 |
| User Cache | 차단, mute, privacy setting, 친한 친구 등 feed fanout 대상 필터링 |

단순히 follower ID만 있으면 되는 것이 아니라, "이 사용자에게 이 게시글을 보여줘도 되는가" 판단이 필요하다.

---

## 5. Fanout Strategy

### 5.1 Fanout on Write

게시글이 생성되는 순간 follower들의 피드 캐시에 미리 넣는 방식.

| 장점 | 단점 |
|---|---|
| 피드 조회가 매우 빠름 | follower가 많은 사용자는 write amplification이 큼 |
| read path가 단순함 | celebrity user가 게시글을 쓰면 fanout 비용 폭증 |

### 5.2 Fanout on Read

사용자가 피드를 읽는 순간 친구 목록을 가져오고, 친구들의 최근 게시글을 모아 정렬하는 방식.

| 장점 | 단점 |
|---|---|
| write path가 가벼움 | read latency가 큼 |
| celebrity write에 강함 | 매 조회마다 merge/sort 비용 발생 |

### 5.3 회의 결론: Hybrid Fanout

| 사용자 유형 | 전략 | 이유 |
|---|---|---|
| 일반 사용자 | Fanout on Write | follower 수가 제한적이면 read latency를 낮추는 것이 유리 |
| 유명인/셀럽 | Fanout on Read | follower 수가 너무 커서 write fanout 비용이 비현실적 |

즉, 기본은 **push model(fanout on write)** 이고, celebrity user는 **pull model(fanout on read)** 로 예외 처리한다.

---

## 6. 뉴스 피드 조회 흐름

### 6.1 흐름

1. 사용자가 `GET /v1/me/feed?cursor=&limit=` 요청을 보낸다.
2. Feed Service가 News Feed Cache에서 사용자의 `post_id` 목록을 가져온다.
3. Post Cache/Post DB에서 post body, media reference, author_id를 가져온다.
4. User Cache/User DB에서 작성자 프로필을 가져온다.
5. Feed Service가 hydrated feed item을 구성한다.
6. 미디어는 CDN URL을 통해 클라이언트가 로드한다.

### 6.2 왜 News Feed Cache에 전체 데이터를 넣지 않는가

News Feed Cache에 post 본문, 작성자 프로필, 미디어 정보까지 모두 넣으면 다음 문제가 생긴다.

- 캐시 크기가 급격히 커진다.
- 작성자 프로필 변경 시 feed cache 전체 invalidation이 어려워진다.
- 게시글 수정/삭제/visibility 변경 반영이 어려워진다.
- 동일 post data가 여러 사용자의 feed cache에 중복 저장된다.

따라서 News Feed Cache는 보통 **가벼운 ID list** 역할을 한다.

---

## 7. News Feed Cache 데이터 모델

회의 중 가장 오래 논의한 부분.

### 7.1 헷갈린 지점

책의 그림/설명에서 `post_id`와 `user_id`가 함께 언급되는데, 여기서 `user_id`가 누구인지 혼동이 있었다.

가능한 해석:

1. 게시글 작성자 user_id
2. 피드를 읽을 사용자 user_id

### 7.2 결론

**News Feed Cache의 key가 되는 `user_id`는 피드를 읽을 사용자, 즉 fanout의 수신자 ID다.**

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

### 7.3 작성자 정보는 어디서 가져오는가

작성자 ID는 News Feed Cache의 key가 아니다.

작성자 정보는 다음 순서로 얻는다.

```text
post_id
  ↓
Post Cache / Post DB
  ↓
post.author_id
  ↓
User Cache / User DB
  ↓
작성자 이름, 프로필 이미지 등
```

### 7.4 최종 데이터 모델

| 저장소 | Key | Value | 역할 |
|---|---|---|---|
| News Feed Cache | `feed:{reader_user_id}` | ordered list of `post_id` | 사용자가 볼 게시글 ID 목록 |
| Post Cache | `post:{post_id}` | post body, media refs, author_id, created_at | 게시글 상세 |
| User Cache | `user:{author_id}` | display name, profile image, metadata | 작성자 표시 정보 |

---

## 8. API 설계

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/v1/me/feed` | 게시글 발행 |
| `GET` | `/v1/me/feed?cursor=&limit=` | 내 뉴스 피드 조회 |
| `GET` | `/v1/posts/{postId}` | 게시글 상세 조회 |

회의에서는 상세 request/response schema까지는 다루지 않았다. 핵심은 `POST`와 `GET` 흐름이 각각 fanout/write path와 feed read/hydration path로 이어진다는 점이다.

---

## 9. Cache Layer / Cache Namespace

### 9.1 회의 중 의문

책의 cache 구조가 다음처럼 나뉘는 부분이 있다.

- News Feed
- Content/Post
- Social Graph
- Action
- Counter

이걸 L1/L2 캐시처럼 물리적 계층으로 봐야 하는지, 아니면 데이터 도메인별 네임스페이스 분리로 봐야 하는지 논의했다.

### 9.2 임시 결론

현재 이해는 **물리적 cache hierarchy라기보다 데이터 특성별 cache namespace 분리**에 가깝다.

| Cache 영역 | 저장 데이터 | 분리 이유 |
|---|---|---|
| News Feed | user별 post_id list | 피드 조회 경로 최적화 |
| Content/Post | post body, media refs, author_id | 게시글 상세 hydration |
| Social Graph | follower/followee 관계 | fanout 대상 조회 |
| Action | like/comment/reply 상태 | 사용자별 interaction state |
| Counter | like count, comment count, view count | 매우 빈번한 count read/write |

분리 기준은 대략 다음과 같다.

- 조회 패턴
- 변경 빈도
- 데이터 크기
- TTL / invalidation 방식
- write/read hotness

### 9.3 Follow-up

이 부분은 회의에서 완전히 결론내지 못했다. 다음 회의 전까지 "Alex Xu 뉴스 피드 장의 cache layer가 정확히 어떤 의미인지" 조사하기로 했다.

---

## 10. 핵심 결론

1. DAU 10M은 이미 하루 활성 사용자 수이므로, 별도 50% daily active 가정을 다시 곱하지 않는다.
2. 평균 write QPS는 약 116, peak write QPS는 약 230으로 잡는다.
3. read QPS, 평균 follower 수, fanout insert 수는 추가 가정이 필요하지만, fanout 전략을 정하는 핵심 산정값이다.
4. 미디어 저장소는 10% media, 1MB 평균 기준 약 1TB/day, 5년 약 1.8PB로 추정한다.
5. 게시글 발행 경로와 뉴스 피드 조회 경로는 분리해서 설명한다.
6. 기본 fanout 전략은 fanout on write, celebrity user는 fanout on read로 처리하는 hybrid fanout이다.
7. News Feed Cache는 전체 게시글 내용이 아니라 `reader_user_id -> post_id list`를 저장한다.
8. 작성자 정보는 `post_id -> Post Cache -> author_id -> User Cache` 순서로 hydrate한다.
9. Cache layer는 우선 데이터 도메인별 cache namespace 분리로 이해하되, 세부 의미는 follow-up으로 남긴다.

---

## 11. 다음 회차 / 추가 조사 항목

- Alex Xu의 cache layer 구조가 물리적 계층인지, 논리적 namespace 분리인지 확인
- 사용자당 일일 feed read 횟수와 평균 follower 수를 가정해 read QPS/fanout insert 수 보강
- 사용자별 feed cache를 Redis List/ZSet 중 무엇으로 모델링할지 검토
- celebrity threshold를 어떤 기준으로 정할지 검토
- block/mute/privacy 변경 시 기존 feed cache invalidation 전략 검토
- fanout worker lag, feed cache hit ratio, hydration latency에 대한 monitoring 지표 정리
