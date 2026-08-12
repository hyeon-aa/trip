---
paths:
  - "server/**/*.java"
---

# Server rules

- `server/src/main/java/com/example/demo/` 아래 도메인별 패키지로 분리한다.
- 패키지 안에서 `Controller` / `Service` / `Repository` / `dto/`로 나눈다
  (예: `wishlist/WishlistController.java`, `WishlistService.java`,
  `WishlistRepository.java`, `wishlist/dto/`).
- 여러 외부 API를 조합하는 도메인은 `Client` + `Service`로 더 나눈다
  (예: `jeju/TourApiClient.java` + `jeju/TourApiService.java`).
- Spring AI `@Tool` 메서드를 감싸는 클래스는 `<도메인>Tools.java`로 두고,
  `Controller`/`Service`와 마찬가지로 실제 로직은 `Service`에 위임한다(예:
  `wishlist/WishlistTools.java`, `plan/PlanEditTools.java`). LLM이 호출하는
  진입점이라는 점만 다를 뿐 역할은 얇은 어댑터에 가깝다.
- 이름(문자열)으로 `JejuPlace`를 찾을 때는 `findByNameContaining`(부분
  일치)을 직접 쓰지 말고 `JejuPlaceRepository.findBestMatchesByName`을
  쓴다 — 부분 일치만 쓰면 흔한 이름 조각이 여러 후보에 걸려서 전혀 무관한
  장소가 먼저 골라지는 문제가 실제로 두 번 반복됐다(위시리스트 Tool, 위치
  지정 삽입 Tool 코드 리뷰에서 각각 발견).
- 같은 계산/판단 로직이 두 클래스 이상에서 필요해지면, 먼저 짠 쪽에
  그대로 두고 복붙하지 말고 공용 위치로 뽑는다 — 순수 계산은
  `JejuPlaceUtil`(정적 메서드, 예: `haversineMeters`), 리포지토리 조회
  로직은 해당 `Repository` 인터페이스의 `default` 메서드(예:
  `findBestMatchesByName`)에 둔다. `RouteOptimizer`에 있던 haversine
  공식이 나중에 또 필요해졌을 때(`PlanEditTools`) 실제로 이렇게 뽑아서
  재사용했다.
- 외부 API(Gemini/Kakao 등) 호출을 감쌀 때 `catch (Exception e)`로 뭉뚱그려
  `System.out.println`만 찍고 끝내지 않는다 — 최소한 "재시도해볼 만한
  실패"(타임아웃, 5xx, 429)와 "다시 해봐도 안 될 실패"(4xx, 잘못된
  요청)를 구분한다. `AiService.chatWithGemini`가 이 패턴의 기준이다
  (`cause` 체인을 타고 내려가 실제 HTTP 상태 코드로 분기). 새 외부 API
  연동을 추가할 때 이 구분 없이 블랭킷 catch만 쓰면, 키가 만료되거나
  쿼터가 소진된 상황과 일시적 네트워크 문제를 구분 못 해서 계속 조용히
  실패하는 걸 아무도 못 알아챈다(코드 리뷰에서 반복 지적됨).
