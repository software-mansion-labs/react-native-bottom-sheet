import { Ending } from './slides/Ending';
import { useFrameHeight } from './shared/stage';
import { Hero } from './Hero';
import { DetentsSlide } from './slides/Music';
import { ThreadsSlide } from './slides/Portfolio';
import { SlideStack, type Slide } from './SlideStack';
import { CoordinationSlide } from './slides/Video';

const SLIDES: Slide[] = [
  {
    id: 'detents',
    label: '01 Detents',
    tone: 'paper',
    content: <DetentsSlide />,
  },
  {
    id: 'coordination',
    label: '02 Scroll coordination',
    tone: 'sky',
    content: <CoordinationSlide />,
  },
  {
    id: 'threads',
    label: '03 Native host',
    tone: 'paper',
    content: <ThreadsSlide />,
  },
  {
    id: 'ending',
    label: 'Software Mansion',
    tone: 'ending',
    content: <Ending />,
  },
];

/** The hero, with each highlight a sheet that rises over the one before. */
export const Landing = () => {
  useFrameHeight();
  return <SlideStack hero={<Hero />} slides={SLIDES} />;
};
