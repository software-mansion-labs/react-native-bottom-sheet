import { memo, type ReactNode } from 'react';
import clsx from 'clsx';
import useIsBrowser from '@docusaurus/useIsBrowser';
import { useColorMode } from '@docusaurus/theme-common';
import type { Props } from '@theme/ColorModeToggle';
import IconLightMode from '@theme/Icon/LightMode';
import IconDarkMode from '@theme/Icon/DarkMode';

import styles from './styles.module.css';

// A two-segment light/dark switch. The highlighted segment follows the
// rendered theme (html[data-theme]) in CSS, so it is right before hydration
// and while following the system setting.
function ColorModeToggle({ className, onChange }: Props): ReactNode {
  const isBrowser = useIsBrowser();
  // The rendered theme, so the pressed state also holds while following the system.
  const { colorMode } = useColorMode();
  const value = isBrowser ? colorMode : undefined;
  return (
    <div
      className={clsx(styles.toggle, className)}
      role="group"
      aria-label="Color mode"
    >
      <button
        type="button"
        className={clsx('clean-btn', styles.segment, styles.light)}
        aria-pressed={value === 'light'}
        aria-label="Light mode"
        title="Light mode"
        disabled={!isBrowser}
        onClick={() => onChange('light')}
      >
        <IconLightMode aria-hidden className={styles.icon} />
      </button>
      <button
        type="button"
        className={clsx('clean-btn', styles.segment, styles.dark)}
        aria-pressed={value === 'dark'}
        aria-label="Dark mode"
        title="Dark mode"
        disabled={!isBrowser}
        onClick={() => onChange('dark')}
      >
        <IconDarkMode aria-hidden className={styles.icon} />
      </button>
    </div>
  );
}

export default memo(ColorModeToggle);
