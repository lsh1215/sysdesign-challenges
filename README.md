# sysdesign-challenges

![Book](https://img.shields.io/badge/book-System_Design_Interview_(Alex_Xu)-blue?style=flat-square)
![Format](https://img.shields.io/badge/format-study_group-success?style=flat-square)
![Status](https://img.shields.io/badge/status-active-success?style=flat-square)

> **System Design Interview (Alex Xu)** 책을 함께 읽고, 책에 나온 주제로 직접 시스템 설계를 해보는 스터디 레포지토리.

## Table of Contents

- [Overview](#overview)
- [Study Format](#study-format)
  - [Part 1. Chapters 1–7: 책 정리 + 발표](#part-1-chapters-17-책-정리--발표)
  - [Part 2. Chapter 8+ : Mock Interview Style 설계](#part-2-chapter-8--mock-interview-style-설계)
- [Repository Structure](#repository-structure)
  - [토픽 디렉토리 (8장 이후)](#토픽-디렉토리-8장-이후)
- [Conventions](#conventions)

---

## Overview

이 레포는 **알렉스 쉬(Alex Xu)의 *System Design Interview* 시리즈**를 텍스트북으로 삼아, 한 챕터/한 주제씩 함께 공부하는 스터디 공간입니다.

- **혼자 읽고 끝내지 않는다** — 발표·질문·토론을 통해 책 내용을 능동적으로 흡수.
- **읽기만으로 끝내지 않는다** — 후반부에는 책에 나온 주제로 **직접 시스템 설계**를 해보고 문서화.
- **결과물은 글로 남긴다** — 발표 자료 / 모의 인터뷰 기록 / SDD를 한 곳에 모아 다시 볼 수 있게.

---

## Study Format

### Part 1. Chapters 1–7: 책 정리 + 발표

책의 1~7장은 "면접 문제 풀이" 형식이 아닌 **기초 지식 설명** 챕터들입니다. 따라서 이 부분은 다음 방식으로 진행:

1. 각자 챕터를 읽고 핵심을 자기 언어로 정리.
2. 정리한 내용을 스터디원에게 **발표**.
3. 발표 후 책에 다 다루지 못한 **추가 질문·심화 주제**로 토론.

| 챕터 | 디렉토리 |
|---|---|
| 1장. 사용자 수에 따른 규모 확장성 | `01-사용자-수에-따른-규모-확장성/` |
| 2장. 개략적인 규모 추정 | `02-개략적인-규모-추정/` |
| 3장. 시스템 설계 면접 공략법 | `03-시스템-설계-면접-공략법/` |
| 4장. 처리율 제한 장치의 설계 | `04-처리율-제한-장치의-설계/` |
| 5장. 안정 해시 설계 | `05-안정-해시-설계/` |
| 6장. 키-값 저장소 설계 | `06-키-값-저장소-설계/` |
| 7장. 분산 시스템을 위한 유일 ID 생성기 설계 | `07-분산-시스템을-위한-유일-ID-생성기-설계/` |

### Part 2. Chapter 8+ : Mock Interview Style 설계

8장 이후부터는 책이 본격적인 시스템 설계 문제(URL 단축, 채팅, 뉴스피드 등)를 다룹니다. 이 부분은 다음 흐름으로 진행:

1. **Mock Interview 진행** — 스터디원이 면접관/지원자 역할을 번갈아 가며 책의 주제 하나를 가지고 화이트보드 위에서 설계.
2. **기록** — 토론 과정을 `mock-interview-style.md` 에 남김.
3. **Software Design Document 작성** — Mock Interview 결과를 정통 설계 문서 형태로 다시 정리한 `sdd.md` 작성.

> 즉 같은 주제를 **두 번** 통과한다 — 빠른 스케치(Mock Interview) → 정제된 문서(SDD).

---

## Repository Structure

```
sysdesign-challenges/
├── README.md
├── 01-사용자-수에-따른-규모-확장성/
├── 02-개략적인-규모-추정/
├── ... (3~7장)
└── <topic>/                      ← 8장 이후 시스템 설계 주제
    ├── mock-interview-style.md
    ├── sdd.md
    └── study-notes/
        ├── meeting-transcript.md
        └── meeting-summary.md
```

### 토픽 디렉토리 (8장 이후)

```
<topic>/
├── mock-interview-style.md         ← 모의 면접 진행 기록
├── sdd.md                          ← 정제된 Software Design Document
└── study-notes/                    ← 스터디 회의 기록
    ├── meeting-transcript.md       ← 회의록 스크립트 (raw)
    └── meeting-summary.md          ← 회의록 요약본
```

| 파일 | 역할 |
|---|---|
| `mock-interview-style.md` | 면접관·지원자 역할로 진행한 모의 설계 세션의 대화체 기록. 빠른 스케치, 가정·요구사항 명확화 위주. |
| `sdd.md` | Mock Interview에서 도출된 결정들을 정통 설계 문서 형태로 재정리. 구조화된 섹션(Constraints / ADR / Risk / Rollout 등) 포함. |
| `study-notes/meeting-transcript.md` | 스터디 회의 중 오간 대화·토론을 그대로 받아 적은 **원본 스크립트**. |
| `study-notes/meeting-summary.md` | 스크립트에서 핵심 결정·질문·액션 아이템만 추려낸 **요약본**. |

**`<topic>` 예시**: `rate-limiter`, `url-shortener`, `notification-system`, `news-feed`, `chat-system`, `search-autocomplete`, `youtube`, `google-drive` 등 (책 8장 이후 다루는 주제들).

---

## Conventions

- **디렉토리·파일명**: 1~7장은 한글 챕터 제목 그대로, 8장 이후 토픽은 `kebab-case` 영문.
- **다이어그램**: 가능하면 텍스트 기반(Mermaid, ASCII) — diff 가능하도록.
- **이미지**: 책 본문에서 가져온 이미지는 `images/` 하위에 두고 `.gitignore` 처리 (저작권).
- **커밋 메시지**: `<scope>: <change>` 형식.
  - 챕터 정리: `ch01: ...`, `ch02: ...`
  - 토픽 설계: `url-shortener: ...`, `chat-system: ...`
- **설계 변경 시**: Mock Interview 또는 SDD를 먼저 갱신하고 토론.
