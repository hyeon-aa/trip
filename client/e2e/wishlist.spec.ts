import { test, expect } from "@playwright/test";

// 전부 진짜 서버(8080, e2e 프로필) + 진짜 Postgres로 붙는다 — 모킹 없음.
// 서버가 e2e 프로필로 미리 떠있어야 한다 (docs/E2E.md 참고).

test.describe("위시리스트", () => {
  test("검색해서 추가하고, 메모 남기고, 새로고침해도 유지되고, 삭제된다", async ({
    page,
  }) => {
    page.on("dialog", (dialog) => dialog.accept());

    await page.goto("/wishlist");

    // 검색 → 추가
    await page.getByPlaceholder("어디로 떠날까요?").fill("협재해수욕장");
    await page.getByRole("button", { name: "검색" }).click();
    const firstResult = page.locator('[data-testid^="search-result-"]').first();
    await expect(firstResult).toBeVisible();
    await firstResult.getByRole("button", { name: "저장" }).click();

    const item = page.locator('[data-testid^="wishlist-item-"]', {
      hasText: "협재해수욕장",
    }).first();
    await expect(item).toBeVisible();

    // 메모 추가 — 이 카드 안에서만
    await item.getByText("✏️ 메모 추가").click();
    await item.getByPlaceholder(/왜 저장했나요/).fill("E2E 테스트 메모");
    await page.keyboard.press("Enter");
    await expect(item.getByText("📝 E2E 테스트 메모")).toBeVisible();

    // 새로고침 후에도 유지
    await page.reload();
    const itemAfterReload = page.locator('[data-testid^="wishlist-item-"]', {
      hasText: "협재해수욕장",
    }).first();
    await expect(itemAfterReload.getByText("📝 E2E 테스트 메모")).toBeVisible();

    // 메모 수정
    await itemAfterReload.getByText("📝 E2E 테스트 메모").click();
    await itemAfterReload.getByPlaceholder(/왜 저장했나요/).fill("수정된 메모");
    await page.keyboard.press("Enter");
    await expect(itemAfterReload.getByText("📝 수정된 메모")).toBeVisible();

    // 삭제
    await itemAfterReload.getByRole("button", { name: "삭제" }).click();
    await expect(page.getByText("협재해수욕장")).not.toBeVisible();
  });
});
