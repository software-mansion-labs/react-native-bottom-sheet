import type { ReactNode } from 'react';
import { useNavbarMobileSidebar } from '@docusaurus/theme-common/internal';
import { translate } from '@docusaurus/Translate';
import IconMenu from '@theme/Icon/Menu';

// The menu button turns into a close button while the full-screen menu is
// open, as in Pulsar and Detour; the header stays in place above the menu.
export default function MobileSidebarToggle(): ReactNode {
  const { toggle, shown } = useNavbarMobileSidebar();
  return (
    <button
      onClick={toggle}
      aria-label={
        shown
          ? translate({
              id: 'theme.docs.sidebar.closeSidebarButtonAriaLabel',
              message: 'Close navigation bar',
              description: 'The ARIA label for close button of mobile sidebar',
            })
          : translate({
              id: 'theme.docs.sidebar.toggleSidebarButtonAriaLabel',
              message: 'Toggle navigation bar',
              description:
                'The ARIA label for hamburger menu button of mobile navigation',
            })
      }
      aria-expanded={shown}
      className="navbar__toggle clean-btn"
      type="button"
    >
      {shown ? (
        <svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true">
          <path
            stroke="currentColor"
            strokeLinecap="round"
            strokeWidth="2"
            d="m8 8 14 14M22 8 8 22"
          />
        </svg>
      ) : (
        <IconMenu />
      )}
    </button>
  );
}
