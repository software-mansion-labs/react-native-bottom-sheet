import type { ReactNode } from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';

import styles from './styles.module.css';

export const Lead = ({ children }: { children: ReactNode }) => (
  <p className={styles.lead}>{children}</p>
);

const START = [
  {
    to: '/docs/getting-started',
    title: 'Getting started',
    body: 'Install the package and wrap your app in the provider.',
  },
  {
    to: '/docs/inline-sheets',
    title: 'Inline sheets',
    body: 'A sheet that lives inside your screen layout.',
  },
  {
    to: '/docs/modal-sheets',
    title: 'Modal sheets',
    body: 'A sheet above everything else, with an optional scrim.',
  },
  {
    to: '/docs/how-it-compares',
    title: 'How it compares',
    body: 'Documented behavior next to other bottom-sheet libraries.',
  },
];

export const StartHere = () => (
  <nav className={styles.cards} aria-label="Start here">
    {START.map((item) => (
      <Link key={item.to} to={item.to} className={`${styles.card} card-link`}>
        <span className={styles.cardTitle}>
          {item.title}
          <svg
            className={styles.arrow}
            viewBox="0 0 16 16"
            width="16"
            height="16"
            aria-hidden="true"
          >
            <path
              d="M3 8h9.5M8.5 4l4 4-4 4"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
            />
          </svg>
        </span>
        <span className={styles.cardBody}>{item.body}</span>
      </Link>
    ))}
  </nav>
);

export const Highlights = ({ children }: { children: ReactNode }) => (
  <div className={styles.highlights}>{children}</div>
);

export const Credits = () => {
  const gobi = useBaseUrl('/img/gobi.png');
  return (
    <div className={styles.credits}>
      <a
        className={`${styles.credit} ${styles.swm} card-link`}
        href="https://swmansion.com/contact/projects?utm_source=react-native-bottom-sheet&utm_medium=docs"
      >
        <span className={styles.creditLabel}>Built by</span>
        <img
          src="https://logo.swmansion.com/logo?color=white&variant=desktop&width=152&tag=react-native-bottom-sheet-docs"
          alt="Software Mansion"
          width="152"
          height="38"
          loading="lazy"
        />
        <span className={styles.creditBody}>
          Core React Native contributors since 2012. Need help with your app?
          Hire us.
        </span>
      </a>
      <a
        className={`${styles.credit} ${styles.gobi} card-link`}
        href="https://www.gobimaps.com"
      >
        <span className={styles.creditLabel}>Sponsored by</span>
        <img
          src={gobi}
          alt="Gobi Maps"
          width="115"
          height="38"
          loading="lazy"
        />
        <span className={styles.creditBody}>
          The best of your city, all in one map.
        </span>
      </a>
    </div>
  );
};
