/** Where the app under test is running. The default is the Docker stack on port 8080. */
export const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8080';

export interface DemoUser {
  readonly username: string;
  readonly password: string;
  readonly displayName: string;
  /** Where the app sends this role after sign-in. */
  readonly home: string;
}

// The seeder's default. Override it when the stack runs with a different APP_DEMO_PASSWORD.
const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD || 'demo-password';

export const thandi: DemoUser = {
  username: 'thandi',
  password: DEMO_PASSWORD,
  displayName: 'Thandi Mokoena',
  home: '/transactions',
};

export const pieter: DemoUser = {
  username: 'pieter',
  password: DEMO_PASSWORD,
  displayName: 'Pieter van der Merwe',
  home: '/transactions',
};

/** The demo back-office agent. */
export const lerato: DemoUser = {
  username: 'lerato',
  password: DEMO_PASSWORD,
  displayName: 'Lerato Dlamini',
  home: '/agent',
};
