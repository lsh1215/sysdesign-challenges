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

## Getting Started

```bash
git clone <this-repo>
cd sysdesign-challenges

# 새로운 주제 시작
mkdir -p <topic>/System-Design-Document/diagrams
mkdir -p <topic>/source
mkdir -p <topic>/test-results
touch <topic>/System-Design-Document/design-doc.md
```

## Conventions

- 디렉토리·파일명: `kebab-case`
- 다이어그램: 가능하면 텍스트 기반(Mermaid, ASCII) — diff 가능하도록
- 커밋 메시지: `<topic>: <change>` (예: `url-shortener: add capacity estimation`)
- 설계 변경 시: SDD를 먼저 갱신하고, 그다음 코드 수정
