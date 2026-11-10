import { Hero } from './Hero';
import { SlideStack, type Slide } from './SlideStack';
import { Ending } from './slides/Ending';

const SLIDES: Slide[] = [
  {
    id: 'ending',
    label: 'Software Mansion',
    tone: 'ending',
    content: <Ending />,
  },
];

/** The hero, with the Software Mansion ending a sheet that rises over it. */
export const Landing = () => <SlideStack hero={<Hero />} slides={SLIDES} />;
