# 뉴스 피드 설계 — 스터디 회의록 (트랜스크립트)

**진행:** 작성자  
**참석:** 유경, 종민, 지민 외  
**다룬 챕터:** Alex Xu Vol.1 Ch.11 — 뉴스 피드 시스템 설계  
**원본:** Notion AI 회의록/STT 변환본. 받아쓰기 오류를 보정하되, 화면공유 문제·반복 발화·잡담은 제거해 기술 논의 중심으로 재구성.

---

## 0. 회의 시작

**작성자**: 세션을 먼저 시작해야 동기화가 되는 거였네요. 이제 보이니까 시작하겠습니다.

---

## 1. 문제 정의 (Clarifying)

**작성자**: 기본 요구사항부터 정리하겠습니다. DAU는 1,000만 명으로 잡습니다.

**참석자**: 책에 나온 값이 맞습니다. DAU 10M.

**작성자**: 사용자당 친구 수 또는 followee 수는 최대 5,000명으로 가정합니다. 뉴스 피드는 시간 역순(reverse chronological order)이고, 텍스트뿐 아니라 이미지와 비디오도 포함됩니다. 모바일과 웹을 모두 지원합니다.

**참석자**: 핵심 기능은 사용자가 게시글을 발행하고, 다른 사용자가 친구들의 게시글을 뉴스 피드로 보는 것입니다.

**작성자**: 맞습니다. 뉴스 피드라고 하지만 페이스북 피드나 인스타그램 피드처럼 생각하면 됩니다. 먼저 게시글 발행과 피드 조회를 나눠서 봐야 합니다.

---

## 2. Back-of-the-Envelope Estimation

**작성자**: 설계 전에 규모 산정을 해야 합니다. 책의 트위터 QPS/저장소 추정 방식과 비슷하게 가정해보겠습니다. 다만 우리 문제는 이미 DAU가 1,000만 명으로 주어졌으니, "월간 사용자의 50%가 매일 사용한다" 같은 가정을 다시 곱하면 안 됩니다.

**참석자**: DAU 자체가 daily active user라서 이미 하루 활성 사용자 수입니다. 여기서 50%를 다시 곱하면 중복 가정입니다.

**작성자**: 맞습니다. 그러면 하루에 활성 사용자 1,000만 명이 있고, 사용자가 평균 하루 1개의 게시글을 올린다고 가정하겠습니다.

### 2.1 Write QPS

**작성자**: 평균 write QPS는 `10M posts/day ÷ 86,400 seconds/day` 입니다.

**참석자**: 계산하면 약 115.74 QPS니까 **116 QPS**로 잡으면 됩니다.

**작성자**: peak QPS는 평균의 2배 정도로 보고 **약 230 QPS**로 잡겠습니다. 조금 넉넉히 말하면 230~240 QPS 정도입니다. 실제 설계에서는 피드 조회 QPS와 fanout으로 인해 발생하는 내부 write 수가 더 중요해질 수 있으니, 여기서는 회의에서 계산한 write QPS를 기준점으로 두겠습니다.

### 2.2 Media Storage

**작성자**: 미디어 저장소 요구량도 가정해야 합니다. 게시글 중 10%가 미디어를 포함하고, 미디어 평균 크기는 1MB로 잡겠습니다.

**참석자**: 그러면 하루 미디어 저장량은 `10M posts/day × 10% × 1MB = 1TB/day` 입니다.

**작성자**: 5년 보존 기준이면 `1TB/day × 365 × 5 = 1,825TB`, 즉 대략 **1.8PB**, 여유 있게 **약 2PB**로 잡으면 됩니다.

**참석자**: 세부 단위는 면접에서 너무 깊게 들어가기보다, 이 정도 규모라는 감을 잡는 게 중요합니다.

**작성자**: 맞습니다. 중요한 것은 QPS와 미디어 저장 규모가 설계 판단의 근거가 된다는 점입니다.

---

## 3. High-Level Components

**작성자**: 이제 기능 흐름을 나눠보겠습니다. 크게는 게시글 발행 경로와 뉴스 피드 조회 경로가 있습니다.

**참석자**: 게시글 발행에는 게시글 저장, fanout, 알림 같은 기능이 필요합니다.

**작성자**: 그렇습니다. 사용자가 게시글을 발행하면 웹 서버를 거쳐 Post Service가 게시글을 저장합니다. 이후 Fanout Service가 팔로워 또는 친구 목록을 조회해서, 해당 사용자들의 뉴스 피드 캐시에 새 게시글 ID를 반영합니다.

**참석자**: 그러면 News Feed Service는 별도 서비스로 보는 게 맞나요? 아니면 Post Service 내부 기능으로 봐도 되나요?

**작성자**: 흐름 설명을 위해 분리해서 말하고 있지만, 실제 시스템 아키텍처 그림에서는 하나의 큰 피드 시스템 안에 Post Service, Fanout Service, Feed Service가 있다고 보면 됩니다. 쓰기 경로와 읽기 경로의 부하 특성이 다르기 때문에 서비스 분리 후보가 됩니다.

