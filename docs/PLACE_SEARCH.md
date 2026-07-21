# 장소 검색 & 지역 그룹핑 — 스펙

AI가 일정을 짤 때 어떤 장소 후보를 어떻게 추릴지에 대한 스펙. 임베딩 기반 후보
검색부터 읍/면/동 세분화까지 다룬다. 코드 위치는 전부
`server/src/main/java/com/example/demo/` 기준.

## 1. 임베딩 기반 후보 검색

**요구사항**: 사용자가 "바다 보면서 커피 마시고 싶어" 같은 자연어로 말해도, 정확한
키워드 매칭 없이 의미 기반으로 관련 장소를 찾아야 한다.

**설계**:
- 대화 텍스트(히스토리 + 새 메시지)를 Gemini 임베딩 API(`gemini-embedding-001`,
  `AiService.createEmbedding`)로 벡터화한다. 이 호출은 Redis cache-aside로
  캐싱되어 있어 동일한 텍스트면 API를 다시 부르지 않는다 (`docs/REDIS.md` 참고).
- `jeju_place`의 모든 장소도 이름/카테고리/주소/지역/설명을 합친 텍스트를 같은
  방식으로 미리 임베딩해서 pgvector 컬럼(`embedding`)에 저장해둔다
  (`TourApiService.createEmbeddings`, `POST /jeju/embedding`).
- 요청이 오면 `JejuPlaceRepository.findSimilarPlacesWithFilter`가 코사인 거리
  (`embedding <=> query_embedding`)로 정렬해 상위 50개를 후보로 낸다.

## 2. 지역(region) 키워드 필터

**요구사항**: 사용자가 "동부 위주로" 같은 지역을 명시하면 그 지역 후보만 우선
보여줘야 하고, 명시하지 않으면 전체에서 찾아야 한다.

**설계**:
- 대화 텍스트에 "동부"/"서부"/"남부"/"제주시" 중 하나가 포함돼 있으면 그 값을
  `targetRegion`으로 잡아 SQL `WHERE region = ?` 필터를 pgvector 검색에 같이 건다
  (카테고리도 동일한 방식으로 `targetCategory`).
- 필터링 결과가 0개면 필터를 빼고 전체에서 재검색한다 — 조건이 너무 좁아서 후보가
  아예 없어지는 상황을 막기 위한 안전장치.
- `region` 값 자체는 매 요청마다 계산하지 않는다. TourAPI에서 장소를 수집하는
  시점에 `JejuPlaceUtil.getRegion(lat, lng)`이 위경도 임계값으로 한 번 계산해서
  DB에 저장해두고 계속 재사용한다 (임계값은 `docs/API.md` 참고).

## 3. 읍/면/동(`sub_region`) 세분화

**요구사항**: 하루 일정이 `region` 4개(동부/서부/남부/제주시) 안에서도 너무 넓게
흩어지지 않아야 한다 — 예를 들어 "제주시" 하나에 애월읍부터 구좌읍까지 다 포함되는
문제를 막고, "1일차는 서귀포 위주, 2일차는 애월 위주" 식으로 하루 동선이 좁은
지역에 모이게 해야 한다.

**설계 검토 — 두 가지 방식**:
| | 좌표 클러스터링 | 행정구역명 매핑 (채택) |
|---|---|---|
| 방식 | 매 요청마다 k-means 등으로 실시간 그룹핑 | 좌표 → 읍/면/동 이름을 한 번 변환해 DB에 저장, 계속 재사용 |
| 장점 | 새 지오코딩 불필요, 여행 일수에 자동 대응 | 사람이 쓰는 지명이라 AI 설명도 자연스러움("애월 쪽 위주로..."), 매 요청 계산 없음 |
| 단점 | 그룹에 이름이 없어 설명이 부자연스러움, 요청마다 연산 비용 | 장소 추가 시마다 지오코딩 필요 |

행정구역명 매핑을 선택한다 — 사용자 경험(자연스러운 설명)과 반복 요청 시 연산
비용 두 측면에서 더 유리하다.

**상세 스펙**:
1. **스키마**: `JejuPlace` 엔티티에 `subRegion` 필드를 추가한다. `ddl-auto=update`
   설정이라 서버 재시작만으로 `sub_region` 컬럼이 생성되며, 별도 마이그레이션
   스크립트는 필요 없다.
