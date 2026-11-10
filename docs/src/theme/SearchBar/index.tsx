import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import { useHistory, useLocation } from '@docusaurus/router';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {
  fetchIndexesByWorker,
  searchByWorker,
  type SearchResult,
} from '@easyops-cn/docusaurus-search-local/dist/client/client/theme/searchByWorker';
import { highlight } from '@easyops-cn/docusaurus-search-local/dist/client/client/utils/highlight';
import { highlightStemmed } from '@easyops-cn/docusaurus-search-local/dist/client/client/utils/highlightStemmed';
import { getStemmedPositions } from '@easyops-cn/docusaurus-search-local/dist/client/client/utils/getStemmedPositions';
import { Mark } from '@easyops-cn/docusaurus-search-local/dist/client/client/utils/proxiedGenerated';

import styles from './styles.module.css';

// Search after Detour and Pulsar: a field in the header (an icon on narrow
// screens) opens a dialog with the query on top and results grouped by
// page. Wide screens get a centered dialog; narrow ones get it full screen.
// Results come from the local index built by docusaurus-search-local.

const LIMIT = 50;

// Lunr document types from docusaurus-search-local.
const TITLE = 0;
const HEADING = 1;
const DESCRIPTION = 2;
const CONTENT = 4;

type Hit = { key: number; href: string; title: string; excerpt?: string };
type Group = { url: string; href: string; title: string; hits: Hit[] };

function hrefFor({ document, tokens }: SearchResult) {
  let search = '';
  if (Mark && tokens.length > 0) {
    const params = new URLSearchParams();
    for (const token of tokens) params.append('_highlight', token);
    search = `?${params.toString()}`;
  }
  return document.u + search + (document.h ?? '');
}

function groupResults(results: SearchResult[]): Group[] {
  const groups = new Map<string, Group>();
  for (const result of results) {
    const { document, type, page, tokens, metadata } = result;
    const stemmed = (text: string) =>
      highlightStemmed(text, getStemmedPositions(metadata, 't'), tokens, 100);
    let group = groups.get(document.u);
    if (!group) {
      const title =
        type === TITLE ? document.t : page ? page.t : (document.s ?? '');
      group = {
        url: document.u,
        href: hrefFor({ ...result, document: { ...document, h: undefined } }),
        title: highlight(title, tokens),
        hits: [],
      };
      groups.set(document.u, group);
    }
    if (type === TITLE) {
      group.title = stemmed(document.t);
    } else if (type === HEADING) {
      group.hits.push({
        key: document.i,
        href: hrefFor(result),
        title: stemmed(document.t),
      });
    } else {
      group.hits.push({
        key: document.i,
        href: hrefFor(result),
        title: highlight(document.s ?? '', tokens),
        excerpt:
          type === CONTENT || type === DESCRIPTION
            ? stemmed(document.t)
            : undefined,
      });
    }
  }
  return [...groups.values()];
}

function SearchIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      width="16"
      height="16"
      aria-hidden="true"
    >
      <path
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        d="M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15ZM16 16l5 5"
      />
    </svg>
  );
}

function PageIcon() {
  return (
    <svg
      className={styles.pageIcon}
      viewBox="0 0 24 24"
      width="18"
      height="18"
      aria-hidden="true"
    >
      <path
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
        d="M6 2.75h8L19.25 8v13.25H6a1.25 1.25 0 0 1-1.25-1.25V4A1.25 1.25 0 0 1 6 2.75ZM13.75 2.75V8.25h5.5M8.5 12.5h7M8.5 16.5h7"
      />
    </svg>
  );
}

