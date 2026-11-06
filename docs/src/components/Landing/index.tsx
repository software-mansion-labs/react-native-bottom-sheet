import BrowserOnly from '@docusaurus/BrowserOnly';
import { PageMetadata } from '@docusaurus/theme-common';
import LayoutProvider from '@theme/Layout/Provider';

import { Hero, HeroPoster } from './Hero';
import landing from './landing.module.css';

const DESCRIPTION =
  'Gestures, snapping, and scroll negotiation run in native code. Everything you see is plain React Native, on iOS, Android, and the web.';

export const LandingPage = () => (
  <LayoutProvider>
    <PageMetadata title="Native mechanics. Your surface." description={DESCRIPTION} />
    <main className={landing.landing}>
      <BrowserOnly fallback={<HeroPoster />}>{() => <Hero />}</BrowserOnly>
    </main>
  </LayoutProvider>
);
