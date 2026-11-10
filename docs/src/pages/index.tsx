import BrowserOnly from '@docusaurus/BrowserOnly';
import { HtmlClassNameProvider } from '@docusaurus/theme-common';
import Layout from '@theme/Layout';

import { Hero, HeroPoster } from '../landing/Hero';

const DESCRIPTION =
  'Gestures, snapping, and scroll coordination run in native code on iOS and Android, and in JavaScript on the web. Everything you see is plain React Native.';

// Added to the classes Docusaurus puts on <html>, rather than replacing them.
const Home = () => (
  <HtmlClassNameProvider className="landing-page">
    <Layout
      title="Native mechanics. Your surface."
      description={DESCRIPTION}
      noFooter
    >
      <main>
        <BrowserOnly fallback={<HeroPoster />}>{() => <Hero />}</BrowserOnly>
      </main>
    </Layout>
  </HtmlClassNameProvider>
);

export default Home;
