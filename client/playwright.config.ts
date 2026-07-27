import { defineConfig, devices } from "@playwright/test";

// 서버(8080)는 여기서 관리하지 않는다 — SPRING_PROFILES_ACTIVE=e2e로 미리
// 띄워둬야 한다는 전제(이슈 #48, docs/E2E.md 참고). webServer는 Next.js만
// 자동 기동한다.
//
// 테스트별로 DB 상태를 격리하지 않고 하나의 실제 서버+DB를 그대로 공유하므로
// (모킹은 Gemini뿐) 병렬로 돌리면 위시리스트 row가 테스트 간에 섞이거나,
// 동시 실행되는 여러 브라우저 세션이 하나의 Next dev 서버를 두고 자원 경합을
// 벌여 마커 클릭이 간헐적으로 실패하는 게 실측으로 확인됨 — 항상 순차 실행한다.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: "html",
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "pnpm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
  },
});
