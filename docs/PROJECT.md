# 프로젝트 운영 — DB 마이그레이션 / 로컬 환경

프로젝트 개요(스택, 핵심 기능, 외부 연동)는 루트 `CLAUDE.md` 참고. 이 문서는
CLAUDE.md에 없는 두 가지 운영 정보만 다룬다: DB 스키마 관리 규칙, 로컬 실행에
필요한 환경 설정.

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
