import { z } from 'zod';

// Limits mirror LoginRequest in docs/openapi.yaml. Messages are i18n keys, translated where shown,
// so the schema stays a plain constant and the wording stays in src/i18n.
export const loginSchema = z.object({
  username: z
    .string()
    .trim()
    .min(1, 'login.validation.usernameRequired')
    .max(50, 'login.validation.usernameTooLong'),
  password: z
    .string()
    .min(1, 'login.validation.passwordRequired')
    .max(72, 'login.validation.passwordTooLong'),
});

export type LoginFormValues = z.input<typeof loginSchema>;
