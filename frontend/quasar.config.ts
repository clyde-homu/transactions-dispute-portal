// Configuration for the Dispute Portal SPA
// https://v2.quasar.dev/quasar-cli-vite/quasar-config-file

import { defineConfig } from '#q-app';

export default defineConfig((ctx) => {
  return {
    // Boot order matters: i18n first so the axios error handler can use translated messages.
    boot: ['i18n', 'axios'],

    css: ['app.scss'],

    extras: ['roboto-font', 'material-icons'],

    build: {
      target: {
        browser: 'baseline-widely-available',
      },

      typescript: {
        strict: true,
        vueShim: true,
      },

      // The Spring Boot app serves index.html for unknown paths, so history mode is safe.
      vueRouterMode: 'history',

      vitePlugins: [
        [
          '@intlify/unplugin-vue-i18n/vite',
          {
            // Messages use named tokens such as {count}, which need the message compiler at runtime.
            runtimeOnly: false,
            ssr: ctx.mode.ssr || ctx.mode.ssg,
            include: [ctx.appPaths.resolve.app('src/i18n')],
          },
        ],
      ],
    },

    devServer: {
      open: false,
      port: 9000,
      // The API and the SPA share an origin in production, so the dev server mimics that.
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: false,
        },
      },
    },

    framework: {
      config: {
        // Follows the operating system preference; there is no manual toggle in this version.
        dark: 'auto',
      },

      lang: 'en-GB',

      plugins: ['Notify'],
    },

    animations: [],
  };
});
