<!-- OMC:START -->
<!-- OMC:VERSION:4.13.5 -->

# oh-my-claudecode - Intelligent Multi-Agent Orchestration

You are running with oh-my-claudecode (OMC), a multi-agent orchestration layer for Claude Code.
Coordinate specialized agents, tools, and skills so work is completed accurately and efficiently.

<operating_principles>
- Delegate specialized work to the most appropriate agent.
- Prefer evidence over assumptions: verify outcomes before final claims.
- Choose the lightest-weight path that preserves quality.
- Consult official docs before implementing with SDKs/frameworks/APIs.
</operating_principles>

<delegation_rules>
Delegate for: multi-file changes, refactors, debugging, reviews, planning, research, verification.
Work directly for: trivial ops, small clarifications, single commands.
Route code to `executor` (use `model=opus` for complex work). Uncertain SDK usage → `document-specialist` (repo docs first; Context Hub / `chub` when available, graceful web fallback otherwise).
</delegation_rules>

<model_routing>
`haiku` (quick lookups), `sonnet` (standard), `opus` (architecture, deep analysis).
Direct writes OK for: `~/.claude/**`, `.omc/**`, `.claude/**`, `CLAUDE.md`, `AGENTS.md`.
</model_routing>

<skills>
Invoke via `/oh-my-claudecode:<name>`. Trigger patterns auto-detect keywords.
Tier-0 workflows include `autopilot`, `ultrawork`, `ralph`, `team`, and `ralplan`.
Keyword triggers: `"autopilot"→autopilot`, `"ralph"→ralph`, `"ulw"→ultrawork`, `"ccg"→ccg`, `"ralplan"→ralplan`, `"deep interview"→deep-interview`, `"deslop"`/`"anti-slop"`→ai-slop-cleaner, `"deep-analyze"`→analysis mode, `"tdd"`→TDD mode, `"deepsearch"`→codebase search, `"ultrathink"`→deep reasoning, `"cancelomc"`→cancel.
Team orchestration is explicit via `/team`.
Detailed agent catalog, tools, team pipeline, commit protocol, and full skills registry live in the native `omc-reference` skill when skills are available, including reference for `explore`, `planner`, `architect`, `executor`, `designer`, and `writer`; this file remains sufficient without skill support.
</skills>

<verification>
Verify before claiming completion. Size appropriately: small→haiku, standard→sonnet, large/security→opus.
If verification fails, keep iterating.
</verification>

<execution_protocols>
Broad requests: explore first, then plan. 2+ independent tasks in parallel. `run_in_background` for builds/tests.
Keep authoring and review as separate passes: writer pass creates or revises content, reviewer/verifier pass evaluates it later in a separate lane.
Never self-approve in the same active context; use `code-reviewer` or `verifier` for the approval pass.
Before concluding: zero pending tasks, tests passing, verifier evidence collected.
</execution_protocols>

<hooks_and_context>
Hooks inject `<system-reminder>` tags. Key patterns: `hook success: Success` (proceed), `[MAGIC KEYWORD: ...]` (invoke skill), `The boulder never stops` (ralph/ultrawork active).
Persistence: `<remember>` (7 days), `<remember priority>` (permanent).
Kill switches: `DISABLE_OMC`, `OMC_SKIP_HOOKS` (comma-separated).
</hooks_and_context>

<cancellation>
`/oh-my-claudecode:cancel` ends execution modes. Cancel when done+verified or blocked. Don't cancel if work incomplete.
</cancellation>

<worktree_paths>
State: `.omc/state/`, `.omc/state/sessions/{sessionId}/`, `.omc/notepad.md`, `.omc/project-memory.json`, `.omc/plans/`, `.omc/research/`, `.omc/logs/`
</worktree_paths>

## Setup

Say "setup omc" or run `/oh-my-claudecode:omc-setup`.

<!-- OMC:END -->

---

# Project: System Design Challenges

## 프로젝트 개요

시스템 설계 → AI 기반 빠른 구현 → 테스트 검증 사이클을 반복하는 연습 레포.

