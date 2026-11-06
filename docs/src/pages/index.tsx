import BrowserOnly from '@docusaurus/BrowserOnly';
import { HtmlClassNameProvider } from '@docusaurus/theme-common';
import Layout from '@theme/Layout';

import { Landing } from '../landing';
import { HeroPoster } from '../landing/Hero';
import landing from '../landing/landing.module.css';

const DESCRIPTION =
  'Gestures, snapping, and scroll coordination run in native code. Everything you see is plain React Native—on iOS, Android, and the web.';

// Added to the classes Docusaurus puts on <html>, rather than replacing them.
const Home = () => (
  <HtmlClassNameProvider className="landing-page">
    <Layout
      title="Native mechanics. Your surface."
      description={DESCRIPTION}
      noFooter
    >
      <main className={landing.landing}>
        <BrowserOnly fallback={<HeroPoster />}>{() => <Landing />}</BrowserOnly>
      </main>
    </Layout>
  </HtmlClassNameProvider>
);

export default Home;
