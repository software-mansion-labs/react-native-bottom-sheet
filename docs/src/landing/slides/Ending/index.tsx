import Footer from '@theme/Footer';

import { LogoWheel } from './LogoWheel';
import styles from './styles.module.css';

/** "We are Software Mansion", as at the bottom of the Enriched docs. */
export const Ending = () => (
  <div className={styles.ending}>
    <div className={styles.main}>
      <div className={styles.content}>
        <h2 className={styles.title}>We are Software Mansion</h2>
        <div className={styles.description}>
          <p>
            We create award-winning mobile apps, real-time multimedia
            products, and AI solutions. Our open-source libraries—like React
            Native Reanimated, Gesture Handler, and Screens—are used by
            hundreds of thousands of developers worldwide.
          </p>
          <p>
            Planning to add bottom sheets to your app? Get in touch—we can
            help at every stage of the journey.
          </p>
        </div>
        <a className={styles.contact} href="https://swmansion.com/contact/">
          Contact us
        </a>
      </div>
      <div className={styles.logos}>
        <LogoWheel />
      </div>
      <div className={styles.balance} />
    </div>
    <Footer />
  </div>
);
