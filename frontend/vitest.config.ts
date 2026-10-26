import { fileURLToPath } from 'node:url';

import { quasar, transformAssetUrls } from '@quasar/vite-plugin';
import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

// Separate from quasar.config.ts on purpose: unit tests only need the Vue compiler and Quasar's
// component auto-import, not the full app build.
export default defineConfig({
  plugins: [vue({ template: { transformAssetUrls } }), quasar({ sassVariables: false })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Quasar resolves this itself at build time; its runtime wrappers just return their argument.
      '#q-app': '@quasar/app-vite',
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/stores/**', 'src/composables/**'],
      // The Quasar store factory is bootstrap wiring with no logic of its own.
      exclude: ['src/**/*.spec.ts', 'src/stores/index.ts'],
      reporter: ['text', 'html'],
      thresholds: {
        'src/stores/**': { lines: 70 },
        'src/composables/**': { lines: 70 },
      },
    },
  },
});
