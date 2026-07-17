---
name: dev-up
description: 이 프로젝트(trip)의 로컬 개발 환경을 띄웁니다. Docker로 Postgres(pgvector)와 Redis 컨테이너를 확인/기동하고, pgvector 확장을 보장한 뒤 server(Spring Boot)와 client(Next.js)를 백그라운드로 실행합니다. "앱 실행해줘", "개발 서버 켜줘", "dev-up" 같은 요청에 사용합니다.
---

# /dev-up — 로컬 개발 환경 기동

server(`application.properties`)가 기대하는 상태: Postgres(pgvector)가 `localhost:5434`,
Redis가 `localhost:6379`. DB명/사용자/비밀번호는 (gitignore된)
`server/src/main/resources/application.properties`의 `spring.datasource.*` 값을 따른다
— 새 컨테이너를 만들 때 그 파일에서 값을 읽어 그대로 쓴다. 파일이 없으면 사용자에게 값을
물어본다. docker-compose 파일은 없고 컨테이너는 수동(`docker run`)으로 관리한다 — 이름이
제각각일 수 있으니 **포트로 기존 컨테이너를 찾고**, 없을 때만 새로 만든다.

## 1. Docker 데몬 확인

```bash
docker info >/dev/null 2>&1 && echo OK || echo DOWN
```

- `DOWN`이면 `open -a Docker`로 Docker Desktop을 띄우고, 최대 ~60초 정도
  `docker info`가 성공할 때까지 폴링한다. 그래도 안 되면 사용자에게 Docker Desktop을
  직접 켜달라고 안내하고 중단한다.

## 2. 기존 컨테이너 탐색 (이름 무관, 포트 기준)

```bash
docker ps -a --format '{{.Names}}\t{{.Ports}}\t{{.Status}}'
```

- `5434->` 매핑이 있는 컨테이너 → Postgres 컨테이너로 취급
- `6379->` 매핑이 있는 컨테이너 → Redis 컨테이너로 취급
- 상태 문자열로 분기 (`/dev-down`이 컨테이너를 `pause`로 내려두므로, 단순히
  `Up`으로 시작하는지만 보면 `Up ... (Paused)`도 이미 떠 있다고 착각해서
  실제로는 얼어붙은 채로 넘어간다):
  - `Up ... (Paused)` 포함 → `docker unpause <name>`
  - `Up`으로 시작(Paused 아님) → 이미 정상 기동 중, 아무것도 안 함
  - 그 외(`Exited` 등) → `docker start <name>`

## 3. 없으면 새로 생성

Postgres (pgvector 포함 이미지 사용):
```bash
docker run -d --name trip-postgres \
  -p 5434:5432 \
  -e POSTGRES_DB=<application.properties의 spring.datasource.url DB명> \
  -e POSTGRES_USER=<...username> \
  -e POSTGRES_PASSWORD=<...password> \
  -v trip-postgres-data:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
```

Redis:
```bash
docker run -d --name trip-redis \
  -p 6379:6379 \
  -v trip-redis-data:/data \
  redis:7-alpine
```

## 4. Postgres 준비 확인

```bash
docker exec <postgres-container> pg_isready -U <username>
```
`pg_isready`가 성공할 때까지 몇 초 간격으로 재시도(최대 ~30초). pgvector
확장 생성(`CREATE EXTENSION IF NOT EXISTS vector`)은 더 이상 여기서 수동으로
하지 않는다 — Flyway 도입(이슈 #29) 이후 서버 기동 시 첫 마이그레이션
(`V1__baseline.sql`)이 자동으로 보장한다.

## 5. Redis 준비 확인

```bash
docker exec <redis-container> redis-cli ping
```
`PONG`이 나올 때까지 재시도.

## 6. server / client 백그라운드 실행

각각 별도 백그라운드 프로세스로 실행(`run_in_background: true`):
```bash
cd server && ./gradlew bootRun
```
```bash
cd client && npm run dev
```

- server 로그에서 `Started DemoApplication` (또는 에러) 확인 후 보고.
- client 로그에서 `Ready in` (또는 에러) 확인 후 보고.
- 이미 두 프로세스가 실행 중인 것 같으면(포트 8080/3000 사용 중) 새로 띄우지 말고 알려준다.

## 7. 최종 보고

아래를 한 번에 정리해서 사용자에게 알린다:
- Postgres/Redis 컨테이너 상태 (새로 만듦 / 기존 것 기동함 / 이미 떠 있었음)
- server: http://localhost:8080
- client: http://localhost:3000
