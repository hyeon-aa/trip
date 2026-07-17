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

## DB 스키마 관리

Flyway로 관리한다 (`server/src/main/resources/db/migration/`). 규칙:
- 파일명은 `V<번호>__<설명>.sql` (예: `V2__add_wishlist_version.sql`) — 번호는
  증가만 하고 건너뛰지 않는다.
- `V1__baseline.sql`은 Flyway 도입 시점의 실제 스키마를 캡처한 베이스라인이다.
  이미 스키마가 있는 DB에서는 `spring.flyway.baseline-on-migrate=true` 설정
  덕분에 이 파일이 실행되지 않고 "이미 적용됨"으로만 기록되고, 완전히 새
  DB에서는 그대로 실행되어 스키마를 처음부터 만든다 — 같은 파일로 기존/신규
  DB 둘 다 커버.
- `spring.jpa.hibernate.ddl-auto=validate`로 설정돼 있다 — Hibernate가 스키마를
  자동으로 바꾸지 않고, 엔티티가 실제 스키마와 일치하는지 검증만 한다. 스키마
  변경은 반드시 새 마이그레이션 파일로 한다 (엔티티만 고치고 끝내지 않기).
- 이미 실행된 마이그레이션 파일은 수정하지 않는다 — 체크섬이 바뀌어 다음 기동
  시 Flyway가 에러를 낸다. 잘못된 게 있으면 그걸 고치는 새 마이그레이션을
  추가한다.

## 로컬 실행 환경

`server/src/main/resources/application.properties`에 아래가 필요하다 (커밋 대상 아님,
`.gitignore`에 등록됨 — `application.properties.example`을 복사해서 값을 채운다):
- PostgreSQL (pgvector 확장 포함) — 기본 포트 5434, DB명 `trip`
- Redis — 기본 포트 6379
- API 키: `kakao.api.key`, `visitjeju.api.key`, `gemini.api.key`, `groq.api.key`,
  `tourapi.service.key`

client는 서버 주소를 `NEXT_PUBLIC_API_URL` 환경변수로 읽는다.
