import { useState, type ReactNode } from 'react';
import Link from '@docusaurus/Link';
import clsx from 'clsx';

import { Arrow } from './pieces';
import landing from '../landing.module.css';
import styles from './example.module.css';

const KEYWORDS = /^(const|return|import|from|true|false)$/;
const TOKENS =
  /(\/\/.*|\{\/\*.*?\*\/\}|'[^']*'|<\/?[A-Z][\w.]*|\/>|\b\d+(?:\.\d+)?\b|\b[a-z]+\b)/g;

/** Just enough JSX highlighting for the snippets on this page. */
const highlight = (code: string) =>
  code.split(TOKENS).map((part, i) => {
    if (i % 2 === 0) return part;
    let kind: string | undefined;
    if (part.startsWith('//') || part.startsWith('{/*')) kind = styles.tC;
    else if (part.startsWith("'")) kind = styles.tS;
    else if (part.startsWith('<') || part === '/>') kind = styles.tJ;
    else if (/^\d/.test(part)) kind = styles.tN;
    else if (KEYWORDS.test(part)) kind = styles.tK;
    return kind ? (
      <span key={i} className={kind}>
        {part}
      </span>
    ) : (
      part
    );
  });

export const Code = ({ children }: { children: string }) => (
  <div className={styles.code}>
    <pre key={children}>{highlight(children)}</pre>
  </div>
);

/**
 * Every variant in one slot, as tall as the tallest. Switching crossfades them,
 * and nothing around the slot moves.
 */
export const Swap = ({
  active,
  items,
}: {
  active: string;
  items: [string, ReactNode][];
}) => (
  <div className={styles.swap}>
    {items.map(([id, node]) => (
      <div
        key={id}
        className={styles.swapItem}
        data-on={id === active}
        aria-hidden={id !== active}
      >
        {node}
      </div>
    ))}
  </div>
);

export const CodeSwap = ({
  active,
  items,
}: {
  active: string;
  items: [string, string][];
}) => (
  <div className={styles.code}>
    <Swap
      active={active}
      items={items.map(([id, code]) => [
        id,
        <pre key={id}>{highlight(code)}</pre>,
      ])}
    />
  </div>
);

export const DocsButton = ({ page }: { page: string }) => (
  <Link className={clsx(styles.docsButton, landing.press)} to={`/${page}`}>
    Check docs <Arrow />
  </Link>
);

/**
 * Code beside an example, and the way into the docs. The code is always shown on
 * wide screens; on small ones it sits behind a toggle, so the options and the
 * example fit on one screen.
 */
export const CodeBlock = ({
  docs,
  children,
}: {
  docs: string;
  children: ReactNode;
}) => {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.codeBlock} data-open={open}>
      <div className={styles.codeActions}>
        <button
          className={clsx(styles.codeToggle, landing.press)}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden
          >
            <path d="M5.5 4.5 2 8l3.5 3.5M10.5 4.5 14 8l-3.5 3.5" />
          </svg>
          {open ? 'Hide code' : 'Show code'}
        </button>
        <DocsButton page={docs} />
      </div>
      <div className={styles.codeCollapse}>
        <div className={styles.codeCollapseInner}>{children}</div>
      </div>
    </div>
  );
};

export const Chip = ({
  active,
  mono,
  onClick,
  children,
}: {
  active: boolean;
  mono?: boolean;
  onClick: () => void;
  children: ReactNode;
}) => (
  <button
    className={clsx(styles.chip, landing.press, mono && styles.chipMono)}
    aria-pressed={active}
    onClick={onClick}
  >
    {children}
  </button>
);