- **설계는 사람이**: `<topic>/System-Design-Document/` — 두 스타일(Mock Interview / SDD) 템플릿이 `templates/System-Design-Document/`에 있음
- **구현은 AI가**: `<topic>/source/` — 마이크로서비스 코드
- **검증은 데이터로**: `<topic>/test-results/` — 부하·기능·장애 테스트 결과

## 디렉토리 구조

```
sysdesign-challenges/
├── README.md
├── .gitignore
├── templates/
│   └── System-Design-Document/        # SDD 템플릿 (mock-interview / sdd 스타일)
├── .claude/
│   ├── CLAUDE.md                       # 이 파일
│   ├── settings.json                   # skill-injector hook 등록
│   ├── hooks/skill-injector.mjs        # 키워드 기반 스킬 자동 주입
│   └── skills/                         # 7개 도메인 특화 스킬
└── <topic>/                            # 시스템 디자인 주제 단위
    ├── System-Design-Document/
    ├── source/
    └── test-results/
```

## 디폴트 Stack

이 레포의 모든 시스템 디자인 주제는 별도 명시가 없는 한 **Spring Boot 3.x + Java 21**로 구현한다.

### 라우팅 규칙

사용자가 "이 설계대로 구현해줘", "코드 짜줘", "URL shortener 만들어줘" 같이 **stack을 명시하지 않은 구현 요청**을 하면:

1. **`spring-boot-expert` sub-agent로 위임** (`Task(subagent_type="spring-boot-expert", ...)`)
2. **`<topic>/source/`가 비어 있거나 `build.gradle`이 없으면** — agent가 `spring-bootstrap` skill을 먼저 발동해 `.claude/skills/spring-bootstrap/templates/`를 복사·치환하여 골격을 깐다
3. **그 위에서** 도메인 모델 → 서비스 → 컨트롤러 순으로 TDD 구현 (`new-feature` 슬래시 또는 `tdd-patterns` skill 따라)

### 다른 stack 사용 시

사용자가 "이번엔 **Python** / **Node** / **Go** 로 구현해줘"처럼 **명시적으로 다른 stack을 지정**한 경우에만 Spring 경로를 벗어난다. 이 경우:

- `spring-boot-expert` agent에 위임하지 않는다
- `spring-bootstrap` skill을 발동하지 않는다
- 해당 stack의 표준 부트스트랩(예: `pyproject.toml`, `package.json`)을 사용자에게 합의받고 진행
- 단, **DDD layered architecture**, **DTO 분리**, **TDD**, **GlobalExceptionHandler 같은 중앙집중식 예외 처리** 등 개념은 stack에 맞춰 매핑해서 적용 (`domain-modeling`, `tdd-patterns` skill의 개념 부분만 차용)

## 코딩 컨벤션 (보편 규칙)

이 컨벤션들은 stack 무관하게 적용된다.

- **per-topic isolation**: 각 주제의 source / test-results는 다른 주제와 격리. 공유 코드 금지.
- **설계 우선**: 코드 작성 전 SDD 갱신. 설계 변경 시 SDD부터 수정 후 코드.
- **TDD**: RED → GREEN → REFACTOR 순서. 테스트 없는 구현 금지. (자세한 규칙은 `tdd-patterns` 스킬)
- **DDD layered**: api → application → domain → infra. Domain layer는 infrastructure 의존 0. (자세한 규칙은 `domain-modeling`, `layer-architecture` 스킬)
- **다이어그램**: 가능하면 텍스트 기반(Mermaid / ASCII) — diff 가능해야 함
- **커밋 메시지**: `<topic>: <change>` (예: `url-shortener: add capacity estimation`). 일반 변경은 conventional commits (`docs:`, `feat:`, `fix:`, `refactor:`, `test:`, `chore:`)
- **AI 생성 티 금지**: TODO 주석, 워터마크, 과잉 docstring, 보일러플레이트 설명 주석 금지
- **PR 없이 main 직접 푸시**: 개인 연습 레포라 허용. 단, 큰 구조 변경은 별도 브랜치 권장.

## 파일 접근 규칙

- `.env` 파일 절대 읽지 말 것 (글로벌 규칙)
- `.env.example` / `.env.sample`만 참조 가능

