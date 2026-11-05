import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ComponentType,
  type CSSProperties,
  type ReactNode,
} from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import CodeBlock from '@theme/CodeBlock';
import clsx from 'clsx';

import { Stage } from './stage';
import styles from './styles.module.css';

export * from './controls';
export * from './stage';

type Tab = 'preview' | 'code';

/** Below this panel width, the controls move under the stage. */
const TWO_COLUMN_MIN_WIDTH = 620;
/** The tabs row, which the stage or the code sits below when it's full width. */
const HEADER_HEIGHT = 56;

const ShellContext = createContext<{
  tab: Tab;
  setTab: (tab: Tab) => void;
  reset: () => void;
  height: number;
} | null>(null);

const CopyButton = ({ code }: { code: string }) => {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <button
      type="button"
      className={styles.iconButton}
      aria-label={copied ? 'Copied' : 'Copy code'}
      onClick={copy}
    >
      {copied ? (
        <svg viewBox="0 0 24 24" className={styles.icon} aria-hidden>
          <path d="m5 12.5 4.5 4.5L19 7.5" />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" className={styles.icon} aria-hidden>
          <rect x="9" y="9" width="11" height="11" rx="2" />
          <path d="M15 5.5V5a1 1 0 0 0-1-1H6a2 2 0 0 0-2 2v8a1 1 0 0 0 1 1h.5" />
        </svg>
      )}
    </button>
  );
};

const Tabs = () => {
  const { tab, setTab } = useContext(ShellContext)!;
  const listRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState<{ x: number; width: number }>();

  useLayoutEffect(() => {
    const list = listRef.current!;
    const update = () => {
      const active = list.querySelector<HTMLElement>('[aria-selected="true"]')!;
      setIndicator({ x: active.offsetLeft, width: active.offsetWidth });
    };
    update();
    // Tab widths change when the docs' font loads.
    const observer = new ResizeObserver(update);
    observer.observe(list);
    return () => observer.disconnect();
  }, [tab]);

  return (
    <div ref={listRef} role="tablist" className={styles.tabs}>
      {(['preview', 'code'] as const).map((value) => (
        <button
          key={value}
          type="button"
          role="tab"
          aria-selected={tab === value}
          className={clsx(styles.tab, tab === value && styles.tabActive)}
          onClick={() => setTab(value)}
        >
          {value === 'preview' ? 'Preview' : 'Code'}
        </button>
      ))}
      {indicator !== undefined && (
        <span
          className={styles.tabIndicator}
          style={{
            width: indicator.width,
            transform: `translateX(${indicator.x}px)`,
          }}
        />
      )}
    </div>
  );
};

const useElementSize = () => {
  const ref = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  useLayoutEffect(() => {
    const element = ref.current!;
    const update = () =>
      setSize({ width: element.offsetWidth, height: element.offsetHeight });
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return [ref, size] as const;
};

interface ExampleProps {
  /** What plays on the stage: the content behind the sheets, and the sheets. */
  stage: ReactNode;
  /** Optional controls beside or below the stage, from the shared controls. */
  controls?: ReactNode;
  /** Source shown in the Code tab. Derive it from state to keep it live. */
  code: string;
}

/** The look of every example. Render it from an `interactiveExample` body. */
export const Example = ({ stage, controls, code }: ExampleProps) => {
  const { tab, reset, height } = useContext(ShellContext)!;
  const [panelRef, { width }] = useElementSize();
  const [controlsRef, { height: controlsHeight }] = useElementSize();
  const [codeRef, { height: codeHeight }] = useElementSize();
  const [animated, setAnimated] = useState(false);

  useEffect(() => {
    const frame = requestAnimationFrame(() => setAnimated(true));
    return () => cancelAnimationFrame(frame);
  }, []);

  const wide = width >= TWO_COLUMN_MIN_WIDTH;
  // The stage keeps this height on the Code tab too, so sheets don't move.
  const stageHeight = wide ? Math.max(height, controlsHeight) : height;
  const previewHeight = wide
    ? stageHeight
    : HEADER_HEIGHT + stageHeight + controlsHeight;

  return (
    <div className={styles.example}>
      <div
        ref={panelRef}
        className={clsx(
          styles.panel,
          wide && styles.wide,
          tab === 'code' && styles.showCode,
          animated && styles.animated
        )}
        style={
          {
            'height': tab === 'preview' ? previewHeight : codeHeight,
            '--stage-bottom': `${HEADER_HEIGHT + stageHeight}px`,
          } as CSSProperties
        }
      >
        <div className={styles.stageSlot} inert={tab !== 'preview'}>
          <Stage height={stageHeight}>{stage}</Stage>
        </div>
        {/* Grows over the stage to show the code, then shrinks back. */}
        <div className={styles.column}>
          <div
            ref={controlsRef}
            className={styles.controlsPane}
            inert={tab !== 'preview'}
          >
            {controls !== undefined && (
              <div className={styles.controls}>{controls}</div>
            )}
          </div>
          <div ref={codeRef} className={styles.codePane} inert={tab !== 'code'}>
            <CodeBlock language="tsx">{code}</CodeBlock>
          </div>
          <button
            type="button"
            className={clsx(styles.iconButton, styles.reset)}
            aria-label="Reset example"
            inert={tab !== 'preview'}
            onClick={reset}
          >
            <svg viewBox="0 0 24 24" className={styles.icon} aria-hidden>
              <path d="M4 12a8 8 0 0 1 13.7-5.6L20 8.5" />
              <path d="M20 4v4.5h-4.5" />
              <path d="M20 12a8 8 0 0 1-13.7 5.6L4 15.5" />
              <path d="M4 20v-4.5h4.5" />
            </svg>
          </button>
        </div>
        <div className={styles.header}>
          <Tabs />
          <CopyButton code={code} />
        </div>
      </div>
    </div>
  );
};

/**
 * Turns a component that renders `Example` into one you can use in MDX: it runs
 * in the browser only, and reset remounts it, so its state needs no reset logic.
 */
export const interactiveExample = (
  Body: ComponentType,
  { height = 400 }: { height?: number } = {}
) => {
  const Shell = () => {
    const [tab, setTab] = useState<Tab>('preview');
    const [resetKey, setResetKey] = useState(0);

    return (
      <ShellContext.Provider
        value={{
          tab,
          setTab,
          height,
          reset: () => setResetKey((key) => key + 1),
        }}
      >
        <Body key={resetKey} />
      </ShellContext.Provider>
    );
  };

  return () => (
    <BrowserOnly
      fallback={
        <div
          className={styles.placeholder}
          style={{ '--stage-height': `${height}px` } as CSSProperties}
        />
      }
    >
      {() => <Shell />}
    </BrowserOnly>
  );
};
