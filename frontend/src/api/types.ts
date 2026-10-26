// Named aliases over the generated OpenAPI types, so feature code never indexes
// `components['schemas']` by hand. Regenerate the source with `npm run gen:api`.
import type { components } from './generated/schema';

type Schemas = components['schemas'];

export type Me = Schemas['Me'];
export type LoginRequest = Schemas['LoginRequest'];
export type Role = Schemas['Role'];
export type ProblemCode = Schemas['ProblemCode'];
export type ProblemDetail = Schemas['ProblemDetail'];
export type FieldError = Schemas['FieldError'];
