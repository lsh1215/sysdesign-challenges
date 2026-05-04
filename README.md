# sysdesign-challenges

![Status](https://img.shields.io/badge/status-active-success?style=flat-square)
![Architecture](https://img.shields.io/badge/architecture-microservices-blue?style=flat-square)
![Docs](https://img.shields.io/badge/design_doc-google_style-4285F4?style=flat-square&logo=google&logoColor=white)

> 시스템 설계 → AI 기반 빠른 구현 → 테스트 검증 사이클을 반복하는 시스템 디자인 연습 레포지토리.

## Table of Contents

- [Overview](#overview)
- [How It Works](#how-it-works)
- [Repository Structure](#repository-structure)
- [Components](#components)
  - [1. System Design Document](#1-system-design-document)
  - [2. Source Code](#2-source-code)
  - [3. Test Documentation](#3-test-documentation)
- [Workflow](#workflow)
- [Getting Started](#getting-started)
- [Conventions](#conventions)

## Overview

이 레포는 시스템 디자인을 **이론으로 끝내지 않기 위한** 작업 공간입니다.

- **설계는 사람이**: 가상의 서비스를 가정하고, Google 스타일 디자인 문서로 직접 설계합니다.
- **구현은 AI가**: 설계 문서를 입력으로 마이크로서비스 코드를 빠르게 빌드합니다.
- **검증은 데이터로**: 부하·기능·장애 시나리오를 돌리고 결과를 문서화합니다.

목적은 "동작하는 시스템"보다 **설계 결정이 실제로 어떻게 작동하는지 확인**하는 것 — 가정과 현실 사이의 간극을 좁히는 데 있습니다.

## How It Works

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Design    │ ──▶ │  Implement  │ ──▶ │    Verify   │
│  (human)    │     │    (AI)     │     │  (results)  │
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │                    │
       ▼                   ▼                    ▼
  SDD .md            source/                test-results/
```

## Repository Structure

```
sysdesign-challenges/
├── README.md
├── .gitignore
├── templates/
│   └── System-Design-Document/       # SDD 작성용 템플릿 모음
│       ├── mock-interview-style.md   # 모의 인터뷰 스타일
│       └── software-design-document-style.md  # 정통 SDD 스타일
└── <topic>/                          # 시스템 디자인 주제 단위 디렉토리
    ├── System-Design-Document/       # 설계 문서 (templates에서 복사)
    │   ├── design-doc.md
    │   └── diagrams/
    ├── source/                       # 마이크로서비스 구현체
    │   ├── service-a/
    │   ├── service-b/
    │   └── docker-compose.yml
    └── test-results/                 # 테스트 결과 및 분석
        ├── load-test.md
        ├── functional-test.md
        └── reports/
```

`<topic>` 예시: `url-shortener`, `chat-system`, `ride-sharing`, `news-feed`.

## Components

### 1. System Design Document

두 가지 스타일의 템플릿을 `templates/System-Design-Document/`에 둔다. 같은 주제를 두 스타일로 작성해 비교하는 것도 권장된다.

| Style | When to Use | Template |
|---|---|---|
| **Mock Interview** | 빠른 스케치, 인터뷰 연습 | `mock-interview-style.md` |
| **Software Design Document** | 정통 SDD, 팀 리뷰·운영 인계 | `software-design-document-style.md` |

세부 비교와 사용법은 [`templates/System-Design-Document/README.md`](templates/System-Design-Document/README.md) 참고.

### 2. Source Code

설계 문서에 명세된 컴포넌트를 마이크로서비스로 구현합니다.

- 서비스 간 경계는 SDD의 컴포넌트 다이어그램을 따릅니다.
- 각 서비스는 독립적으로 빌드·실행 가능해야 합니다.
- `docker-compose`로 로컬에서 전체 스택을 띄울 수 있도록 합니다.

### 3. Test Documentation

구현체가 설계의 가정을 만족하는지 검증한 결과를 문서로 남깁니다.

- **Functional Test**: API 명세 대비 동작 확인
- **Load Test**: SLO(예: p99 latency, throughput) 충족 여부
- **Failure Test**: 장애 주입 시 격리·복구 동작
- **Findings**: 설계 가정과 실측 결과가 어긋난 지점 — 이게 가장 가치 있는 산출물

## Workflow

1. **주제 선정** — `<topic>/` 디렉토리 생성
2. **설계** — `System-Design-Document/design-doc.md` 작성
3. **구현** — 설계 문서를 컨텍스트로 AI가 `source/` 하위 마이크로서비스 빌드
4. **검증** — 테스트 시나리오 실행 후 `test-results/`에 결과 정리
5. **회고** — SDD에 "Findings vs Assumptions" 섹션 업데이트

## Sysdesign Workflow (AI 디자인 파트너)

위 Workflow를 자동화한 5개 스킬이 `.claude/skills/sysdesign-*` 에 있다. **슬래시 커맨드 없음** — 자연어로 트리거.

### 시나리오 한 사이클

```
[Phase 1–3] "채팅 시스템 만들어보고 싶어"
  → sysdesign-design 트리거
  → chat-system/ 디렉토리 + mock-interview.md 템플릿 복사
  → .omc/state/active-sysdesign-topic.txt 에 chat-system 기록
  → 책 canned 숫자(50M DAU 등) 던지고 사용자가 조정
  → 티키타카하며 mock-interview.md 즉시 갱신
  → "DAU 100M이면 저장 얼마?" → sysdesign-frameworks 자동 주입 (capacity 공식)
  → "캐시 도입하면 얼마나 빨라?" → frameworks 자동 주입 (Jeff Dean latency)

[Phase 4: 사용자 리뷰]
  chat-system/System-Design-Document/mock-interview.md 직접 보고 수정/추가

[Phase 5] "이제 SDD로 넘어가자"
  → sysdesign-sdd 트리거
  → mock-interview.md + chat-system/conversation-log/*.log 통째로 읽음
  → sdd.md 자동 bulk-fill
  → SDD 전용 항목(Constraints/ADR/Risk/Rollout) 한 섹션씩 질문

[Phase 6] "이제 구현하자"
  → sysdesign-impl 트리거
  → SDD 읽고 minimum-viable subset 결정 (CDN/multi-region 자동 제외)
  → stack 확인 (default Spring Boot+Postgres+Redis+Kafka)
  → chat-system/source/ + docker-compose.yml + NFR test stub 스캐폴딩
```

### 트리거 사전

| Phase | 자연어 트리거 (예시) |
|---|---|
| **1–3 (sysdesign-design)** | `채팅 시스템 만들어보고 싶어` / `알림 시스템 설계하자` / `news feed 만들` / `url shortener 설계` / `시스템 만들어보고 싶` / `시스템 디자인 시작` |
| **5 (sysdesign-sdd)** | `SDD 작성` / `sdd로 넘어가` / `정식 설계 문서 만들자` / `software design document` |
| **6 (sysdesign-impl)** | `구현하자` / `최소 구현` / `MVI 만들` / `소스 스캐폴드` / `프로토타입 만들` |
| **frameworks** (자동) | `back-of-envelope` / `개략적 규모` / `캐시 도입` / `제프 딘` / `latency` / `샤딩` / `fan-out` / `db 선택` / `cap 정리` / `quorum` / `snowflake` / `consistent hashing` / `토큰 버킷` (총 30+) |

### 토픽별 산출물 위치

```
<topic>/
├── System-Design-Document/
│   ├── mock-interview.md        ← Phase 1–3 결과 (즉시 갱신)
│   ├── sdd.md                   ← Phase 5 결과
│   └── diagrams/
├── source/                      ← Phase 6 결과 (MVI 코드 + docker-compose.yml)
├── test-results/                ← 사용자가 실행한 테스트 결과
└── conversation-log/            ← AI와의 대화 자동 기록 (gitignored)
    └── YYYY-MM-DD.log
```

### 지원되는 12개 토픽 (Alex Xu Vol.1)

`rate-limiter` · `consistent-hashing` · `key-value-store` · `unique-id-generator` · `url-shortener` · `web-crawler` · `notification-system` · `news-feed` · `chat-system` · `search-autocomplete` · `youtube` · `google-drive`

추천 학습 순서: `rate-limiter` → `url-shortener` → `notification-system` → `news-feed` → `search-autocomplete` → `chat-system` → `key-value-store`. 자세한 내용은 `.claude/skills/sysdesign-question-bank/SKILL.md`.

Vol.2 토픽(proximity service / nearby friends / Google Maps / 분산 메시지 큐 / 메트릭 모니터링 / ad click aggregation / hotel reservation)은 책이 라이브러리에 없어서 미지원 — 직접 가정 입력하면 진행 가능.

### 대화 로그 자동 정리 정책

대화 로그는 **gitignore**되며 (개인 정보 보호 + 저장 공간 관리) 다음 정책으로 자동 정리:

| 토픽 상태 | 판정 | 보존 |
|---|---|---|
| 활성 (active) | `.omc/state/active-sysdesign-topic.txt` 와 일치 | 무기한 (현재 작업 중) |
| 완료 (shipped) | `<topic>/source/` 에 파일 있음 (impl 완료) | 마지막 활동 +3일 |
| 비활성 (abandoned) | active도 아니고 source/도 비어있음 | 마지막 활동 +14일 |
| catchall (`.claude/logs/`) | 토픽 미설정 상태의 로그 | 3일 |

수동 보존: `<topic>/conversation-log/2026-05-04.log` → `....log.keep` 으로 리네임 (`.log` 만 cleanup 대상).

cleanup은 SessionStart hook (`.claude/hooks/cleanup-logs.mjs`)이 매 세션 시작 시 1회 실행.

## Getting Started

```bash
git clone <this-repo>
cd sysdesign-challenges

# 자동: 자연어로 시작
# (Claude Code 세션 안에서 그냥 말하면 됨)
# > "채팅 시스템 만들어보고 싶어"
#   → sysdesign-design 자동 트리거 → chat-system/ 자동 생성

# 수동 (스킬 미사용 시):
mkdir -p <topic>/System-Design-Document/diagrams
mkdir -p <topic>/source
mkdir -p <topic>/test-results
cp templates/System-Design-Document/mock-interview-style.md \
   <topic>/System-Design-Document/mock-interview.md
```

## Conventions

- 디렉토리·파일명: `kebab-case`
- 다이어그램: 가능하면 텍스트 기반(Mermaid, ASCII) — diff 가능하도록
- 커밋 메시지: `<topic>: <change>` (예: `url-shortener: add capacity estimation`)
- 설계 변경 시: SDD를 먼저 갱신하고, 그다음 코드 수정
- **로그/대화 파일은 커밋 금지** — `.claude/logs/`, `*/conversation-log/`, `.omc/state/` 모두 gitignored
