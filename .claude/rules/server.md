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
