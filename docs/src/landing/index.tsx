import { Hero } from './Hero';
import { SlideStack } from './SlideStack';
import { Ending } from './slides/Ending';

/** The hero, with the Software Mansion ending a sheet that rises over it. */
export const Landing = () => (
  <SlideStack hero={<Hero />} sheet={<Ending />} label="Software Mansion" />
);