## Custom Skills (Auto-Triggered via Hook)

`.claude/skills/*/SKILL.md`의 `triggers:` frontmatter에 정의된 키워드를 사용자 프롬프트에서 발견하면 hook이 해당 스킬 본문을 컨텍스트에 자동 주입한다 (프롬프트당 최대 5개).

| Skill | Triggers (subset) | Purpose |
|-------|-------------------|---------|
| **domain-modeling** | `jpa entity`, `aggregate root`, `value object`, `bounded context`, `domain event`, `도메인 모델` | Eric Evans DDD — Entity / VO / Aggregate / Bounded Context / ACL / Specification |
| **layer-architecture** | `@restcontroller`, `controller layer`, `service layer`, `rest api`, `@transactional`, `dto pattern`, `레이어 아키텍처` | Application/Domain/Infra 서비스 구분, 트랜잭션 경계, REST/DTO/에러 핸들링 규약 |
| **tdd-patterns** | `tdd`, `test first`, `red green refactor`, `@test`, `junit5`, `mockito mock`, `testcontainers`, `단위 테스트`, `통합 테스트` | TDD 워크플로, 레이어별 테스트 종류·네이밍·픽스처 |
| **e2e-testing** | `e2e test`, `e2e 테스트`, `browser test`, `qa test`, `agent-browser`, `end to end` | agent-browser CLI 기반 토큰 효율적 E2E 테스트 |
| **event-driven** | `kafka producer`, `kafka consumer`, `kafka topic`, `outbox pattern`, `idempotent consumer`, `카프카 설정`, `이벤트 발행` | Kafka 토픽 네이밍, producer/consumer 설정, Outbox, idempotency |
| **saga-pattern** | `saga pattern`, `saga orchestration`, `compensation transaction`, `distributed transaction`, `보상 트랜잭션`, `사가 패턴` | SAGA 오케스트레이션, 상태 머신, 보상 트랜잭션 |
| **spring-exception-handling** | `try catch`, `exception handler`, `@restcontrolleradvice`, `businessexception`, `throw new`, `예외 처리`, `익셉션 핸들러` | try-catch 남용 방지 + GlobalExceptionHandler 강제. spring-boot-expert agent 부재 시 안전망 |
| **spring-bootstrap** | `spring 프로젝트 세팅`, `spring 부트스트랩`, `bootstrap spring`, `scaffold spring`, `common 모듈`, `shared kernel`, `설계 기반 구현` | 새 Spring Boot 멀티모듈 프로젝트의 기초 골격(`common/` shared kernel + Gradle/Docker). 거의 항상 필요한 최소 셋만 — exception 처리·DTO·BaseEntity·CORS·JPA Auditing·OpenAPI. Kafka/Outbox/QueryDSL 등 도메인 특화 패턴은 별도 llm-wiki에서 가져옴 |
| **new-feature** | (slash command, `disable-model-invocation: true`) | 도메인 피처 스캐폴딩 — TDD 순서로 entity → test → service → test → controller → test |
| **sysdesign-design** | `시스템 만들어보고 싶`, `시스템 설계하자`, `채팅 시스템 만들`, `알림 시스템 만들`, `news feed 설계`, `url shortener 설계` 등 12개 토픽 별칭 | **Phase 1–3** (Clarifying → High Level → Drill Down). 토픽 디렉토리 생성 + mock-interview.md 템플릿 복사 + question-bank 매칭 토픽 로드 + `.omc/state/active-sysdesign-topic.txt` 기록 + 책 canned 숫자로 시작 → 사용자와 티키타카하며 mock-interview.md **즉시 갱신** |
| **sysdesign-sdd** | `SDD 작성`, `sdd로 넘어가`, `정식 설계 문서`, `software design document`, `설계 문서 정리` | **Phase 5**. 활성 토픽의 mock-interview.md + `<topic>/conversation-log/*.log` 통째로 읽고 → sdd.md 작성. ADR / Constraints / Risk / Rollout 등 SDD 전용 항목은 한 섹션씩 사용자에게 질문 |
| **sysdesign-impl** | `구현하자`, `최소 구현`, `MVI 만들`, `implementation 시작`, `소스 스캐폴드` | **Phase 6**. SDD 읽고 minimum-viable subset 결정 (CDN/multi-region/replica는 자동 제외) + 사용자에게 stack 지정 받기 (default Spring Boot+Postgres+Redis+Kafka) → `<topic>/source/` 스캐폴딩 + `docker-compose.yml` + NFR별 test stub |
| **sysdesign-frameworks** | `back-of-envelope`, `개략적 규모`, `캐시 도입`, `제프 딘`, `latency`, `샤딩`, `fan-out`, `db 선택`, `cap 정리`, `quorum`, `snowflake`, `consistent hashing`, `토큰 버킷`, `delivery framework` 등 30+ | sysdesign-design / sdd / impl 진행 중 자동 주입되는 reference. capacity 공식 / Jeff Dean·Colin Scott latency / building blocks / CAP / 일관성 / rate limiting / Snowflake / consistent hashing / fan-out / Hello Interview delivery framework. 12개 per-framework 파일 |
| **sysdesign-question-bank** | (no triggers, `disable-model-invocation: true`) | sysdesign-design이 명시적으로 매칭된 토픽 1개 파일만 로드. 12개 문제별 책 canned 숫자/결정/함정/key-design-dimensions. Vol.2 문제(proximity/maps/등)는 `_vol2-gap.md`로 별도 표시 |

