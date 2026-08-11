---
paths:
  - "**/*"
---

# Security rules

- API 키/시크릿(`kakao.api.key`, `gemini.api.key`, `groq.api.key`,
  `tourapi.service.key`, `visitjeju.api.key` 등)은 절대 소스 코드, 커밋, 문서,
  테스트 파일에 실제 값으로 하드코딩하지 않는다. 항상
  `server/src/main/resources/application.properties`(`.gitignore` 등록됨)에서만
  읽고, 예시가 필요하면 `application.properties.example`처럼 플레이스홀더 값만
  쓴다.
- `client/.env.local`도 마찬가지로 커밋 대상이 아니다(`.gitignore`의
  `.env`/`.env.local`).
- 채팅 응답에 실제 키/시크릿 값을 그대로 출력하지 않는다 — 사용자의 IDE에
  이미 값이 보이는 상황이라도 마찬가지다. 필요하면 "키 길이"처럼 값을 노출하지
  않는 방식으로 확인한다.
- 새 외부 API 연동을 추가할 때도 이 패턴을 그대로 따른다 — 새 키는
  `application.properties`에 추가하고 `.example` 파일에는 플레이스홀더만
  남긴다.
