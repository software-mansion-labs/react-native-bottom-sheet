import BrowserOnly from '@docusaurus/BrowserOnly';
import { PageMetadata } from '@docusaurus/theme-common';
import LayoutProvider from '@theme/Layout/Provider';

import { Ending } from './Ending';
import { useFrameHeight } from './example';
import { Hero, HeroPoster } from './Hero';
import { DetentsSlide } from './Music';
import { ThreadsSlide } from './Portfolio';
import { SlideStack, type Slide } from './SlideStack';
import { NegotiationSlide } from './Video';
import landing from './landing.module.css';

const DESCRIPTION =
  'Gestures, snapping, and scroll negotiation run in native code. Everything you see is plain React Native, on iOS, Android, and the web.';

const Page = () => {
  useFrameHeight();
  const slides: Slide[] = [
    { id: 'detents', tone: 'paper', content: <DetentsSlide /> },
    { id: 'negotiation', tone: 'sky', content: <NegotiationSlide /> },
    { id: 'threads', tone: 'paper', content: <ThreadsSlide /> },
    { id: 'ending', tone: 'ending', content: <Ending /> },
  ];
  return <SlideStack hero={<Hero />} slides={slides} />;
};

export const LandingPage = () => (
  <LayoutProvider>
    <PageMetadata
      title="Native mechanics. Your surface."
      description={DESCRIPTION}
    />
    <main className={landing.landing}>
      <BrowserOnly fallback={<HeroPoster />}>{() => <Page />}</BrowserOnly>
    </main>
  </LayoutProvider>
);
