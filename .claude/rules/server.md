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
