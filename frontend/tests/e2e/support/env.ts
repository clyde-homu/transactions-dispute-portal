/** Where the app under test is running. The default is the Docker stack on port 8080. */
export const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8080';

export interface DemoUser {
  readonly username: string;
  readonly password: string;
  readonly displayName: string;
}

// The seeder's default. Override it when the stack runs with a different APP_DEMO_PASSWORD.
const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD || 'demo-password';

export const thandi: DemoUser = {
  username: 'thandi',
  password: DEMO_PASSWORD,
  displayName: 'Thandi Mokoena',
};

export const pieter: DemoUser = {
  username: 'pieter',
  password: DEMO_PASSWORD,
  displayName: 'Pieter van der Merwe',
};
