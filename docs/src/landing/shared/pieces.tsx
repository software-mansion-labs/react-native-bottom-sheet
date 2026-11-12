import { createContext } from 'react';

export const clamp = (value: number, min = 0, max = 1) =>
  Math.min(max, Math.max(min, value));

/** Whether this slide is the one showing, so hidden ones can stop their work. */
export const SlideActiveContext = createContext(true);
