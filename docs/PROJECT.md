# 프로젝트 설명

제주도 여행 일정을 짜주는 AI 챗봇 서비스. 사용자가 챗봇과 대화하면서 여행 스타일,
동행자, 기간을 알려주면 AI가 실제 제주 장소 데이터를 바탕으로 일자별 일정을
만들어주고, 카카오맵에 동선을 표시해준다.

## 구성

모노레포이며 client/server가 완전히 독립적으로 배포·실행된다.

| | 스택 |
|---|---|
| `client/` | Next.js 16 (App Router), React 19, TypeScript, Tailwind v4, Kakao Maps SDK |
| `server/` | Spring Boot 4 (Java 17, Gradle), PostgreSQL + pgvector, Redis |

## 핵심 기능

- **AI 일정 채팅** (`/plan/chat`, SSE): 대화 맥락 임베딩 → pgvector 유사도 검색으로
  후보 장소 추리기 → Gemini로 JSON 일정 생성 → 동선 최적화(haversine 거리) →
  방문 시간대 배정까지 한 번에 처리한다. 자세한 흐름은 루트 `CLAUDE.md` 참고.
- **위시리스트**: 사용자가 저장한 장소도 AI 추천 후보에 함께 들어간다.
- **장소 검색**: 카카오 장소 검색 API 연동.
- **지도 표시**: 생성된 일정을 `KakaoMap` 컴포넌트로 시각화.

## 외부 연동

- **Gemini** — 채팅 완성(`gemini-2.5-flash`), 텍스트 임베딩(`gemini-embedding-001`)
- **Groq** — `llama-3.3-70b-versatile` (현재 메인 플로우에서는 미사용, `AiService.chat`에 존재)
- **TourAPI / VisitJeju** — 제주 장소 데이터 수집(`jeju` 패키지)
- **Kakao** — 지도 렌더링(client), 장소 검색(server)

## 로컬 실행 환경

`server/src/main/resources/application.properties`에 아래가 필요하다 (커밋 대상 아님,
`.gitignore`에 등록됨):
- PostgreSQL (pgvector 확장 포함) — 기본 포트 5434, DB명 `trip`
- Redis — 기본 포트 6379
- API 키: `kakao.api.key`, `visitjeju.api.key`, `gemini.api.key`, `groq.api.key`,
  `tourapi.service.key`

client는 서버 주소를 `NEXT_PUBLIC_API_URL` 환경변수로 읽는다.
