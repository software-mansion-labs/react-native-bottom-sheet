import { version, type ReactNode } from 'react';
import clsx from 'clsx';
import { ThemeClassNames, useThemeConfig } from '@docusaurus/theme-common';
import {
  useNavbarMobileSidebar,
  useNavbarSecondaryMenu,
} from '@docusaurus/theme-common/internal';
import NavbarItem, { type Props as NavbarItemProps } from '@theme/NavbarItem';
import SearchBar from '@theme/SearchBar';
import type { Props } from '@theme/Navbar/MobileSidebar/Layout';

import styles from './styles.module.css';

// The phone menu after Reanimated's docs: the color-mode switch and close
// button on top, then search, the docs tree, and the top-nav links at the
// bottom instead of behind a "Back to main menu" step.

function inertProps(inert: boolean) {
  const isBeforeReact19 = parseInt(version.split('.')[0]!, 10) < 19;
  return isBeforeReact19 ? { inert: inert ? '' : undefined } : { inert };
}

function Panel({ children, inert }: { children: ReactNode; inert: boolean }) {
  return (
    <div
      className={clsx(
        ThemeClassNames.layout.navbar.mobileSidebar.panel,
        'navbar-sidebar__item menu'
      )}
      {...(inertProps(inert) as object)}
    >
      {children}
    </div>
  );
}

function FooterLinks() {
  const mobileSidebar = useNavbarMobileSidebar();
  const items = useThemeConfig().navbar.items as Record<string, unknown>[];
  return (
    <ul className={styles.links}>
      {items
        .filter((item) => item.type !== 'search')
        // Icon-only items (GitHub) show their accessible name as text here.
        .map(({ html, className: _className, ...item }, i) => {
          const props = {
            ...item,
            label: item.label ?? (html ? item['aria-label'] : undefined),
            mobile: true,
            onClick: () => mobileSidebar.toggle(),
          } as NavbarItemProps;
          return <NavbarItem {...props} key={i} />;
        })}
    </ul>
  );
}

export default function NavbarMobileSidebarLayout({
  header,
  primaryMenu,
  secondaryMenu,
}: Props): ReactNode {
  const { shown: secondaryMenuShown } = useNavbarSecondaryMenu();
  return (
    <div
      className={clsx(
        ThemeClassNames.layout.navbar.mobileSidebar.container,
        'navbar-sidebar'
      )}
    >
      {header}
      <div className={styles.search}>
        <SearchBar />
      </div>
      <div
        className={clsx('navbar-sidebar__items', {
          'navbar-sidebar__items--show-secondary': secondaryMenuShown,
        })}
      >
        <Panel inert={secondaryMenuShown}>{primaryMenu}</Panel>
        <Panel inert={!secondaryMenuShown}>{secondaryMenu}</Panel>
      </div>
      {secondaryMenuShown && <FooterLinks />}
    </div>
  );
}
