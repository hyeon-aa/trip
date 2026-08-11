# 컨벤션

기존 커밋 히스토리에서 확인되는 실제 관례를 정리한 것. 새 커밋도 아래 형식을 따른다.

## 커밋 메시지

```
<type>: <설명>(<scope>)
```

- `type`: `feat`, `fix`, `chore` 등
- `설명`: 한글로 작성
- `scope`: 프론트/백엔드 중 어느 쪽 변경인지 표시 — `(front)` / `(back)`.
  두 쪽 모두에 걸치거나 범위가 명확하지 않으면 생략 가능 (예: `feat: coderabbit 추가`).

예시:
```
feat: 채팅(front)
fix: dto 추가(back)
feat: tourapi로 변경(back)
```

## 브랜치 이름

```
<type>/<짧은-이름>
```
예: `feat/place`, `feat/claude-setup`

코드 구조(패키지/디렉토리 배치 규칙)는 `.claude/rules/server.md`,
`.claude/rules/client.md`를 참고 — 해당 디렉토리 작업 시 자동으로 적용된다.
