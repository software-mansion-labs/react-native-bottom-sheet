import {
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react';
import { ScrollView, Text, View } from 'react-native';
import { BottomSheet } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import {
  ATTACHED,
  Backdrop,
  DocsButton,
  DragHint,
  Grabber,
  restStops,
  Row,
  sheet,
  SHEET_SURFACE,
  SheetBody,
  Stage,
  TOP_INSET,
  useRestUnderContent,
} from './example';
import { Icon, Reveal, SlideActiveContext } from './pieces';
import landing from './landing.module.css';
import shared from './example.module.css';
import styles from './Portfolio.module.css';

type Direction = 'up' | 'down';

const ROLL_MS = 480;
const MARKET_CYCLE_MS = 5000;
const PORTFOLIO_OPEN = 46926.32;

const WATCHLIST = [
  ['NORD', 'Nordlys Energy', 142.3, '#38acdd', 0.7],
  ['ALTA', 'Alta Robotics', 86.12, '#782aeb', 1.3],
  ['KITE', 'Kite Logistics', 23.84, '#57b495', 2.1],
  ['PINE', 'Pinewood Foods', 54.6, '#31775d', 0.4],
  ['HALO', 'Halo Biotech', 212.45, '#ff6259', 1.8],
  ['ROOK', 'Rook Security', 67.91, '#001a72', 0.9],
  ['TIDE', 'Tidewater Shipping', 38.27, '#33488e', 1.5],
  ['VOLT', 'Volt Storage', 119.03, '#38acdd', 2.4],
  ['MILL', 'Millstone Group', 44.5, '#91823d', 0.6],
  ['ORBT', 'Orbit Networks', 301.18, '#782aeb', 1.1],
] as const;

type Quote = {
  ticker: string;
  name: string;
  color: string;
  swing: number;
  open: number;
  price: number;
  direction: Direction;
};

type Market = { quotes: Quote[]; value: number; direction: Direction };

const portfolioValue = (quotes: Quote[]) =>
  48210.42 +
  quotes.reduce((sum, { price, open }) => sum + (price - open) * 12, 0);

const openMarket = (): Market => {
  const quotes = WATCHLIST.map(
    ([ticker, name, price, color, swing]): Quote => ({
      ticker,
      name,
      color,
      swing,
      open: price,
      price,
      direction: 'up',
    })
  );
  return { quotes, value: portfolioValue(quotes), direction: 'up' };
};

/** One step of sample data: each price moves a little, up or down. */
const nextMarket = (market: Market): Market => {
  const quotes = market.quotes.map((quote): Quote => {
    const step = quote.open * (quote.swing / 100) * (Math.random() * 0.8 - 0.4);
    return {
      ...quote,
      price: quote.price + step,
      direction: step < 0 ? 'down' : 'up',
    };
  });
  const value = portfolioValue(quotes);
  return { quotes, value, direction: value < market.value ? 'down' : 'up' };
};

const money = (value: number) =>
  value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

/** One character. When it changes, the old one leaves as the new one arrives. */
const RollingChar = ({
  char,
  direction,
  order,
}: {
  char: string;
  direction: Direction;
  order: number;
}) => {
  const shown = useRef(char);
  const [leaving, setLeaving] = useState<string | null>(null);
  useLayoutEffect(() => {
    if (shown.current === char) return undefined;
    setLeaving(shown.current);
    shown.current = char;
    const timer = setTimeout(() => setLeaving(null), ROLL_MS + order * 40);
    return () => clearTimeout(timer);
  }, [char, order]);
  const moving = leaving !== null;
  return (
    <span
      className={styles.rollChar}
      data-digit={/\d/.test(char)}
      data-moving={moving}
      data-direction={direction}
      style={{ '--roll-delay': `${order * 40}ms` } as CSSProperties}
    >
      {moving ? <span className={styles.rollOut}>{leaving}</span> : null}
      <span key={char} className={moving ? styles.rollIn : undefined}>
        {char}
      </span>
    </span>
  );
};

/**
 * A number that rolls to its next value: digits move up when it rises and down
 * when it falls, and only the digits that changed move, from the right.
 */
const RollingNumber = ({
  value,
  direction,
  className,
}: {
  value: string;
  direction: Direction;
  className?: string;
}) => {
  const chars = [...value];
  return (
    <span className={clsx(styles.rolling, className)} aria-label={value}>
      {chars.map((char, i) => {
        // Keyed from the right, so places stay put when the length changes.
        const fromRight = chars.length - 1 - i;
        return (
          <RollingChar
            key={fromRight}
            char={char}
            direction={direction}
            order={fromRight}
          />
        );
      })}
    </span>
  );
};

const PortfolioBackdrop = ({
  value,
  gain,
  direction,
}: {
  value: number;
  gain: number;
  direction: Direction;
}) => (
  <Backdrop className={styles.backdropPortfolio}>
    <div className={shared.appBar}>
      <b>Portfolio</b>
      <Icon d="M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4" />
    </div>
    <strong>
      <RollingNumber value={`$${money(value)}`} direction={direction} />
    </strong>
    <em>
      <RollingNumber value={`+$${money(gain)}`} direction={direction} /> today ·
      sample data
    </em>
    <svg viewBox="0 0 400 150" preserveAspectRatio="none">
      <defs>
        <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#a0dfc0" stopOpacity=".3" />
          <stop offset="1" stopColor="#3fc684" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path
        d="M0 120C30 110 50 124 90 92s60-6 100-34 50 10 90-12 70-22 120-30v134H0z"
        fill="url(#area)"
      />
      <path
        d="M0 120C30 110 50 124 90 92s60-6 100-34 50 10 90-12 70-22 120-30"
        fill="none"
        stroke="#a0dfc0"
        strokeWidth="2.5"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
    <div className={styles.ranges}>
      <b>1D</b>
      <span>1W</span>
      <span>1M</span>
      <span>1Y</span>
      <span>All</span>
    </div>
  </Backdrop>
);

export const ThreadsSlide = () => {
  const [blocked, setBlocked] = useState(false);
  const [index, setIndex] = useState(1);
  const [dragged, setDragged] = useState(false);
  const [market, setMarket] = useState(openMarket);
  const stage = useRef<HTMLDivElement>(null);
  const rest = useRestUnderContent(stage, 410);
  const uiValue = useRef<HTMLOutputElement>(null);
  const jsValue = useRef<HTMLOutputElement>(null);
  const blockedRef = useRef(false);
  const active = useContext(SlideActiveContext);

  // A cycle counted in time JS was free: blocking JS stalls it, as it would in an app.
  useEffect(() => {
    if (!active) return undefined;
    let last = performance.now();
    let elapsed = 0;
    const id = setInterval(() => {
      const now = performance.now();
      if (!blockedRef.current) elapsed += now - last;
      last = now;
      if (elapsed >= MARKET_CYCLE_MS) {
        elapsed -= MARKET_CYCLE_MS;
        setMarket(nextMarket);
      }
    }, 100);
    return () => clearInterval(id);
  }, [active]);

  const block = () => {
    if (blockedRef.current) return;
    blockedRef.current = true;
    setBlocked(true);
    setTimeout(() => {
      blockedRef.current = false;
      setBlocked(false);
    }, 4000);
  };

  return (
    <section className={shared.chapter} data-compact>
      <Reveal className={shared.chapterCopy} data-copy>
        <span className={shared.kicker}>03 — native host</span>
        <h2 className={shared.title}>
          Your JS can be busy. The sheet isn’t waiting.
        </h2>
        <p className={shared.body}>
          The drag, the snap, and the scroll handoff don’t pass through the JS
          thread, so a heavy render inside the sheet doesn’t stop your finger
          from moving it.
        </p>
        <div
          className={clsx(landing.lightTokens, styles.lanes)}
          data-blocked={blocked}
          data-active={active}
        >
          <div className={styles.lane} data-lane="ui">
            <span>native gesture</span>
            <div className={styles.laneTrack} />
            <output ref={uiValue}>—</output>
          </div>
          <div className={styles.lane} data-lane="js">
            <span>JS events</span>
            <div className={styles.laneTrack} />
            <output ref={jsValue}>—</output>
          </div>
        </div>
        <button
          className={clsx(styles.blockBtn, landing.press)}
          data-blocked={blocked}
          onClick={block}
        >
          <span>
            {blocked
              ? 'JS thread blocked…'
              : 'Block the JS thread for 4 seconds'}
          </span>
        </button>
        <p className={styles.honest}>
          Illustration. On the web, the sheet and JavaScript share one thread,
          so this page can only pretend.
        </p>
        <div className={clsx(shared.codeActions, shared.codeActionsSolo)}>
          <DocsButton page="how-it-compares" />
        </div>
      </Reveal>
      <Stage
        backdrop={
          <PortfolioBackdrop
            value={market.value}
            gain={market.value - PORTFOLIO_OPEN}
            direction={market.direction}
          />
        }
        stageRef={stage}
      >
        <BottomSheet
          detents={restStops(rest)}
          topInset={TOP_INSET}
          animateIn={false}
          index={index}
          onIndexChange={(next) => {
            setIndex(next);
            setDragged(true);
          }}
          surface={SHEET_SURFACE}
          surfaceStyle={ATTACHED}
          onPositionChange={(event) => {
            const shown = `${Math.round(event.nativeEvent.position)}`;
            if (uiValue.current) uiValue.current.textContent = shown;
            if (!blockedRef.current && jsValue.current) {
              jsValue.current.textContent = shown;
            }
          }}
        >
          <SheetBody fill>
            <DragHint seen={dragged} />
            <Grabber />
            <View style={sheet.head}>
              <Text style={sheet.title}>Watchlist</Text>
            </View>
            <ScrollView
              style={sheet.fill}
              contentContainerStyle={sheet.listPad}
            >
              {market.quotes.map(
                ({ ticker, name, color, open, price, direction }) => {
                  const change = ((price - open) / open) * 100;
                  return (
                    <Row
                      key={ticker}
                      item={[ticker, name, color]}
                      round
                      letter
                      trailing={
                        <div className={styles.quote}>
                          <RollingNumber
                            className={styles.quotePrice}
                            value={money(price)}
                            direction={direction}
                          />
                          <RollingNumber
                            className={styles.quoteChange}
                            value={`${change < 0 ? '−' : '+'}${Math.abs(change).toFixed(2)}%`}
                            direction={direction}
                          />
                        </div>
                      }
                    />
                  );
                }
              )}
            </ScrollView>
          </SheetBody>
        </BottomSheet>
      </Stage>
    </section>
  );
};
