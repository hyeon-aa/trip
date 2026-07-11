# 컨벤션

기존 커밋 히스토리에서 확인되는 실제 관례를 정리한 것. 새 커밋도 아래 형식을 따른다.

## 커밋 메시지

```
<type>: <설명>(<scope>)
```

- `type`: `feat`, `fix`, `chore` 등
- `설명`: 한글로 작성
- `scope`: 프론트/백엔드 중 어느 쪽 변경인지 표시 — `(front)` / `(back)`.
  두 쪽 모두에 걸치거나 범위가 명확하지 않으면 생략 가능 (예: `feat: coderabbit 추가`).

예시:
```
feat: 채팅(front)
fix: dto 추가(back)
feat: tourapi로 변경(back)
```

## 브랜치 이름

```
<type>/<짧은-이름>
```
예: `feat/place`, `feat/claude-setup`

## 코드 구조

### server (`server/src/main/java/com/example/demo/`)
도메인별 패키지로 분리하고, 패키지 안에서 `Controller` / `Service` / `Repository` /
`dto/`로 나눈다 (예: `wishlist/WishlistController.java`, `WishlistService.java`,
`WishlistRepository.java`, `wishlist/dto/`). 여러 외부 API를 조합하는 도메인은
`Client` + `Service`로 더 나누기도 한다 (`jeju/TourApiClient.java` +
`jeju/TourApiService.java`).

### client
- `feature/<domain>/api.ts` — 도메인별 fetch 래퍼. 서버 base URL은
  `NEXT_PUBLIC_API_URL` 환경변수에서 읽는다.
- `types/<domain>/*.ts` — 도메인별 공유 타입, 서버 DTO와 이름을 맞춘다.
- `components/` — 화면에 조합되는 최상위 기능 컴포넌트.
