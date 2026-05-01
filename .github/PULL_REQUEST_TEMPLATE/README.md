# PR Template Guide

이 디렉토리는 PR 본문 양식을 담고 있다. 라우팅 규칙(언제 무엇을 쓰는가)은 `CLAUDE.md §9`에 있고, 이 문서는 사람이 읽는 사용법이다.

## 어떤 템플릿을 쓸 것인가

| 상황 | 템플릿 | URL parameter |
|---|---|---|
| **Plan 구현** (주 흐름) | `../pull_request_template.md` | (기본 — 파라미터 불필요) |
| ADR / Plan 문서만 변경, 코드 동작 변경 없음 | `docs-plan-adr.md` | `?template=docs-plan-adr.md` |
| Plan과 무관한 버그 수정 | `fix.md` | `?template=fix.md` |
| 도구·의존성·포맷팅·저장소 정리 | `chore.md` | `?template=chore.md` |

GitHub는 PR 생성 URL이 `https://github.com/<owner>/<repo>/compare/<base>...<head>`일 때 `?template=<filename>` 파라미터로 본문 템플릿을 선택한다. 파라미터 없으면 루트의 `pull_request_template.md`(=Plan 구현)를 기본으로 쓴다.

예:

```
https://github.com/dunowljj/board-service/compare/main...fix/trace-id-decode-guard?template=fix.md
```

## 경계 사례 — 어느 템플릿이 맞는지 헷갈릴 때

- **문서 + 코드를 동시에 변경?** → 기본 템플릿 (Plan 구현). docs-plan-adr.md는 *코드 동작 변경 없음*에만 쓴다.
- **버그 수정인데 Plan을 만들어야 할 만큼 큰가?** → 기본 템플릿 + 새 Plan. `fix.md`는 작은 단발 수정용이다.
- **의존성 bump가 동작 변경을 동반?** (예: minor 버전이 default를 바꿈) → 기본 템플릿. `chore.md`는 *동작 무변경*에만 쓴다.

규칙: **의심되면 기본 템플릿**. 정보가 더 들어가는 쪽이 안전하다.

## Squash Merge 정책

이 저장소는 squash merge가 기본이다. 각 템플릿 끝에 있는 `## Squash Commit Message` 섹션이 squash 커밋 본문의 *초안*이다. 머지 직전 그대로 복사해서 squash dialog에 붙여넣을 수 있도록 자기충족적으로 작성한다.

```text
feat(scope): one-line summary (PLAN-NNNN[-X])

- bullet
- bullet
```

머지 후 `git log --oneline` 한 줄로도 변경 의도가 드러나야 한다 — PR description 링크 없이 읽힐 수 있어야 한다.

## 템플릿 자체를 수정할 때

템플릿 파일은 `.github/` 디렉토리 컨벤션에 따라 GitHub가 직접 읽는다. 수정 시:

- 기본(Plan 구현) 템플릿은 이 저장소의 주 흐름이므로 보수적으로 변경한다.
- 새 상황별 템플릿을 추가하려면 `CLAUDE.md §9`의 type 목록과 본 README의 표를 함께 갱신한다 — 둘이 drift하면 사람도 에이전트도 헷갈린다.
- 라우팅 규칙(언제 어떤 템플릿을 쓰는가) 자체가 바뀌면 `CLAUDE.md §9`도 같이 수정한다.
