import type { NativeSyntheticEvent } from 'react-native';

// Codegen recognizes these marker names independently of their import path.
// Keep their TypeScript meaning local so emitted declarations work with both
// RN 0.76 and the current Strict API, without exposing blocked deep imports.
export type Double = number;
export type Int32 = number;
export type WithDefault<
  T extends number | boolean | string | ReadonlyArray<string>,
  // Codegen consumes this parameter as metadata; it does not narrow the TS value.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _Default extends T | string | null | undefined,
> = T | null | undefined;
export type DirectEventHandler<T> = (
  event: NativeSyntheticEvent<T>
) => void | Promise<void>;
