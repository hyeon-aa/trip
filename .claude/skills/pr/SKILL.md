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

로컬 `main`은 fetch를 안 했으면 origin보다 뒤처져 있을 수 있어서, 그 상태로
`main..HEAD`를 쓰면 이 브랜치와 무관한 커밋까지 diff에 섞여 PR 범위를 착각하게
된다 — 반드시 먼저 fetch하고 `origin/main` 기준으로 비교한다:

```bash
git fetch origin main --quiet
git branch --show-current
git log origin/main..HEAD --oneline
git diff origin/main...HEAD --stat
```
`origin/main..HEAD`에 커밋이 없으면 올릴 게 없다고 안내하고 중단.

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

## 7. CI 상태 확인

`.github/workflows/ci.yml`이 있는 한, 리뷰 코멘트만 보고 머지를 권하지 않는다 —
CI 결과도 반드시 확인한다 (실제로 2026-07-12에 이 확인을 건너뛸 뻔해서
`client` job이 깨진 채로 머지될 뻔한 적이 있다):

```bash
gh pr checks <번호>
```

- **하나라도 `pending`이면**: 바로 재확인하지 말고 몇십 초~1분 정도 기다렸다가
  다시 확인한다 (긴 대기가 필요하면 ScheduleWakeup으로 나중에 다시 확인). 다
  끝날 때까지 머지를 권하지 않는다.
- **하나라도 `fail`이면**: `gh run view <run-id> --job <job-id> --log`로 실패
  로그를 읽고 원인을 진단한다. 원인이 명확하고 안전하게 고칠 수 있으면(예:
  린트 에러, 락파일 불일치처럼 이번 변경과 직접 관련된 문제) 바로 고쳐서 같은
  브랜치에 커밋 + push하고, 다시 `gh pr checks`로 확인한다. 원인이 불명확하거나
  이번 PR 범위를 벗어나면 사용자에게 실패 내용을 설명하고 어떻게 할지 물어본다.
- **다 `pass`면**: 결과를 사용자에게 알리고 다음 단계(머지)로 넘어간다.

## 8. 머지 (사용자 확인 후에만)

CI가 전부 통과한 걸 확인한 뒤, 리뷰 결과까지 본 사용자가 머지를 요청하면 그때
진행한다 — 자동 머지 아님:
```bash
gh pr view <번호> --json mergeable,mergeStateStatus
gh pr merge <번호> --merge
```
(기존 PR들과 같은 방식 — squash/rebase 아닌 일반 merge commit.) `CONFLICTING`이면
`main`과 충돌하는 파일을 파악해서 어느 쪽 내용이 실제 코드 상태와 더 맞는지
확인한 뒤 해결하고 진행한다 — 무조건 최신 커밋을 우선하지 않는다.

## 9. 머지 후 브랜치 정리

머지 직후 바로 이어서 정리한다 — 다음 작업을 이 브랜치 위에서 계속 이어가지
않기 위함 (오래된 브랜치를 재사용하면 이미 머지된 PR과 다음 작업이 뒤섞인다):
```bash
git checkout main
git pull origin main
git branch -d <머지한-브랜치>
git push origin --delete <머지한-브랜치>
```
`git branch -d`가 "not fully merged" 경고를 내면(로컬 커밋이 원격/머지 커밋과
다르게 인식된 경우) 먼저 `git log main..<브랜치> --oneline`으로 실제로 남은
커밋이 없는지 확인하고, 없으면 `-D`로 강제 삭제한다.
