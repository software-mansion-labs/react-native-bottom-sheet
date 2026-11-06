import Link from '@docusaurus/Link';
import { useColorMode } from '@docusaurus/theme-common';

import { Icon } from './pieces';
import styles from './Hero.module.css';

const ThemeToggle = () => {
  const { colorMode, setColorMode } = useColorMode();
  const label = `Switch between dark and light mode (currently ${colorMode} mode)`;
  return (
    <button
      className={styles.themeToggle}
      type="button"
      title={label}
      aria-label={label}
      aria-live="polite"
      onClick={() => setColorMode(colorMode === 'dark' ? 'light' : 'dark')}
    >
      <span data-active={colorMode === 'light'}>
        <Icon
          d="M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"
          size={23}
        />
      </span>
      <span data-active={colorMode === 'dark'}>
        <Icon d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" size={23} />
      </span>
    </button>
  );
};

/** The same header as the Reanimated and Gesture Handler docs. */
export const Header = () => (
  <nav className={styles.navbar}>
    <Link
      className={styles.brand}
      to="/"
      aria-label="React Native Bottom Sheet"
    >
      <span className={styles.wordmark}>
        <span>React Native</span>
        <b>Bottom Sheet</b>
      </span>
    </Link>
    <div className={styles.navbarRight}>
      <ThemeToggle />
      <a
        className={styles.navbarGithub}
        href="https://github.com/software-mansion-labs/react-native-bottom-sheet"
        aria-label="GitHub repository"
      >
        <svg
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden
        >
          <path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2.15c-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.68-1.28-1.68-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.8 1.19 1.83 1.19 3.09 0 4.42-2.7 5.4-5.26 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5z" />
        </svg>
      </a>
    </div>
  </nav>
);
