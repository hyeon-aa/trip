import { test, expect } from "@playwright/test";

// 위시리스트 마커를 실제로 지도에 렌더링하고 클릭해서 팝업을 확인한다.
// KakaoMap의 기본 지도 중심(제주시 인근)에서 너무 먼 장소를 쓰면 확대
// 레벨상 마커가 화면 밖으로 가려질 수 있어, 중심에서 가까운 장소를 쓴다.
// wishlist.spec.ts와 다른 장소명을 써서 병렬 실행 시 데이터가 안 겹치게 한다.
test.describe("지도 마커 팝업", () => {
  test.beforeEach(async ({ page }) => {
    page.on("dialog", (dialog) => dialog.accept());
    await page.goto("/wishlist");
    await page.getByPlaceholder("어디로 떠날까요?").fill("카카오 스페이스닷원");
    await page.getByRole("button", { name: "검색" }).click();
    const result = page.locator('[data-testid^="search-result-"]').first();
    await expect(result).toBeVisible();
    await result.getByRole("button", { name: "저장" }).click();
    await expect(
      page.locator('[data-testid^="wishlist-item-"]', { hasText: "카카오 스페이스닷원" })
    ).toBeVisible();
  });

  test.afterEach(async ({ page }) => {
    await page.goto("/wishlist");
    const item = page.locator('[data-testid^="wishlist-item-"]', {
      hasText: "카카오 스페이스닷원",
    });
    // locator.count()는 expect()와 달리 자동 대기가 없어서, goto 직후 목록이
    // 아직 fetch되기 전이면 0으로 잘못 판단하고 삭제를 건너뛸 수 있다 — 항상
    // 렌더링을 기다린 뒤 삭제한다(beforeEach에서 만든 항목이라 항상 있어야 함).
    await expect(item).toBeVisible();
    await item.getByRole("button", { name: "삭제" }).click();
    await expect(item).not.toBeVisible();
  });

  test("위시리스트 마커 클릭 시 팝업이 뜨고, 닫기/전환이 된다", async ({
    page,
  }) => {
    await page.goto("/");

    const marker = page.locator('img[src*="marker_red.png"]').first();
    await expect(marker).toBeVisible({ timeout: 10_000 });
    await marker.click();

    const popup = page.getByText("카카오 스페이스닷원").last();
    await expect(popup).toBeVisible();

    // 닫기 버튼
    await page.getByRole("button", { name: "닫기" }).click();
    await expect(page.getByRole("button", { name: "닫기" })).not.toBeVisible();

    // 다시 클릭하면 재오픈
    await marker.click();
    await expect(page.getByRole("button", { name: "닫기" })).toBeVisible();
  });
});
