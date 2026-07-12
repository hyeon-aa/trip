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
