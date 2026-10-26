<script setup lang="ts">
import { useQuasar } from 'quasar';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

const { t } = useI18n();
const $q = useQuasar();
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const navItems = [
  {
    name: 'transactions',
    path: '/transactions',
    labelKey: 'nav.transactions',
    icon: 'receipt_long',
  },
  { name: 'disputes', path: '/disputes', labelKey: 'nav.disputes', icon: 'report_problem' },
] as const;

// A detail page such as /disputes/:id still belongs to its section, so match by prefix.
function isActive(item: { path: string }): boolean {
  return route.path === item.path || route.path.startsWith(`${item.path}/`);
}

// Phones and tablets get the bottom bar; the drawer only earns its width on desktop.
const isDesktop = computed(() => $q.screen.gt.sm);
const chromeClass = computed(() =>
  $q.dark.isActive ? 'bg-dark text-white' : 'bg-white text-dark',
);

const signingOut = ref(false);

async function signOut(): Promise<void> {
  signingOut.value = true;
  try {
    await auth.logout();
  } catch {
    $q.notify({ type: 'warning', message: t('auth.signOutFailed') });
  } finally {
    signingOut.value = false;
  }
  await router.replace({ name: 'login' });
}

// A plain "#main-content" link would make the router navigate, so move focus by hand.
function skipToContent(): void {
  document.getElementById('main-content')?.focus();
}
</script>

<template>
  <q-layout view="hHh LpR lFf">
    <a class="skip-link" href="#main-content" @click.prevent="skipToContent">
      {{ t('app.skipToContent') }}
    </a>

    <q-header :class="$q.dark.isActive ? 'bg-dark text-white' : 'bg-primary text-white'" bordered>
      <q-toolbar>
        <q-icon name="verified_user" size="sm" aria-hidden="true" />
        <q-toolbar-title class="text-weight-medium">{{ t('app.name') }}</q-toolbar-title>
        <div v-if="auth.user" class="row items-center no-wrap user-badge">
          <q-icon name="account_circle" size="sm" aria-hidden="true" class="q-mr-xs" />
          <span class="ellipsis" data-testid="user-display-name">{{ auth.user.displayName }}</span>
        </div>
      </q-toolbar>
    </q-header>

    <q-drawer v-if="isDesktop" :model-value="true" behavior="desktop" bordered :width="248">
      <nav :aria-label="t('nav.primary')" class="column fit no-wrap justify-between">
        <q-list padding>
          <q-item
            v-for="item in navItems"
            :key="item.name"
            clickable
            :to="{ name: item.name }"
            :active="isActive(item)"
            :aria-current="isActive(item) ? 'page' : undefined"
            class="nav-item"
          >
            <q-item-section avatar>
              <q-icon :name="item.icon" aria-hidden="true" />
            </q-item-section>
            <q-item-section>{{ t(item.labelKey) }}</q-item-section>
          </q-item>
        </q-list>
        <q-list padding>
          <q-item clickable :disable="signingOut" class="nav-item" @click="signOut">
            <q-item-section avatar>
              <q-icon name="logout" aria-hidden="true" />
            </q-item-section>
            <q-item-section>{{ t('nav.signOut') }}</q-item-section>
          </q-item>
        </q-list>
      </nav>
    </q-drawer>

    <q-page-container>
      <main id="main-content" tabindex="-1" class="main-content">
        <router-view />
      </main>
    </q-page-container>

    <q-footer v-if="!isDesktop" bordered :class="chromeClass">
      <nav :aria-label="t('nav.primary')" class="bottom-nav">
        <q-btn
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          flat
          stack
          no-caps
          square
          class="bottom-nav__item"
          :class="{ 'bottom-nav__item--active': isActive(item) }"
          :aria-current="isActive(item) ? 'page' : undefined"
          :icon="item.icon"
          :label="t(item.labelKey)"
        />
        <q-btn
          flat
          stack
          no-caps
          square
          class="bottom-nav__item"
          icon="logout"
          :label="t('nav.signOut')"
          :loading="signingOut"
          @click="signOut"
        />
      </nav>
    </q-footer>
  </q-layout>
</template>

<style scoped lang="scss">
.user-badge {
  max-width: 50%;
}

.bottom-nav {
  display: flex;
  padding-bottom: env(safe-area-inset-bottom);
}

.bottom-nav__item {
  flex: 1 1 0;
  min-height: 56px;
}

// Colour alone must not say which tab is current, so the active tab also gets a bar.
.bottom-nav__item--active {
  color: var(--q-primary);
  box-shadow: inset 0 3px 0 0 currentcolor;
  font-weight: 600;
}

.nav-item.q-item--active {
  font-weight: 600;
}

.main-content:focus {
  outline: none;
}
</style>
