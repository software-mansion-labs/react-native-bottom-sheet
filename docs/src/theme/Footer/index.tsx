import { SOCIALS } from './socials';
import styles from './styles.module.css';
import SwmLogo from './swm.svg';

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <div className={styles.brand}>
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
      </div>
    </footer>
  );
}
