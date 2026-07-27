# E2E 테스트 (Playwright)

`client/e2e/`의 Playwright 테스트는 진짜 E2E다 — Gemini 호출만
`StubAiChatService`(`server/src/main/java/com/example/demo/ai/StubAiChatService.java`,
`@Profile("e2e")`)로 갈아끼우고, 나머지(서버, Postgres, 위시리스트 CRUD,
동선 최적화, 이벤트 발행)는 전부 실제 코드 경로를 그대로 탄다. `page.route()`
같은 네트워크 모킹은 쓰지 않는다.

## 로컬에서 돌리기

Playwright의 `webServer`(`client/playwright.config.ts`)는 Next.js 개발 서버만
관리한다 — Spring Boot 서버는 `e2e` 프로필로 미리, 직접 띄워둬야 한다.

### 1. 전용 Postgres 컨테이너

기존 `/dev-up`의 `trip-db`(5434)와 섞이지 않게 별도 컨테이너를 쓴다:

```bash
docker run -d --name trip-e2e-db -p 5436:5432 \
  -e POSTGRES_DB=test -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test \
  pgvector/pgvector:pg16
```

Redis는 `/dev-up`으로 띄운 기존 컨테이너(6379)를 그대로 재사용해도 된다.

### 2. 서버를 `e2e` 프로필로 기동

```bash
cd server
KAKAO_API_KEY=$(grep '^kakao.api.key=' src/main/resources/application.properties | cut -d= -f2)
SPRING_PROFILES_ACTIVE=e2e \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5436/test" \
  KAKAO_API_KEY="$KAKAO_API_KEY" \
  ./gradlew bootRun
```

`KAKAO_API_KEY`는 실제 값이 필요하다 — 위시리스트 검색(`/place/search`)이
진짜 Kakao API를 호출하기 때문. Gemini/Groq/TourAPI 키는 이 프로필에서
전혀 쓰이지 않으므로 더미 값이면 충분하다(`application-e2e.properties`에
이미 채워져 있음).

기동 시 `E2eSeedRunner`(`@Profile("e2e")`)가 `jeju_place`에 지도 기본 중심
(`33.450701, 126.570667`) 근처로 촘촘하게 클러스터된 합성 테스트 장소
6개를 심는다 — 실제 서로 멀리 떨어진 관광지를 쓰면 지도 기본 확대 레벨
(`level={3}`, 실측 스케일바 기준 약 50m 단위)에서 마커가 화면 밖으로
가려져 Playwright가 못 찾는다.

### 3. Playwright 실행

```bash
cd client
pnpm run test:e2e
```

## 테스트 간 DB 격리가 없다 — 항상 순차 실행

세 시나리오(`wishlist.spec.ts`, `marker-popup.spec.ts`,
`schedule-delete-and-popup.spec.ts`)는 테스트별로 트랜잭션 롤백이나 별도
스키마 같은 격리 장치 없이 하나의 실제 서버+DB를 그대로 공유한다. 그래서
`playwright.config.ts`는 `workers: 1` / `fullyParallel: false`로 고정돼
있다 — 병렬로 돌리면:

- 서로 다른 테스트가 만든 위시리스트 row가 이름 기준 필터에 섞여
  `strict mode violation`(동일 텍스트 다중 매치)이 난다.
- 여러 브라우저 세션이 하나의 Next dev 서버를 두고 자원을 경합해 렌더링이
  느려지고 마커 클릭이 간헐적으로 실패한다(둘 다 실측으로 확인됨).

각 spec의 `afterEach`가 자신이 만든 위시리스트 데이터를 지운다. `count()`는
Playwright의 자동 대기가 없는 메서드라, `goto()` 직후 목록이 아직
fetch되기 전에 호출하면 0으로 오판해 삭제를 건너뛸 수 있다 — 반드시
`expect(locator).toBeVisible()`로 렌더링을 먼저 기다린 뒤 조작한다.

## CI

`.github/workflows/ci.yml`의 `client` job이 `server` job과 동일한
postgres(5432)/redis(6379) 서비스 컨테이너를 띄우고, 서버를 `e2e` 프로필로
백그라운드 기동 + `/wishlist` 폴링으로 준비를 기다린 뒤 Playwright를 돌린다.

다음 GitHub Secret이 등록돼 있어야 CI가 통과한다:

- `KAKAO_API_KEY` — 서버가 실제 위시리스트 검색에 사용
- `NEXT_PUBLIC_KAKAO_MAP_KEY` — 클라이언트가 실제 지도를 렌더링하는 데 사용
