/**
 * Ejected from @docusaurus/theme-classic so the right column renders on every
 * doc page: the table of contents when the page has one, and the Hire us card
 * always.
 */
import type { ReactNode } from 'react';
import clsx from 'clsx';
import { useWindowSize } from '@docusaurus/theme-common';
import { useDoc } from '@docusaurus/plugin-content-docs/client';
import DocItemPaginator from '@theme/DocItem/Paginator';
import DocVersionBanner from '@theme/DocVersionBanner';
import DocVersionBadge from '@theme/DocVersionBadge';
import DocItemFooter from '@theme/DocItem/Footer';
import DocItemTOCMobile from '@theme/DocItem/TOC/Mobile';
import DocItemTOCDesktop from '@theme/DocItem/TOC/Desktop';
import DocItemContent from '@theme/DocItem/Content';
import DocBreadcrumbs from '@theme/DocBreadcrumbs';
import ContentVisibility from '@theme/ContentVisibility';
import type { Props } from '@theme/DocItem/Layout';

import HireUs from '../../../components/HireUs';
import styles from './styles.module.css';

export default function DocItemLayout({ children }: Props): ReactNode {
  const { frontMatter, toc, metadata } = useDoc();
  const windowSize = useWindowSize();
  const hasTOC = !frontMatter.hide_table_of_contents && toc.length > 0;
  const isDesktop = windowSize === 'desktop' || windowSize === 'ssr';

  return (
    <div className="row">
      <div className={clsx('col', styles.docItemCol)}>
        <ContentVisibility metadata={metadata} />
        <DocVersionBanner />
        <div className={styles.docItemContainer}>
          <article>
            <DocBreadcrumbs />
            <DocVersionBadge />
            {hasTOC && <DocItemTOCMobile />}
            <DocItemContent>{children}</DocItemContent>
            <DocItemFooter />
          </article>
          <DocItemPaginator />
        </div>
      </div>
      {isDesktop && (
        <div className={clsx('col col--3', styles.aside)}>
          <div className={styles.sticky}>
            {hasTOC && (
              <nav className={styles.toc} aria-labelledby="on-this-page">
                <h2 id="on-this-page" className={styles.tocTitle}>
                  On this page
                </h2>
                <DocItemTOCDesktop />
              </nav>
            )}
            <HireUs />
          </div>
        </div>
      )}
    </div>
  );
}