**참석자**: 저장과 조회는 성격이 다릅니다. 피드 조회는 훨씬 많이 발생하고, 사용자별 피드 조합이 필요하므로 별도 Feed Service로 두는 게 합리적입니다.

**작성자**: 맞습니다. 단순히 "조회가 많을 것 같다"가 아니라, 읽기/쓰기 부하, 트랜잭션 범위, 캐시 전략, fanout 작업의 비동기성 때문에 분리를 검토한다고 설명해야 합니다.

---

## 4. 게시글 발행 흐름 (Feed Publishing Flow)

**작성자**: 게시글을 발행하는 흐름을 먼저 그려봅시다.

1. 사용자가 모바일/웹 클라이언트에서 게시글을 작성합니다.
2. 요청은 Load Balancer와 Web Server를 거칩니다.
3. Post Service가 게시글 본문과 메타데이터를 Post DB에 저장합니다.
4. 게시글 생성 이벤트가 Message Queue에 들어갑니다.
5. Fanout Worker가 이벤트를 소비합니다.
6. Graph DB 또는 Follow Store에서 작성자의 follower/friend 목록을 조회합니다.
7. User Service/User Cache에서 차단, 음소거, 친한 친구, privacy setting 같은 visibility/filter 정보를 확인합니다.
8. 일반 사용자의 follower에게는 News Feed Cache에 `post_id`를 push합니다.

**참석자**: Graph DB를 쓰는 이유는 follower/followee 관계를 빠르게 조회하기 위해서입니다.

**작성자**: 그리고 User Cache를 보는 이유는 모든 친구에게 무조건 보내면 안 되기 때문입니다. 차단, mute, privacy setting, close friends 같은 조건이 있으면 feed fanout 대상에서 필터링해야 합니다. 알림 설정은 피드 삽입 여부보다는 push notification을 보낼지 판단하는 별도 조건에 가깝습니다.

**참석자**: 책에서는 메시지 큐를 거쳐 Fanout Worker가 뉴스 피드 캐시에 넣는 방식입니다.

**작성자**: 맞습니다. 일반적인 사용자는 write 시점에 fanout 하는 것이 기본이고, 유명인/셀럽처럼 follower가 너무 많은 사용자는 예외 처리가 필요합니다.

---

## 5. Fanout on Write vs Fanout on Read

**작성자**: fanout을 쓰기 시점에 할지, 읽기 시점에 할지 비교해야 합니다.

### 5.1 Fanout on Write

**참석자**: 쓰기 시점 fanout은 게시글을 작성할 때 follower들의 피드 캐시에 미리 넣어두는 방식입니다. 읽을 때는 빠릅니다.

**작성자**: 장점은 뉴스 피드 조회가 빠르다는 것입니다. 사용자가 피드를 열면 이미 준비된 `post_id` 목록을 읽으면 됩니다.

**참석자**: 단점은 팔로워가 많은 사용자가 게시글을 올릴 때 write amplification이 큽니다. 한 번의 게시글 발행이 수천, 수만, 수백만 개의 피드 캐시 업데이트로 이어질 수 있습니다.

### 5.2 Fanout on Read

**참석자**: 읽기 시점 fanout은 사용자가 피드를 조회하는 순간 친구 목록을 가져오고, 친구들의 최근 게시글을 조회해서 정렬하는 방식입니다.

**작성자**: 장점은 쓰기 경로가 가볍다는 것입니다. 단점은 피드를 읽을 때마다 많은 조합과 정렬이 필요해서 latency가 커질 수 있습니다.

### 5.3 결론: Hybrid Fanout

**참석자**: 일반 사용자는 fanout on write를 쓰고, 유명인/셀럽 계정은 fanout on read로 처리하는 하이브리드 방식이 좋습니다.

**작성자**: 저도 그렇게 봅니다. 일반적인 모델에서는 쓰기 시점 fanout이 더 적합하고, 유명인은 follower 수가 너무 많으므로 read 시점에 merge/pull하는 방식으로 처리합니다.

---

## 6. 뉴스 피드 읽기 흐름 (Feed Retrieval Flow)

**작성자**: 이제 피드를 읽는 흐름을 봅시다.

1. 사용자가 `GET /feed` 요청을 보냅니다.
2. 요청은 Load Balancer와 Web Server를 거칩니다.
3. Feed Service가 News Feed Cache에서 해당 사용자의 `post_id` 목록을 조회합니다.
4. Post Cache/Post DB에서 각 post의 본문, media reference, author_id를 가져옵니다.
5. User Cache/User DB에서 작성자 프로필 정보를 가져옵니다.
6. 미디어는 CDN URL을 통해 클라이언트가 로드합니다.
7. Feed Service가 hydrated feed item 목록을 JSON으로 응답합니다.

**참석자**: 여기서 궁금한 점이 있습니다. News Feed Cache에 사용자 정보와 게시글 정보를 모두 저장해두면, 왜 Post Cache와 User Cache를 다시 조회해야 하나요?

**작성자**: 좋은 질문입니다. News Feed Cache에는 전체 게시글 내용을 저장하는 게 아니라, 보통 사용자별 `post_id` 목록만 저장합니다. 캐시가 너무 커지는 걸 막고, 게시글/사용자 정보 변경을 별도 캐시에서 관리하기 위해서입니다.

