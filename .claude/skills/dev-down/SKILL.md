---
name: dev-down
description: 이 프로젝트(trip)의 로컬 개발 환경을 종료합니다. 포트 8080(server)/3000(client)을 점유한 프로세스를 종료하고, Docker 컨테이너(trip-db, redis)는 기본으로 pause합니다 (CPU는 거의 안 먹으면서 재개는 즉시 되도록 stop 대신 pause). "앱 종료해줘", "개발 서버 꺼줘", "dev-down" 같은 요청에 사용합니다.
---

# /dev-down — 로컬 개발 환경 종료

`/dev-up`의 대칭 스킬. server/client 프로세스 종료 + Docker 컨테이너(`trip-db`,
`redis`) pause까지가 기본 동작이다.

## 1. 포트로 프로세스 탐색

```bash
lsof -i :8080 -sTCP:LISTEN -t
lsof -i :3000 -sTCP:LISTEN -t
```
각각 PID가 안 나오면 "이미 안 떠 있음"으로 판단하고 해당 프로세스는 건너뛴다.

## 2. 프로세스 종료

먼저 SIGTERM으로 정상 종료 시도:
```bash
kill <pid>
```
몇 초 기다린 뒤 같은 포트에 여전히 프로세스가 있으면(`lsof`로 재확인) SIGKILL:
```bash
kill -9 <pid>
```
gradle은 `bootRun`이 데몬 프로세스를 띄울 수 있으니, SIGTERM 후에도 포트가 안 풀리면
자식 프로세스까지 확인한다 (`lsof -i :8080`로 재탐색).

## 3. Docker 컨테이너 pause (기본 동작)

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'
```
로 `5434->`(Postgres), `6379->`(Redis) 매핑 컨테이너를 찾아 `docker pause <name>`.
이미 paused/정지 상태면 건너뛴다.

`stop`이 아니라 `pause`를 쓰는 이유: CPU 사용은 거의 0으로 떨어지면서(cgroup freezer로
프로세스를 얼림), `stop`처럼 Postgres/Redis가 재부팅 절차를 거칠 필요 없이
`docker unpause`로 즉시(수백ms) 재개된다 — 메모리는 계속 점유하지만, 로컬 개발 환경
용도로는 이 트레이드오프가 낫다. `docker rm`은 물론 하지 않는다. "완전히 종료"/
"컨테이너까지 stop" 같은 명시적 요청이 있을 때만 `docker stop`으로 대신한다.

`/dev-up`이 paused 컨테이너를 `docker unpause`로 되살리는 로직을 갖고 있어야
이 pause 기본값이 안전하다 — `/dev-up`의 2단계(기존 컨테이너 탐색) 참고.

## 4. 최종 보고

- server/client 프로세스: 종료함 / 이미 안 떠 있었음
- Docker 컨테이너: pause함 / 이미 paused·정지 상태였음
