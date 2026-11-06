import BrowserOnly from '@docusaurus/BrowserOnly';
import { PageMetadata } from '@docusaurus/theme-common';
import LayoutProvider from '@theme/Layout/Provider';

import { Ending } from './slides/Ending';
import { Hero, HeroPoster } from './slides/Hero';
import { SlideStack, type Slide } from './SlideStack';
import landing from './landing.module.css';

const DESCRIPTION =
  'Gestures, snapping, and scroll negotiation run in native code. Everything you see is plain React Native, on iOS, Android, and the web.';

const SLIDES: Slide[] = [
  {
    id: 'ending',
    label: 'Software Mansion',
    tone: 'ending',
    content: <Ending />,
  },
];

export const LandingPage = () => (
  <LayoutProvider>
    <PageMetadata
      title="Native mechanics. Your surface."
      description={DESCRIPTION}
    />
    <main className={landing.landing}>
      <BrowserOnly fallback={<HeroPoster />}>
        {() => <SlideStack hero={<Hero />} slides={SLIDES} />}
      </BrowserOnly>
    </main>
  </LayoutProvider>
);