**참석자**: 그러면 News Feed Cache는 `user_id -> list(post_id)` 구조에 가깝고, 실제 내용은 Post Cache와 User Cache에서 hydrate 하는 구조군요.

**작성자**: 맞습니다. feed cache는 "어떤 사용자가 어떤 게시글들을 볼 것인가"를 빠르게 찾는 인덱스 역할을 합니다.

---

## 7. News Feed Cache의 `user_id` 의미 논의

**작성자**: 책에서 News Feed Cache에 `post_id`와 `user_id`가 같이 나온 부분이 헷갈릴 수 있습니다. 여기서 `user_id`가 게시글 작성자인지, 피드를 읽을 사용자인지 명확히 해야 합니다.

**참석자**: 처음에는 작성자 ID라고 생각했습니다. 게시글을 보여줄 때 작성자 프로필이 필요하니까요.

**작성자**: 그런데 fanout 흐름을 생각해보면, Fanout Service가 follower 목록을 가져와서 각 follower의 피드에 게시글을 넣습니다. 그렇다면 feed cache의 key가 되는 `user_id`는 피드를 읽을 사람, 즉 **수신자 user_id**입니다.

**참석자**: 예를 들어 작성자 A가 `post_1`을 올렸고, follower가 B, C, D라면 캐시에는 다음처럼 들어갑니다.

```text
feed:B -> [post_1, ...]
feed:C -> [post_1, ...]
feed:D -> [post_1, ...]
```

**작성자**: 맞습니다. 이 구조라면 B가 피드를 열 때 `feed:B`만 조회하면 자신이 볼 post 목록을 바로 얻을 수 있습니다.

**참석자**: 그러면 작성자 ID는 어디에서 가져오나요?

**작성자**: Post Cache/Post DB에서 `post_id`로 게시글을 조회하면 `author_id`가 나옵니다. 그 `author_id`를 가지고 User Cache에서 작성자 이름, 프로필 사진 같은 정보를 가져옵니다.

**참석자**: 그러면 정리하면:

- News Feed Cache의 key `user_id` = 피드를 읽는 사용자 ID
- News Feed Cache의 value = 해당 사용자가 볼 `post_id` 목록
- 작성자 정보 = `post_id -> post.author_id -> User Cache` 순서로 hydrate

**작성자**: 맞습니다. 회의 중 가장 헷갈렸던 부분인데, 이 정리가 맞습니다.

---

## 8. API 설계

**작성자**: API는 크게 두 개면 됩니다.

```http
POST /v1/me/feed
GET  /v1/me/feed?cursor=&limit=
```

**참석자**: 게시글 발행은 POST, 피드 조회는 GET으로 두면 됩니다. cursor 기반 pagination이 필요합니다.

**작성자**: 게시글 상세 조회가 필요하면 `GET /v1/posts/{postId}`도 둘 수 있습니다. 하지만 핵심은 발행과 피드 조회입니다.

---

## 9. Cache Layer / Cache Namespace 논의

**작성자**: 책 마지막에 캐시 구조 설계가 나옵니다. News Feed, Content, Social Graph, Action, Counter 같은 영역으로 나뉘는 부분입니다.

**참석자**: 이걸 L1/L2 캐시처럼 계층적인 레이어로 봐야 하는지, 아니면 네임스페이스 분리로 봐야 하는지 헷갈립니다.

**작성자**: 일단 지금 보기에는 L1/L2 같은 물리적 계층이라기보다, 데이터 특성별 캐시 네임스페이스 분리에 가깝습니다.

**참석자**: 데이터 변경 주기, 조회 패턴, 크기, invalidation 방식이 다르기 때문에 나누는 것으로 보입니다.

**작성자**: 예를 들면:

- News Feed Cache: 사용자별 feed item/post_id list
- Content/Post Cache: post body, media reference, author_id
- Social Graph Cache: follower/followee 관계
- Action Cache: 좋아요, 답글, 사용자의 action state
- Counter Cache: like count, comment count, view count

**참석자**: 이 부분은 정확한 기준을 더 조사해서 다음에 공유하면 좋겠습니다.

**작성자**: 좋습니다. "캐시 레이어"라는 표현 때문에 L1/L2 캐시처럼 오해하지 말고, 우선은 데이터 도메인별 캐시 분리로 이해하겠습니다. 다만 세부 기준은 follow-up으로 남기겠습니다.

---

## 10. 마무리

**작성자**: 오늘은 요구사항, 규모 산정, 게시글 발행 흐름, 피드 조회 흐름, fanout 전략, feed cache 구조까지 정리했습니다. 가장 중요한 결론은 일반 사용자는 fanout on write, 유명인 계정은 fanout on read를 섞는 hybrid fanout이고, News Feed Cache는 수신자 user_id 기준으로 post_id 목록을 저장한다는 점입니다.

**참석자**: 캐시 구조 설계는 다음 시간 전까지 각자 더 찾아보고 공유하면 좋겠습니다.

**작성자**: 좋습니다. 모두 수고하셨습니다.
