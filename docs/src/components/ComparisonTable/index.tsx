import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import clsx from 'clsx';

import styles from './styles.module.css';

export type Status = 'yes' | 'partial' | 'no';

export type Cell = Status | { status: Status; note: ReactNode };

export type Product = {
  key: string;
  name: ReactNode;
  href?: string;
  /** Highlights the column, for this library. */
  featured?: boolean;
};

export type Row = {
  feature: ReactNode;
  cells: Record<string, Cell>;
};

export type Group = {
  title: string;
  rows: Row[];
};

const STATUS_LABEL: Record<Status, string> = {
  yes: 'First-class',
  partial: 'Supported with caveats',
  no: 'No first-class support',
};

function StatusIcon({ status }: { status: Status }) {
  return (
    <span className={clsx(styles.icon, styles[status])} aria-hidden="true">
      <svg viewBox="0 0 16 16" width="16" height="16">
        {status === 'yes' && (
          <path
            d="M4.5 8.25 7 10.75l4.5-5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
        {status === 'partial' && (
          <path
            d="M5 8h6"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        )}
        {status === 'no' && (
          <path
            d="m5.5 5.5 5 5m0-5-5 5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        )}
      </svg>
    </span>
  );
}

function StatusCell({ cell }: { cell: Cell }) {
  const { status, note } =
    typeof cell === 'string' ? { status: cell, note: undefined } : cell;
  return (
    <div className={styles.status}>
      <StatusIcon status={status} />
      <span className={styles.srOnly}>{STATUS_LABEL[status]}</span>
      {note != null && <span className={styles.note}>{note}</span>}
    </div>
  );
}

function Legend() {
  return (
    <ul className={styles.legend} aria-label="Legend">
      {(Object.keys(STATUS_LABEL) as Status[]).map((status) => (
        <li key={status}>
          <StatusIcon status={status} />
          {STATUS_LABEL[status]}
        </li>
      ))}
    </ul>
  );
}

function ScrollButton({
  direction,
  disabled,
  onClick,
}: {
  direction: 'left' | 'right';
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={styles.scrollButton}
      disabled={disabled}
      onClick={onClick}
      aria-label={direction === 'left' ? 'Scroll left' : 'Scroll right'}
    >
      <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
        <path
          d={
            direction === 'left'
              ? 'M10 3.5 5.5 8l4.5 4.5'
              : 'M6 3.5 10.5 8 6 12.5'
          }
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </button>
  );
}

type Edges = { overflowing: boolean; atStart: boolean; atEnd: boolean };

/** Tracks whether the table overflows its frame and which edges it touches. */
function useScrollEdges(ref: React.RefObject<HTMLDivElement | null>) {
  const [edges, setEdges] = useState<Edges>({
    overflowing: false,
    atStart: true,
    atEnd: true,
  });

  const update = useCallback(() => {
    const el = ref.current;
    if (el == null) return;
    const max = el.scrollWidth - el.clientWidth;
    // A sub-pixel tolerance, since zoomed pages report fractional offsets.
    const next = {
      overflowing: max > 1,
      atStart: el.scrollLeft <= 1,
      atEnd: el.scrollLeft >= max - 1,
    };
    setEdges((prev) =>
      prev.overflowing === next.overflowing &&
      prev.atStart === next.atStart &&
      prev.atEnd === next.atEnd
        ? prev
        : next
    );
  }, [ref]);

  useEffect(() => {
    const el = ref.current;
    if (el == null) return;
    update();
    el.addEventListener('scroll', update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    if (el.firstElementChild) observer.observe(el.firstElementChild);
    return () => {
      el.removeEventListener('scroll', update);
      observer.disconnect();
    };
  }, [ref, update]);

  return edges;
}

export default function ComparisonTable({
  products,
  groups,
  caption,
}: {
  products: Product[];
  groups: Group[];
  caption: string;
}) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const { overflowing, atStart, atEnd } = useScrollEdges(scrollerRef);

  const scrollByColumns = (direction: 1 | -1) => {
    const el = scrollerRef.current;
    if (el == null) return;
    const column = el.querySelector<HTMLElement>('thead th:nth-child(2)');
    const sticky = el.querySelector<HTMLElement>('thead th:first-child');
    const columnWidth = column?.offsetWidth ?? 160;
    // Page by whatever fits beside the sticky feature column, at least one.
    const visible = el.clientWidth - (sticky?.offsetWidth ?? 0);
    const columns = Math.max(1, Math.floor(visible / columnWidth));
    const reduceMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)'
    ).matches;
    el.scrollBy({
      left: direction * columns * columnWidth,
      behavior: reduceMotion ? 'auto' : 'smooth',
    });
  };

  return (
    <figure className={styles.root}>
      <div className={styles.toolbar}>
        <Legend />
        <div
          className={styles.scrollButtons}
          data-visible={overflowing || undefined}
          aria-hidden={!overflowing || undefined}
        >
          <ScrollButton
            direction="left"
            disabled={!overflowing || atStart}
            onClick={() => scrollByColumns(-1)}
          />
          <ScrollButton
            direction="right"
            disabled={!overflowing || atEnd}
            onClick={() => scrollByColumns(1)}
          />
        </div>
      </div>
      <div
        className={styles.frame}
        data-scrolled={(overflowing && !atStart) || undefined}
        data-more={(overflowing && !atEnd) || undefined}
      >
        <div
          ref={scrollerRef}
          className={styles.scroller}
          // Focusable so keyboard users can scroll it with the arrow keys.
          tabIndex={overflowing ? 0 : undefined}
          role="region"
          aria-label={caption}
        >
          <table className={styles.table}>
            <caption className={styles.srOnly}>{caption}</caption>
            <thead>
              <tr>
                <th scope="col" className={styles.featureHead}>
                  Feature
                </th>
                {products.map((product) => (
                  <th
                    key={product.key}
                    scope="col"
                    className={clsx(
                      styles.productHead,
                      product.featured && styles.featured
                    )}
                  >
                    {product.href ? (
                      <a href={product.href}>{product.name}</a>
                    ) : (
                      product.name
                    )}
                  </th>
                ))}
              </tr>
            </thead>
            {groups.map((group) => (
              <tbody key={group.title}>
                <tr className={styles.groupRow}>
                  <th
                    scope="colgroup"
                    colSpan={products.length + 1}
                    className={styles.groupHead}
                  >
                    <span>{group.title}</span>
                  </th>
                </tr>
                {group.rows.map((row, index) => (
                  <tr key={index}>
                    <th scope="row" className={styles.feature}>
                      {row.feature}
                    </th>
                    {products.map((product) => (
                      <td
                        key={product.key}
                        className={clsx(
                          styles.cell,
                          product.featured && styles.featured
                        )}
                      >
                        <StatusCell cell={row.cells[product.key] ?? 'no'} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            ))}
          </table>
        </div>
      </div>
    </figure>
  );
}