function SearchDialog({ onClose }: { onClose: () => void }) {
  const {
    siteConfig: { baseUrl },
  } = useDocusaurusContext();
  const history = useHistory();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[] | null>(null);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const dialog = dialogRef.current!;
    dialog.showModal();
    const root = document.documentElement;
    const overflow = root.style.overflow;
    root.style.overflow = 'hidden';
    fetchIndexesByWorker(baseUrl, '').catch(() => {});
    return () => {
      root.style.overflow = overflow;
    };
  }, [baseUrl]);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults(null);
      return;
    }
    let stale = false;
    searchByWorker(baseUrl, '', trimmed, LIMIT).then((found) => {
      if (stale) return;
      setResults(found);
      setActive(0);
    });
    return () => {
      stale = true;
    };
  }, [query, baseUrl]);

  const groups = useMemo(() => groupResults(results ?? []), [results]);
  // Every link in reading order, for arrow-key navigation.
  const links = useMemo(
    () => groups.flatMap((g) => [g.href, ...g.hits.map((h) => h.href)]),
    [groups]
  );

  useEffect(() => {
    listRef.current
      ?.querySelector(`[data-index="${active}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  }, [active]);

  const onKeyDown = (event: KeyboardEvent) => {
    if (links.length === 0) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActive((i) => (i + step + links.length) % links.length);
    } else if (event.key === 'Enter' && links[active]) {
      event.preventDefault();
      history.push(links[active]);
      onClose();
    }
  };

  let index = 0;
  const item = (href: string, children: ReactNode, className: string) => {
    const i = index++;
    return (
      <Link
        to={href}
        className={clsx(className, i === active && styles.active)}
        data-index={i}
        onClick={onClose}
        onMouseMove={() => setActive(i)}
      >
        {children}
      </Link>
    );
  };

  const trimmed = query.trim();
  const isDev = process.env.NODE_ENV !== 'production';

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-label="Search"
      onClose={onClose}
      onClick={(event) => {
        if (event.target === dialogRef.current) onClose();
      }}
    >
      <div className={styles.frame}>
        <div className={styles.top}>
          <label className={styles.field}>
            <SearchIcon className={styles.fieldIcon} />
            <input
              ref={inputRef}
              className={styles.input}
              type="search"
              placeholder="Search docs"
              aria-label="Search docs"
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              enterKeyHint="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={onKeyDown}
              autoFocus
            />
            {query && (
              <button
                type="button"
                className={styles.clear}
                aria-label="Clear search"
                onClick={() => {
                  setQuery('');
                  inputRef.current?.focus();
                }}
              >
                <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden>
                  <path
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    d="m6 6 12 12M18 6 6 18"
                  />
                </svg>
              </button>
            )}
          </label>
          <button type="button" className={styles.cancel} onClick={onClose}>
            Cancel
          </button>
        </div>

        <div className={styles.body} ref={listRef}>
          {!trimmed ? (
            <p className={styles.status}>
              {isDev
                ? 'Search works in the production build (docusaurus build).'
                : 'Type to search the docs.'}
            </p>
          ) : results === null ? null : groups.length === 0 ? (
            <p className={styles.status}>
              No results for <strong>{trimmed}</strong>
            </p>
          ) : (
            <>
              <p className={styles.status} aria-live="polite">
                {results.length} {results.length === 1 ? 'result' : 'results'}{' '}
                for <strong>{trimmed}</strong>
              </p>
              <ul className={styles.groups}>
                {groups.map((group) => (
                  <li key={group.url} className={styles.group}>
                    {item(
                      group.href,
                      <>
                        <PageIcon />
                        <span
                          dangerouslySetInnerHTML={{ __html: group.title }}
                        />
                      </>,
                      styles.page
                    )}
                    {group.hits.length > 0 && (
                      <ul className={styles.hits}>
                        {group.hits.map((hit) => (
                          <li key={hit.key}>
                            {item(
                              hit.href,
                              <>
                                <span
                                  className={styles.hitTitle}
                                  dangerouslySetInnerHTML={{
                                    __html: hit.title,
                                  }}
                                />
                                {hit.excerpt && (
                                  <span
                                    className={styles.excerpt}
                                    dangerouslySetInnerHTML={{
                                      __html: hit.excerpt,
                                    }}
                                  />
                                )}
                              </>,
                              styles.hit
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </dialog>
  );
}

type MarkInstance = {
  unmark: () => void;
  mark: (keywords: string[], options?: object) => void;
};

// Marks the search terms on the page a result opened (`?_highlight=`), as
// the plugin's own search bar does.
function useHighlightTerms() {
  const location = useLocation();
  useEffect(() => {
    if (!Mark) return;
    const keywords = new URLSearchParams(location.search).getAll('_highlight');
    // Code blocks re-render after effects run, so mark after a macro task.
    const timer = setTimeout(() => {
      const root = document.querySelector('article');
      if (!root) return;
      const MarkClass = Mark as new (root: Element) => MarkInstance;
      const mark = new MarkClass(root);
      mark.unmark();
      if (keywords.length > 0) {
        mark.mark(keywords, { exclude: ['.theme-doc-toc-mobile > button'] });
      }
    }, 0);
    return () => clearTimeout(timer);
  }, [location.search, location.pathname]);
}

export default function SearchBar(): ReactNode {
  useHighlightTerms();
  const [open, setOpen] = useState(false);
  const [isMac, setIsMac] = useState(true);
  const triggerRef = useRef<HTMLButtonElement>(null);
  // The dialog unmounts on close, so hand focus back to the trigger.
  const close = useCallback(() => {
    setOpen(false);
    triggerRef.current?.focus({ preventScroll: true });
  }, []);

  useEffect(() => {
    setIsMac(/Mac|iPhone|iPad/.test(navigator.platform));
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen((value) => !value);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={styles.trigger}
        aria-label="Search"
        aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <SearchIcon className={styles.triggerIcon} />
        <span className={styles.triggerLabel}>Search</span>
        <span className={styles.keys} aria-hidden="true">
          <kbd>{isMac ? '⌘' : 'Ctrl'}</kbd>
          <kbd>K</kbd>
        </span>
      </button>
      {open && createPortal(<SearchDialog onClose={close} />, document.body)}
    </>
  );
}
