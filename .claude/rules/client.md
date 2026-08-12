---
paths:
  - "client/**/*"
---

# Client rules

- 이 저장소의 Next.js 버전은 학습 데이터와 breaking change가 있다. 기억에 의존해 Next.js
  API를 쓰기 전에 `client/node_modules/next/dist/docs/`에서 관련 가이드를 확인할 것
  (`client/AGENTS.md` 참고).
- `feature/<domain>/api.ts` — 도메인별 fetch 래퍼. 서버 base URL은
  `NEXT_PUBLIC_API_URL` 환경변수에서 읽는다.
- `types/<domain>/*.ts` — 도메인별 공유 타입. 서버 DTO와 이름을 맞춘다.
- `components/` — 화면에 조합되는 최상위 기능 컴포넌트 (`KakaoMap`, `PlanChat`,
  `SchedulePanel`, `SearchBar`, `WishlistPanel` 등).
- `feature/*/api.ts`의 fetch 래퍼는 `res.ok`를 확인하지 않고 바로 `res.json()`을
  반환하면 안 된다 — 서버 에러 응답(예: 500)에도 JSON 파싱 자체는 성공하는
  경우가 있어서, 실패를 성공으로 착각한 채로 그대로 화면에 보여주는 문제가
  실제로 있었다(`sendWishlistChat` 코드 리뷰에서 발견). `res.ok`가 아니면
  에러를 던져서 호출부의 `catch`가 잡게 한다.
- 채팅/텍스트 입력에서 Enter로 전송하는 `onKeyDown` 핸들러는
  `e.nativeEvent.isComposing`을 반드시 같이 확인한다 — 한글 등 IME로 글자를
  조합하는 도중에 확정용으로 누른 Enter가 전송으로 오작동해서, 다 안 쳐진
  글자가 그대로 전송되는 문제가 `PlanChat`/`WishlistChat` 양쪽에서 같은
  패턴으로 나타났다.
