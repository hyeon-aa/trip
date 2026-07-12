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

## 화면 구성

- **`/` (메인, `KakaoMap`)**: 3칸 레이아웃 — 지도(+상단에 타이틀과 "위시리스트" 링크) |
  일정 칼럼(`SchedulePanel`, day 탭으로 날짜 선택) | 채팅 칼럼(`PlanChat`). 위시리스트
  검색/목록은 더 이상 이 화면에 없음 — 채팅 입력 공간을 넓히려고 별도 페이지로 분리.
- **`/wishlist`**: `SearchBar` + `WishlistPanel`만 있는 화면. 지도가 없어서 두 컴포넌트의
  지도 연동 콜백(`onSelectPlace`, `onClickItem`)은 여기서는 안 씀(옵셔널 처리됨).

## 컴포넌트

`components/`에 있는 주요 화면 단위:
`KakaoMap`, `PlanChat`, `SchedulePanel`, `SearchBar`, `WishlistPanel`.
공통 스타일 가이드(버튼, 카드, 간격, 타이포 스케일 등)는 아직 없음.

## 색상 — day 팔레트 (`lib/dayColors.ts`)

일차(Day)별 지도 마커/동선/일정 패널 색상은 `getDayColor(day)`가 정하는 7색 팔레트
(색각이상 검증 완료, red는 위시리스트 마커 색과 겹쳐서 제외). 8일 이상인 여행은 팔레트가
순환되어 두 날짜가 같은 색을 공유할 수 있음 — 알려진 제한사항으로 남겨둠(짧은 여행
위주라 실사용 빈도 낮다고 판단).

## 채팅 온보딩 UI (`PlanChat`)

스타일/동행자/기간/도착 시간/출발 시간/숙소 6단계를 서버 호출 없이 프론트에서만
순서대로 물어봄:
- 스타일: 다중 선택 칩(토글) + "선택 완료" 버튼
- 동행자/기간: 단일 선택 버튼(눌리면 바로 다음 질문)
- 도착/출발 시간: 프리셋 버튼 + `<input type="time">`으로 정확한 시각 선택 가능
- 숙소: 카카오 장소검색 미니 검색창(`SearchBar`와 유사한 UX) + "건너뛰기"

## TODO

- [ ] 컬러 팔레트(브랜드 컬러, 상태 색상 등) 정의
- [ ] `body` font-family 하드코딩 정리 (Geist 변수 사용 여부 결정)
- [ ] 컴포넌트 스타일 가이드 (버튼/카드/타이포그래피)