2. **역지오코딩**: 카카오 로컬 API `coord2regioncode` 엔드포인트
   (`GET https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x={lng}&y={lat}`)를
   쓴다. 기존 장소 검색에 쓰던 `kakao.api.key`를 그대로 재사용한다 — 같은 카카오
   로컬 API 계열이라 별도 키 발급이 불필요하다. 응답의 `region_3depth_name`을
   읍/면/동 값으로 쓴다.
   - 응답 `documents` 배열에는 법정동(`region_type: "B"`)과 행정동(`"H"`) 문서가
     같이 올 수 있다. 배열 순서가 항상 고정이라는 보장이 문서화되어 있지 않으므로,
     `region_type == "B"`인 문서를 명시적으로 찾아서 쓴다(없으면 첫 번째 문서로
     대체). 순서에 기대서 `documents[0]`을 그냥 쓰면, 좌표에 따라 법정동/행정동
     이름이 섞여 들어가 같은 동네인데 다른 `sub_region` 값을 갖게 될 수 있다.
3. **백필 서비스**: `SubRegionService.fillSubRegions()`가 `sub_region IS NULL`인
   장소를 100개씩 가져와 하나씩 역지오코딩 → 업데이트하고, 더 남지 않을 때까지
   반복한다. 엔드포인트는 `POST /jeju/sub-region` — 카카오 로컬 API는 일일 호출
   한도가 넉넉해서 `/jeju/embedding`과 달리 여러 번 나눠 호출할 필요 없이 한 번의
   요청으로 전체를 처리한다.
4. **실패 처리 요구사항**: 좌표가 비정상이거나(제주 범위 밖) API 호출이 실패하는
   행이 있을 수 있다. 이런 행도 반드시 처리 완료로 표시해야 한다 — 그렇지 않으면
   같은 행이 `findWithoutSubRegion` 조회에 계속 걸려 백필 루프가 끝나지 않는다.
   따라서 실패 시에도 `sub_region`에 `"알수없음"`을 채우는 것을 스펙에 포함한다
   (`finally` 블록으로 항상 기록).
5. **좌표 데이터 정합성**: 백필 대상 장소는 TourAPI에서 수집된 좌표를 그대로
   신뢰하지만, 대한민국 범위를 벗어나는 등 명백히 잘못된 좌표는 카카오 주소 검색
   API로 주소 기준 재지오코딩해 바로잡는 것을 데이터 정합성 체크리스트에 포함한다.
6. **프롬프트 반영**: 장소 목록은 `region`으로 먼저 묶고, 그 안에서 다시
   `sub_region`으로 한 번 더 묶어 계층 구조로 나열한다:
   ```
   [동부]
     - 성산읍
       [p7] 이름 (카테고리)
     - 표선면
       [p11] 이름 (카테고리)
   ```
   프롬프트 규칙에도 "같은 권역 안에서도 읍/면/동을 최대한 하나로 통일하라"는
   항목을 포함한다. 프롬프트 원문 전체는 `docs/PROMPTS.md` 참고.

## 4. region/main_category 인덱스

**요구사항**: `findSimilarPlacesWithFilter`의 region/main_category 필터가
실제로 인덱스를 타서 빠르게 걸러지는지 확인하고, 필요하면 인덱스를 추가한다.

**설계**:
- `JejuPlace`에 복합 B-tree 인덱스 추가: `@Table(indexes = {@Index(name =
  "idx_jeju_place_region_category", columnList = "region, main_category")})`.
  마이그레이션 도구 없이 `ddl-auto=update`만으로 관리하는 프로젝트라, 벡터
  인덱스와 달리 이 정도 단순 인덱스는 JPA 애너테이션만으로 표현 가능해서
  서버 재기동 시 자동 생성된다.
- 컬럼 순서는 쿼리가 항상 `region`을 먼저 걸고 `main_category`를 나중에
  거는 패턴이라 `region`을 선두 컬럼으로 뒀다.

**측정 결과** (2026-07-14, `jeju_place` 1,013행 기준):

| 조건 | 인덱스 전 | 인덱스 후 |
|---|---|---|
| `region='동부'` (152행 매칭) | Seq Scan, 92.4ms | Bitmap Index Scan, 42.8ms |
| 필터 없음(전체 1,013행) | Seq Scan, 326.7ms | Seq Scan, 280.1ms (변화 없음 — 예상대로) |

필터 없는 경우 인덱스가 도움이 안 되는 건 당연하다 — 인덱스는 "일부만
걸러낼 때"만 유효하고, 전체를 다 봐야 하는 쿼리에는 애초에 쓸 수 없다.

