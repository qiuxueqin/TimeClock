import type { Config } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

const config: Config = {
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
};

export default config;
