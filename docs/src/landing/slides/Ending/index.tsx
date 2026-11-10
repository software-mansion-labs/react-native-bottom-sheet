import type { CSSProperties } from 'react';

import { Reveal } from '../../shared/pieces';
import { LogoWheel } from './LogoWheel';
import { SOCIALS } from './socials';
import styles from './styles.module.css';
import SwmLogo from './swm.svg';

// Each part rises a beat after the one above it, as on the Enriched docs.
const after = (ms: number): CSSProperties => ({ transitionDelay: `${ms}ms` });

/** "We are Software Mansion", as at the bottom of the Enriched docs. */
export const Ending = () => (
  <div className={styles.ending}>
    <div className={styles.main}>
      <div className={styles.content}>
        <Reveal>
          <h2 className={styles.title}>We are Software Mansion</h2>
        </Reveal>
        <Reveal style={after(120)}>
          <div className={styles.description}>
            <p>
              We create award-winning mobile apps, real-time multimedia
              products, and AI solutions. Our open-source libraries — like React
              Native Reanimated, Gesture Handler, and Screens — are used by
              hundreds of thousands of developers worldwide.
            </p>
            <p>
              Planning to add bottom sheets to your app? Get in touch — we can
              help at every stage of the journey.
            </p>
          </div>
        </Reveal>
        <Reveal className={styles.action} style={after(240)}>
          <a className={styles.contact} href="https://swmansion.com/contact/">
            Contact us
          </a>
        </Reveal>
      </div>
      <Reveal className={styles.wheelReveal} style={after(360)}>
        <LogoWheel />
      </Reveal>
      <div className={styles.balance} />
    </div>
    <Reveal style={after(360)}>
      <footer className={styles.legal}>
        <div className={styles.legalStart}>
          <a
            className={styles.swm}
            href="https://swmansion.com/"
            aria-label="Software Mansion"
          >
            <SwmLogo aria-hidden />
          </a>
          <a
            className={styles.privacy}
            href="https://swmansion.com/privacy/policy/"
          >
            Privacy Policy
          </a>
        </div>
        <div className={styles.socials}>
          {SOCIALS.map(({ label, href, path, evenOdd }) => (
            <a
              key={label}
              className={styles.social}
              href={href}
              aria-label={label}
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden
              >
                <path d={path} fillRule={evenOdd ? 'evenodd' : undefined} />
              </svg>
            </a>
          ))}
        </div>
      </footer>
    </Reveal>
  </div>
);
