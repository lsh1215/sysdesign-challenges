# System Design Document Templates

이 디렉토리는 시스템 디자인 문서를 작성할 때 골격으로 쓰는 두 종류의 템플릿을 포함한다.

## 두 스타일 비교

| 항목 | Mock Interview Style | Software Design Document Style |
|---|---|---|
| **목적** | 사고 흐름의 시뮬레이션·기록 | 팀 자산으로서의 결정 근거·추적 |
| **분량** | 2–4 페이지 | 8–20 페이지 |
| **시간** | 45–60분 작성 | 며칠~수 주 협업 |
| **버전 관리** | 없음 | Revision History 필수 |
| **결정 기록** | 구두 설명 수준 | ADR 형식 독립 섹션 |
| **요구사항 추적** | 1:1 매핑 없음 | Traceability Matrix |
| **Risk / Rollout** | 가벼운 언급 | 독립 섹션 |
| **격식** | 화이트보드 톤 | 공식 문서 톤 |
| **출처 베이스** | ByteByteGo, Hello Interview, Donne Martin | IEEE 1016, Atlassian, CMS, Notion, Google |

## 어떤 걸 언제 쓰나

**Mock Interview Style — `mock-interview-style.md`**
- 인터뷰 연습 / 빠른 설계 스케치
- 새 주제를 처음 탐색할 때
- 구현 전 1시간 안에 골격 잡고 싶을 때

**SDD Style — `software-design-document-style.md`**
- 같은 주제를 SDD 스타일로 한 번 더 정리할 때
- 실무 SDD 작성 연습
- 팀 리뷰·운영 인계까지 가정한 정통 문서가 필요할 때

## 사용법

```bash
# 새 주제 시작 시 둘 중 원하는 스타일을 복사
cp templates/System-Design-Document/mock-interview-style.md \
   <topic>/System-Design-Document/mock-interview.md

# 또는 둘 다 (같은 주제를 두 스타일로 비교 작성)
cp templates/System-Design-Document/software-design-document-style.md \
   <topic>/System-Design-Document/sdd.md
```

복사 후 중괄호(`{...}`)와 `> _Note:_ ...` 안내문을 자기 답으로 대체.
