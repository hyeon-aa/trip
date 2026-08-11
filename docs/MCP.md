# MCP — 개념 정리 및 이 프로젝트에서의 쓰임

MCP(Model Context Protocol) 자체에 대한 개념 정리 + 이 프로젝트에 붙인 서버를
함께 정리한 문서.

## MCP가 뭔가

Claude 같은 AI가 외부 도구(DB, API, 파일 시스템 등)랑 대화하는 **표준화된
방식**이다. MCP가 없으면 "DB 쿼리하는 법", "GitHub 이슈 읽는 법"을 매번
따로 통합해야 하는데, MCP 서버 하나를 붙이면 그 서버가 "이런 도구들을 쓸 수
있어요"라고 미리 알려주고, AI는 표준화된 방식으로 그 도구를 호출한다 —
REST API에 OpenAPI 스펙이 있는 것과 비슷한 결.

**핵심 비유**: MCP 서버는 "AI와 외부 시스템 사이의 통역사"다. AI가 직접
Postgres 드라이버로 접속하는 게 아니라, "이 DB에 이런 걸 물어봐줘"라고
MCP 서버에게 부탁하면, 서버가 실제 쿼리를 실행하고 결과만 돌려준다.

## `.mcp.json` — 프로젝트에 어떤 MCP 서버를 붙일지 선언하는 파일

프로젝트 루트의 `.mcp.json`에 등록해두면, Claude Code가 세션 시작 시 그
서버를 자동으로 띄우고 연결한다. 팀원과 공유하기 위한 파일이라 커밋 대상
이지만, **비밀번호/API 키를 직접 적으면 안 된다** — Claude Code가 지원하는
`${VAR}` 환경변수 치환 문법을 써서, 실제 값은 각자의 로컬 환경변수로 채운다
(`.claude/rules/security.md` 참고).

## 이 프로젝트에 붙인 서버 — Postgres (읽기 전용)

```json
{
  "mcpServers": {
    "trip-postgres": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-e", "DATABASE_URI",
        "crystaldba/postgres-mcp", "--access-mode=restricted"],
      "env": { "DATABASE_URI": "${TRIP_DB_URI}" }
    }
  }
}
```

- **이미지**: [`crystaldba/postgres-mcp`](https://github.com/crystaldba/postgres-mcp)
  (Postgres MCP Pro) — 공식 `@modelcontextprotocol/server-postgres`는 SQL
  인젝션 취약점으로 지원 종료(deprecated)돼서 대체됨.
- **동작 방식**: Claude Code가 필요할 때마다 `docker run`으로 이 이미지를
  띄운다(`--rm`이라 요청 끝나면 컨테이너도 사라짐). 컨테이너 안에 MCP
  프로토콜을 아는 작은 서버가 떠서, Claude가 "jeju_place에 협재 들어간
  곳 몇 개야?" 같은 걸 물어보면 실제 SQL을 실행해 결과만 돌려준다.
- **`--access-mode=restricted`**: SELECT만 가능하고 데이터/스키마 수정은
  전부 막힌다 — 검증·조회 용도로만 쓰고 실수로 데이터를 건드릴 위험이 없다.
- **네트워킹 주의**: 컨테이너 안에서는 `localhost`가 호스트 머신을 가리키지
  않는다(Docker Desktop for Mac 기준) — `host.docker.internal`을 써야
  호스트에 떠있는 `trip-db`(5434)에 닿는다.
- **로컬 설정**: `TRIP_DB_URI` 환경변수를 `~/.zshrc`에 등록해야 한다(값은
  `server/src/main/resources/application.properties`의 `spring.datasource.*`
  기준으로 조합, 실제 값은 커밋되지 않음). 등록 후 새 터미널/Claude Code
  재시작이 필요하다.
- **왜 붙였나**: 이 프로젝트 실사용 검증 중 `docker exec trip-db psql ...`로
  직접 DB를 확인하는 일이 잦았다(예: 이슈 #58 버그의 실제 좌표 확인). MCP로
  붙이면 매번 raw docker exec를 안 치고, 읽기 전용이 보장된 채로 더 안전하게
  같은 작업을 할 수 있다.