## Project Sub-Agents

`.claude/agents/<name>.md`에 정의된 sub-agent. `Task` 호출 시 `subagent_type` 파라미터로 라우팅.

| Agent | Model | When to Route |
|-------|-------|---------------|
| **spring-boot-expert** | sonnet | Spring Boot / Java 코드 작성·수정 작업 일체. Controller, Service, JPA Entity, Repository, DTO, 예외 처리, 트랜잭션, Spring Security, Spring Cloud, Kafka 통합, 테스트. 프로젝트의 GlobalExceptionHandler 패턴을 강제하고 try-catch 남용 같은 AI 안티패턴을 차단한다. |

### Agent 라우팅 규칙

- Spring Boot 코드 작업 → `spring-boot-expert`로 위임
- 단순 lookup, trivial fix는 메인 스레드에서 직접 처리 (이때도 `spring-exception-handling` skill이 hook으로 자동 주입되어 안전망 역할)

### 사용 방식 두 가지

1. **자동 주입 (hook)**: 프롬프트에 트리거 키워드를 자연스럽게 포함시키면 hook이 매칭된 스킬을 컨텍스트로 끌어옴. 의도적으로 호출 안 해도 됨.
   - 예: "주문 도메인 aggregate root 설계해줘" → `domain-modeling` 자동 주입
2. **명시적 호출 (slash)**: `new-feature`처럼 `disable-model-invocation: true`인 스킬은 hook이 자동 호출하지 않음. 직접 부르려면 컨텍스트에서 명시.

### 주의: Stack 의존성

`layer-architecture`, `tdd-patterns`, `new-feature` 일부는 **Spring Boot / Java / Gradle**을 전제로 작성되어 있다 (어노테이션 표기, 패키지 구조, JPA/QueryDSL/Testcontainers).

- 같은 stack을 쓰는 주제: 그대로 적용
- 다른 stack을 쓰는 주제 (Node, Go, Python 등): **개념(레이어 분리, 트랜잭션 경계, 테스트 슬라이스, DTO 분리)만 차용**하고 어노테이션·툴은 stack에 맞게 매핑

`domain-modeling`, `event-driven`(Kafka), `saga-pattern`, `e2e-testing`은 stack 비의존적으로 적용 가능.

## Skill 추가/수정 워크플로

새 스킬을 추가하려면:

1. `.claude/skills/<skill-name>/SKILL.md` 생성
2. YAML frontmatter에 `name`, `description`, `triggers:` 명시 (`triggers:`가 없으면 hook이 무시함)
3. 본문은 짧고 표 중심으로 — 주입 시 컨텍스트 비용을 최소화
4. 이 CLAUDE.md의 Skills 표에 한 줄 추가
