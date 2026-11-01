import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  // Pure-function tests can import domain types carrying class-transformer decorators.
  oxc: { decorator: { legacy: true } },
  test: {
    globals: true,
    // Compiler-based ratchets scan all sources. Running them together exceeds their per-test timeout on this host.
    maxWorkers: 1,
    environment: 'node',
    include: ['src/**/*.spec.ts'],
    setupFiles: ['src/test-setup.ts']
  },
  resolve: {
    alias: {
      'plotly.js': resolve(import.meta.dirname, 'node_modules/plotly.js/dist/plotly.js')
    }
  }
});
