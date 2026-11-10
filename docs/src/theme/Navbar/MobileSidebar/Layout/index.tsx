import { version, type ReactNode } from 'react';
import clsx from 'clsx';
import { ThemeClassNames, useThemeConfig } from '@docusaurus/theme-common';
import {
  useNavbarMobileSidebar,
  useNavbarSecondaryMenu,
} from '@docusaurus/theme-common/internal';
import NavbarItem, { type Props as NavbarItemProps } from '@theme/NavbarItem';
import NavbarColorModeToggle from '@theme/Navbar/ColorModeToggle';
import type { Props } from '@theme/Navbar/MobileSidebar/Layout';

import styles from './styles.module.css';

// The phone menu after Pulsar and Detour: a full-width page under the
// header (whose menu button turns into close), with the docs tree and a
// bottom row holding GitHub and the color-mode switch. That row replaces the
// "Back to main menu" step; the Docs link is left out, as in the docs
// header. On pages without a docs tree the menu lists only GitHub.

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

// The navbar items as menu links, without search and the Docs link (the
// docs tree covers it). Icon-only items (GitHub) keep the header's icon and add their accessible name as
// text next to it.
function NavLinks({ className }: { className: string }) {
  const mobileSidebar = useNavbarMobileSidebar();
  const items = useThemeConfig().navbar.items as Record<string, unknown>[];
  return (
    <ul className={className}>
      {items
        .filter((item) => item.type !== 'search' && item.type !== 'docSidebar')
        .map(({ className: _className, ...item }, i) => {
          const props = {
            ...item,
            ...(typeof item.html === 'string' && {
              html: `<span class="${styles.iconLink}">${item.html}<span>${String(item['aria-label'])}</span></span>`,
            }),
            mobile: true,
            onClick: () => mobileSidebar.toggle(),
          } as NavbarItemProps;
          return <NavbarItem {...props} key={i} />;
        })}
    </ul>
  );
}

export default function NavbarMobileSidebarLayout({
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
      <div
        className={clsx('navbar-sidebar__items', {
          'navbar-sidebar__items--show-secondary': secondaryMenuShown,
        })}
      >
        <Panel inert={secondaryMenuShown}>
          {/* Outside the docs (the landing page) the menu holds only GitHub
              and the color-mode switch. */}
          <NavLinks className="menu__list" />
        </Panel>
        <Panel inert={!secondaryMenuShown}>{secondaryMenu}</Panel>
      </div>
      <div className={styles.footer}>
        {secondaryMenuShown && <NavLinks className={styles.links} />}
        <NavbarColorModeToggle className={styles.colorMode} />
      </div>
    </div>
  );
}