**중요한 확인 사항**: `(:region IS NULL OR region = :region)` 같은
"파라미터가 null이면 조건 무시" 패턴은 일반적으로 Postgres가 제네릭 실행
계획을 쓸 경우 인덱스를 못 타고 시퀀셜 스캔으로 빠지는 함정으로 알려져
있다. 실제 `/plan/chat` 엔드포인트를 통해(리터럴이 아니라 JPA 파라미터
바인딩으로) 호출한 뒤 `pg_stat_user_indexes.idx_scan`이 실제로 증가하는지
확인한 결과 — **이 프로젝트에서는 문제없이 인덱스가 사용됨**을 확인했다.

**당시 범위에서 뺀 것** (이슈 #27로 완료됨 — 아래 5번 참고): `embedding` 컬럼이
pgvector `vector` 타입이 아니라 `text`라서(쿼리에서 매번 `::vector`로 캐스팅),
벡터 전용 인덱스(HNSW 등)는 컬럼 타입 마이그레이션이 선행돼야 했다.

## 5. embedding 컬럼 vector 타입 전환

**요구사항**: `embedding` 컬럼이 `text`라서 검색 쿼리가 정렬 대상 행마다
매번 text→vector 캐스팅을 반복하던 것을 없앤다 (4번 "당시 범위에서 뺀 것"
참고).

**설계**:
- 실제 임베딩 차원을 DB에서 직접 확인한 결과 **3072차원**
  (`gemini-embedding-001` 기본 출력)이었다.
- `V2__embedding_to_vector.sql`(Flyway, 이슈 #29 참고)로 컬럼을
  `text` → `vector(3072)`로 변환: `ALTER TABLE jeju_place ALTER COLUMN
  embedding TYPE vector(3072) USING embedding::vector;` — 기존 1,013행의
  텍스트 값을 그대로 재캐스팅해서 데이터 손실 없이 전환.
- `JejuPlace.embedding`의 `@Column(columnDefinition = "text")`를
  `"vector(3072)"`로 변경. `ddl-auto=validate`(이슈 #29에서 전환) 상태에서도
  문제없이 통과함을 실제 부팅으로 확인.
- `JejuPlaceRepository.findSimilarPlacesWithFilter`에서 이제 불필요해진
  좌변 캐스팅(`embedding::vector`) 제거. 파라미터 쪽(`CAST(:embedding AS
  vector)`)은 그대로 — 애플리케이션에서 넘어오는 값은 여전히 Java
  `String`이라 캐스팅이 필요함.

**HNSW 인덱스는 이번에도 추가하지 않음**: pgvector의 HNSW/IVFFlat 인덱스는
2000차원까지만 지원하는데 임베딩이 3072차원이라 그대로는 인덱싱 불가.
`halfvec` 타입(16비트 반정밀도)을 쓰면 4000차원까지 인덱싱 가능하지만
정밀도 손실이 있고, 아래 측정 결과처럼 타입 전환만으로도 이미 충분히
빨라서 인덱스 없이 지금 규모(1,013행)에서는 문제없다고 판단. 데이터가 크게
늘어나면 halfvec 전환을 별도 이슈로 재검토한다.

**측정 결과** (2026-07-17, region 필터 있는 쿼리 기준, `idx_jeju_place_region_category`
인덱스는 이미 적용된 상태):

| 상태 | 실행 시간 |
|---|---|
| text 컬럼 + 매 행 `::vector` 캐스팅 (인덱스만 적용) | 42.8ms |
| vector(3072) 컬럼 (캐스팅 없음) | **1.7ms** |

인덱스만 있을 때보다도 25배 가까이 빨라졌다 — 이 쿼리에서는 "인덱스로 후보를
줄이는 것"보다 "행마다 반복되던 text→vector 파싱을 없애는 것"의 효과가 훨씬
컸다.

## 검증 방법

배포 전 확인해야 할 것:
- 백필 완료 후 `sub_region IS NULL`인 행이 0건이어야 한다.
- 실제 `/plan/chat` 요청의 최종 프롬프트에 `[region] → 읍면동 → [id] 이름` 계층
  구조가 정확히 들어가는지 확인한다 (서버 로그에 `placesStr`을 임시로 출력해
  검증하고, 검증 후에는 제거한다 — 상시 로깅 대상 아님).
