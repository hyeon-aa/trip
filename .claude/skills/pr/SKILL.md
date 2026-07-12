---
name: pr
description: 현재 브랜치를 main으로 향하는 PR로 올리고, 생성 직후 /code-review를 자동으로 돌려 리뷰 코멘트까지 남깁니다. CodeRabbit을 더 이상 쓰지 않는 대신 Claude가 그 역할(PR 자동 리뷰)을 대체합니다. "PR 올려줘", "PR 만들어줘" 같은 요청에 사용합니다.
---

# /pr — PR 생성 + 자동 리뷰

기존 PR들(`gh pr list --state all`)의 실제 패턴:
- 제목: `Feat: <한글 설명>` / `Fix: <한글 설명>` (대문자 시작, 콜론 뒤 공백)
- base: 항상 `main`

CodeRabbit(`.coderabbit.yaml`)은 제거했다 — 더 이상 PR 본문에 자동으로 요약이
붙지 않으므로, 이제 본문을 직접 제대로 작성하고 리뷰도 이 스킬이 대신 돌린다.

## 1. 사전 확인

```bash
gh --version >/dev/null 2>&1 || echo NO_GH
gh auth status >/dev/null 2>&1 || echo NO_AUTH
git status --porcelain
```
커밋 안 된 변경이 있으면 먼저 `/commit`으로 정리할지 사용자에게 확인.

## 2. 브랜치 상태 파악

```bash
git branch --show-current
git log main..HEAD --oneline
git diff main...HEAD --stat
```
`main..HEAD`에 커밋이 없으면 올릴 게 없다고 안내하고 중단.

## 3. 원격 반영

```bash
git push -u origin <current-branch>
```
이미 push 상태면 스킵. force push는 하지 않는다.

## 4. 제목/본문 초안

- 제목: 이 브랜치의 커밋들을 종합해 `Feat: 설명` 형식 하나로 (여러 타입이 섞였으면
  가장 비중 큰 것 기준, 필요하면 `Fix:`도 가능).
- 본문: `.github/PULL_REQUEST_TEMPLATE.md` 구조(`## Summary` / `## Test plan`)를
  따른다. Summary는 커밋들을 종합한 핵심 변경 bullet 2~4개, Test plan은 어떻게
  검증했는지/해야 하는지 체크리스트.

## 5. 확인 후 생성

초안을 사용자에게 보여주고 확정되면:
```bash
gh pr create --base main --title "<제목>" --body "$(cat <<'EOF'
<본문>
EOF
)"
```

## 6. 생성 직후 자동 리뷰

PR 생성 성공하면 바로 이어서 `/code-review`를 실행해 방금 만든 PR의 diff를
리뷰하고, 발견한 이슈를 PR에 인라인 코멘트로 남긴다 (`--comment` 옵션 사용).
리뷰까지 끝난 뒤 PR URL과 리뷰 결과 요약을 사용자에게 전달한다.
