import { defineBoot } from '#q-app';
import { createI18n } from 'vue-i18n';

import messages from '@/i18n';

export type MessageLanguages = keyof typeof messages;
// en-ZA is the master schema; every other locale must match its shape.
export type MessageSchema = (typeof messages)['en-ZA'];

// https://vue-i18n.intlify.dev/guide/advanced/typescript.html#global-resource-schema-type-definition
/* eslint-disable @typescript-eslint/no-empty-object-type */
declare module 'vue-i18n' {
  export interface DefineLocaleMessage extends MessageSchema {}
  export interface DefineDateTimeFormat {}
  export interface DefineNumberFormat {}
}
/* eslint-enable @typescript-eslint/no-empty-object-type */

export default defineBoot(({ app }) => {
  const i18n = createI18n<{ message: MessageSchema }, MessageLanguages>({
    locale: 'en-ZA',
    fallbackLocale: 'en-ZA',
    legacy: false,
    messages,
  });

  app.use(i18n);
});
