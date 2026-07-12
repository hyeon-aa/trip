---
name: commit
description: 이 프로젝트의 커밋 컨벤션(docs/CONVENTIONS.md, `type: 설명(front/back)`)에 맞춰 diff를 분석하고 커밋 메시지를 초안으로 만들어 커밋합니다. "커밋해줘", "커밋 메시지 만들어줘" 같은 요청에 사용합니다.
---

# /commit — 컨벤션에 맞는 커밋

`docs/CONVENTIONS.md`에 정리된 형식을 따른다:
```
<type>: <설명>(<scope>)
```

## 1. 현재 상태 파악

```bash
git status --porcelain
git diff
git diff --staged
```
스테이징된 게 없으면 무엇을 커밋할지(전체 vs 일부) 사용자에게 확인한다.

## 2. type 결정

diff 내용을 보고 판단:
- `feat` — 새 기능/코드 추가
- `fix` — 버그 수정
- `chore` — 설정, 의존성, 잡일성 변경
- `refactor` — 동작 변화 없는 구조 개선

## 3. scope 결정

변경된 파일이:
- `client/` 아래만 → `(front)`
- `server/` 아래만 → `(back)`
- 둘 다 걸치거나 루트 설정/문서면 → scope 생략

## 4. 설명 작성

한글로, diff의 "무엇을 왜 바꿨는지" 핵심만 간결하게. 기존 커밋 예시
(`git log --oneline -10`)와 톤을 맞춘다.

## 5. 초안 확인 + 커밋

작성한 메시지를 사용자에게 보여주고 확정되면 스테이징 후 커밋한다:
```bash
git add <files>
git commit -m "$(cat <<'EOF'
<type>: <설명>(<scope>)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
사용자가 별도 커밋 메시지를 이미 준 경우 그대로 쓰고, 컨벤션에서 벗어나면
한 번 확인만 하고 사용자가 원하는 대로 진행한다.
