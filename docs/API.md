# API

server의 REST 엔드포인트 정리. 모두 `com.example.demo` 하위 컨트롤러 기준.

## 인증 / CORS

- **인증 없음** — `SecurityConfig`가 CSRF 비활성 + 모든 요청 `permitAll()`.
  (커밋 히스토리의 "로그인 기능 제외"와 일치 — 의도된 상태이며 버그 아님)
- **CORS**: `CorsConfig`에서 `http://localhost:3000`만 허용 (`GET/POST/PUT/DELETE`).
  다른 origin(배포 도메인 등)에서 붙으려면 여기를 먼저 고쳐야 한다.

## `/wishlist`

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/wishlist` | `CreateWishlistRequest { name, category, address, lat, lng }` | `WishlistResponse` |
| GET | `/wishlist` | - | `WishlistResponse[]` |
| DELETE | `/wishlist/{id}` | - | 204 |

`WishlistResponse { id, name, category, address, lat, lng }`

## `/place`

| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/place/search` | `query` | 카카오 로컬 검색 API 응답 원문 (JSON 문자열 그대로 패스스루, DTO 변환 없음) |

## `/jeju` (데이터 수집/유지보수용)

| Method | Path | 설명 |
|---|---|---|
| POST | `/jeju/init/all` | TourAPI(`apis.data.go.kr`, `areaBasedList2`, `areaCode=39` 고정=제주)에서 제주 관광지/맛집/문화시설/레포츠 데이터를 긁어와 `jeju_place`에 저장 (`TourApiService.initAllJejuPlaces`). 콘텐츠 타입(12/14/28/39)별 최대 200페이지 순회, 페이지당 100건. 이미 있는 이름은 스킵. 매우 오래 걸릴 수 있음. |
| POST | `/jeju/embedding` | `embedding IS NULL`인 장소 최대 100개에 대해 Gemini 임베딩 생성 (`/seed-embeddings` 스킬이 이걸 반복 호출). |
| POST | `/jeju/sub-region` | `sub_region IS NULL`인 장소 전부에 대해 카카오 좌표→행정구역(coord2regioncode) API로 읍/면/동 이름을 채움 (`SubRegionService.fillSubRegions`). 100개씩 페이지 순회하며 남는 게 없어질 때까지 한 호출 안에서 반복 — `/jeju/embedding`과 달리 여러 번 호출할 필요 없음. 실패한 행은 "알수없음"으로 채워서 무한 재시도를 막는다. |

**지역(region) 분류** (`JejuPlaceUtil.getRegion`) — 위경도 임계값으로만 판정, 실제
행정구역 경계가 아닌 근사치:
```
lng > 126.8        → 동부
lng < 126.3        → 서부
lat < 33.3          → 남부
그 외              → 제주시
```
`PlanChatController`의 "같은 날엔 같은 권역만" 규칙이 이 값을 그대로 신뢰한다 —
경계 근처 장소는 오분류될 수 있음 (실제로 `region=제주시`인 장소 중 일부의
`sub_region`이 서귀포시 관할 읍면동으로 나오는 경우가 있음 — 알려진 한계).

`sub_region`은 이 4대 권역과 별개로, 카카오 역지오코딩이 준 읍/면/동 이름을 그대로
저장한 것 (SQL 필터링에는 안 쓰고, `/plan/chat` 프롬프트에서 region 안을 한 번 더
묶는 용도로만 사용).

## `/plan`

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/plan/chat` | `PlanChatRequest { message: string, history: ChatMessageDto[], accommodationLat?: number, accommodationLng?: number }` | `text/event-stream` (SSE), 단일 이벤트로 최종 JSON 전송 |

`ChatMessageDto { role: "user"\|"assistant"\|"system", content: string }`

`accommodationLat`/`accommodationLng`는 선택값 — 클라이언트가 채팅 온보딩 마지막
단계(숙소 검색)에서 골랐을 때만 채워진다. 있으면 `optimalOrder`가 매일 이 좌표를
동선의 시작/종료 앵커로 쓴다 (마지막 날은 공항이 종료 앵커로 대체됨).

응답 JSON 형태(`type: "question"` 또는 `type: "schedule"`)와 그 안에 들어가는
프롬프트 로직은 `docs/PROMPTS.md` 참고.

## 데이터 모델

`JejuPlace` (`jeju_place` 테이블): `id, name, category, mainCategory, region,
subRegion, address, lat, lng, description, embedding(text, pgvector 캐스팅용)`.

`Wishlist` (`wishlist` 테이블): `id, name, lat, lng, category, kakaoPlaceId, address`.

## 현재 미사용

`RedisService` (`save`/`get`, TTL 1시간)는 정의만 되어 있고 코드베이스 어디에서도
호출되지 않는다. 캐싱/세션 등 어떤 플로우에도 아직 연결되어 있지 않음 — 죽은
코드는 아니지만(빈 스캐폴딩) 실제로 동작 중인 캐시가 있다고 오해하지 말 것.
