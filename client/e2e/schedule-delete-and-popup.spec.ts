import { test, expect } from "@playwright/test";

// 온보딩 위자드를 실제로 진행해서 /plan/chat을 진짜로 호출한다. 서버가
// e2e 프로필이라 Gemini 응답 자체는 StubAiChatService가 대신하지만, DB
// 조회/동선 최적화/이벤트 발행은 전부 진짜 코드 경로를 탄다.
test.describe("일정 생성 후 삭제/마커 팝업", () => {
  test("온보딩 완료 → 일정 렌더링 → 장소 삭제 → 마커 팝업", async ({
    page,
  }) => {
    await page.goto("/");

    // 1. 스타일 (다중 선택)
    await page.getByRole("button", { name: "오름/자연경관" }).click();
    await page.getByRole("button", { name: "선택 완료" }).click();

    // 2. 동행자
    await page.getByRole("button", { name: "혼자" }).click();

    // 3. 기간
    await page.getByRole("button", { name: "2박 3일" }).click();

    // 4. 도착 시간
    await page.getByRole("button", { name: "오전 (9~11시)" }).click();

    // 5. 출발 시간
    await page.getByRole("button", { name: "오후 (12~5시)" }).click();

    // 6. 숙소 - 건너뛰기 (여기서 실제 /plan/chat 호출)
    await page.getByRole("button", { name: "건너뛰기" }).click();

    // 일정 렌더링 대기 (StubAiChatService라 실제 Gemini보다 훨씬 빠름)
    await expect(
      page.getByRole("button", { name: "Day 1" })
    ).toBeVisible({ timeout: 30_000 });

    const day1Places = page.locator('button[aria-label$="삭제"]');
    const beforeCount = await day1Places.count();
    expect(beforeCount).toBeGreaterThan(0);

    // 첫 번째 장소 삭제 - 패널에서 개수 감소 확인
    await day1Places.first().click();
    await expect(day1Places).toHaveCount(beforeCount - 1);

    // 남은 일정 마커(숫자 원형 오버레이) 클릭 → 팝업에 추천시간/이유 확인
    const scheduleMarker = page
      .locator('[data-testid^="schedule-marker-1-"]')
      .first();
    await scheduleMarker.click();
    await expect(
      page.locator('[id="__react-kakao-maps-sdk___Map"]').getByText(/🕒/)
    ).toBeVisible();
  });
});
