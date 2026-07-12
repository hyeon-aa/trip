---
name: seed-embeddings
description: jeju_place 테이블에서 임베딩이 없는 장소들에 대해 서버의 /jeju/embedding 엔드포인트를 반복 호출해 pgvector 임베딩을 채웁니다. TourAPI로 장소 데이터를 새로 넣은 뒤(/jeju/init/all) 항상 필요한 후속 작업입니다. "임베딩 채워줘", "장소 임베딩 백필" 같은 요청에 사용합니다.
---

# /seed-embeddings — 장소 임베딩 백필

`TourApiService.createEmbeddings()` (`POST /jeju/embedding`)는 한 번 호출당
`embedding IS NULL`인 장소를 최대 100개만 처리한다 (`PageRequest.of(0, 100)`).
남은 개수가 0이 될 때까지 반복 호출해야 전체가 채워진다.

server가 떠 있어야 한다 (필요하면 먼저 `/dev-up` 실행 안내).

## 1. server 살아있는지 확인

```bash
curl -sf -o /dev/null -w '%{http_code}' http://localhost:8080/jeju/embedding -X POST
```
연결 실패하면 server가 안 떠 있는 것 — `/dev-up`부터 실행하라고 안내하고 중단.
(이 curl 자체가 이미 한 라운드를 처리하니 그대로 아래 루프의 1회차로 카운트한다.)

## 2. 남은 개수 확인

Postgres 컨테이너 이름은 `docker ps --format '{{.Names}}\t{{.Ports}}'`로 `5434->`
매핑을 찾아 알아낸다.

```bash
docker exec <postgres-container> psql -U <application.properties의 spring.datasource.username> -d <db명> -t -c \
  "SELECT count(*) FROM jeju_place WHERE embedding IS NULL;"
```

## 3. 남은 개수 > 0 이면 반복

```bash
curl -X POST http://localhost:8080/jeju/embedding
```
호출 후 다시 2번 쿼리로 남은 개수를 확인. 개수가 0이 되거나, 두 번 연속
줄어들지 않으면(임베딩 생성 실패가 반복되는 경우) 중단하고 사용자에게 보고한다.

## 4. 결과 보고

- 총 몇 라운드를 돌았는지
- 처리 전/후 남은 개수
- 실패가 있었다면 server 로그(`docker exec` 불필요, `./gradlew bootRun` 콘솔에
  `Embedding 실패!` 라인 출력됨)를 확인하라고 안내
