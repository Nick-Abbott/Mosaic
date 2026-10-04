import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  workers: 2,
  outputDir: '.qa/results',
  reporter: [['list'], ['html', { outputFolder: '.qa/report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4323',
    launchOptions: process.env.MOSAIC_CHROMIUM_PATH ? { executablePath: process.env.MOSAIC_CHROMIUM_PATH } : {},
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'pnpm preview --port 4323 --ignore-lock',
    url: 'http://127.0.0.1:4323',
    reuseExistingServer: !process.env.CI,
  },
});
