# 디자인

아직 별도로 정의된 디자인 시스템은 없다. 아래는 `create-next-app` 기본값에서
그대로 남아있는 현재 상태 기록이며, 컬러 팔레트/컴포넌트 스타일 규칙이 정해지면
이 문서를 채워나간다.

## 현재 상태 (`client/app/globals.css`, `client/app/layout.tsx`)

- **스타일링**: Tailwind CSS v4 (`@import "tailwindcss"`), PostCSS 플러그인 사용
- **폰트**: Geist Sans / Geist Mono (`next/font`), CSS 변수 `--font-sans`,
  `--font-mono`로 노출. 단, `body`의 `font-family`는 현재 `Arial, Helvetica, sans-serif`로
  하드코딩되어 있어 Geist 변수가 실제로 적용되고 있지 않다 — 확인 필요.
- **색상**: 라이트/다크 모드를 `prefers-color-scheme` 미디어쿼리로 전환
  - 라이트: `--background: #ffffff`, `--foreground: #171717`
  - 다크: `--background: #0a0a0a`, `--foreground: #ededed`
- **지도**: `react-kakao-maps-sdk`로 `KakaoMap` 컴포넌트 렌더링

## 컴포넌트

`components/`에 있는 주요 화면 단위:
`KakaoMap`, `PlanChat`, `SchedulePanel`, `SearchBar`, `WishlistPanel`.
공통 스타일 가이드(버튼, 카드, 간격, 타이포 스케일 등)는 아직 없음.

## TODO

- [ ] 컬러 팔레트(브랜드 컬러, 상태 색상 등) 정의
- [ ] `body` font-family 하드코딩 정리 (Geist 변수 사용 여부 결정)
- [ ] 컴포넌트 스타일 가이드 (버튼/카드/타이포그래피)
