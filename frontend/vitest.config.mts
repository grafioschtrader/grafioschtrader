import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  // Pure-function tests can import domain types carrying class-transformer decorators.
  oxc: { decorator: { legacy: true } },
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.spec.ts'],
    setupFiles: ['src/test-setup.ts']
  },
  resolve: {
    alias: {
      'plotly.js': resolve(__dirname, 'node_modules/plotly.js/dist/plotly.js')
    }
  }
});
