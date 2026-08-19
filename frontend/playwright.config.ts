import type { PlaywrightTestConfig } from '@playwright/test';

// Playwright E2E 配置（S0-FE-01 骨架，S0-QA-01 完善）。
// 所有者：QA Agent（E2E 公共夹具单 owner）。
const config: PlaywrightTestConfig = {
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:5173',
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1280, height: 800 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 } } },
  ],
};

export default config;
