import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type RefObject,
} from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import {
  ControlGroup,
  Example,
  Hint,
  interactiveExample,
  SheetBody,
  SheetText,
  StageButton,
  StageContent,
  StageInlineSheet,
} from '../components/InteractiveExample';

// Starts small, and pops at the top edge.
// At the fourth step, the sheet partly overlaps the Tapped button.
const INFLATION_STEPS = [20, 36, 52, 62, 80, 100];

// Leaves room on the sheet's left for the pump.
const SHEET_INSETS = [{ left: 124, right: 16 }];

const BURST_COLORS = ['#782aeb', '#38acdd', '#57b495', '#ff6259', '#ffd61e'];
const BURST_PIECES = 18;

const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** A one-off burst of confetti, which removes itself when it's done. */
const SheetBurst = ({ onDone }: { onDone: () => void }) => {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const pieces = Array.from(ref.current!.children);
    if (prefersReducedMotion() || pieces.length === 0) {
      onDone();
      return;
    }

    const animations = pieces.map((piece, i) => {
      const angle = (i / pieces.length) * Math.PI * 2;
      const distance = 90 + (i % 3) * 45;
      const x = Math.cos(angle) * distance;
      const y = Math.sin(angle) * distance;
      return piece.animate(
        [
          { transform: 'translate(0, 0) scale(1)', opacity: 1 },
          {
            transform: `translate(${x}px, ${y}px) scale(0.3)`,
            opacity: 0,
          },
        ],
        {
          duration: 480,
          easing: 'cubic-bezier(0.23, 1, 0.32, 1)',
          fill: 'forwards',
        }
      );
    });
    animations[0]!.onfinish = onDone;
    return () => animations.forEach((animation) => animation.cancel());
  }, [onDone]);

  return (
    <div
      ref={ref}
      aria-hidden
      style={{
        position: 'absolute',
        top: '50%',
        left: '58%',
        pointerEvents: 'none',
      }}
    >
      {Array.from({ length: BURST_PIECES }, (_, i) => (
        <span
          key={i}
          style={{
            position: 'absolute',
            width: 10,
            height: 10,
            borderRadius: i % 2 === 0 ? 5 : 2,
            backgroundColor: BURST_COLORS[i % BURST_COLORS.length],
          }}
        />
      ))}
    </div>
  );
};

const PIPE_LENGTH = 100;
const BULGE_COUNT = 3;

// Web-only: React Native Web otherwise holds a press back for 50 ms.
const NO_PRESS_DELAY = { delayPressIn: 0 };

const HOSE_SEGMENTS = 20;
const HOSE_DURATION = 1250;

const clamp01 = (value: number) => Math.min(Math.max(value, 0), 1);

/** How far the hose's free end has turned at a moment, in radians, up from flat. */
const hoseBend = (time: number) => {
  // The air still coming through lifts the end, and it wobbles like jelly.
  const lift = 1 - (1 - clamp01(time / 300)) ** 3;
  const wobble = Math.sin(time / 50) * Math.exp(-time / 320) * 0.4;
  // Then the air runs out and it drops, jiggling where it lands.
  const fall = clamp01((time - 520) / 380) ** 2;
  const landed = Math.max(time - 900, 0);
  const jiggle = Math.sin(landed / 40) * Math.exp(-landed / 140) * 0.14;
  return (1.7 * lift + wobble) * (1 - fall) - 0.55 * fall + jiggle;
};

/**
 * Flops the hose from where the pump holds it. Each point follows the one nearer
 * the pump a little late, which is what makes it look soft.
 */
const animateHose = (hose: SVGSVGElement) => {
  const [line, airBulge] = hose.children as unknown as [
    SVGPolylineElement,
    SVGCircleElement,
  ];
  const start = performance.now();
  let frame = 0;

  const draw = (now: number) => {
    const time = now - start;
    const points: [number, number][] = [[0, 0]];
    for (let i = 1; i <= HOSE_SEGMENTS; i++) {
      const along = i / HOSE_SEGMENTS;
      const ripple =
        Math.sin(along * 7 - time / 45) *
        along *
        clamp01(time / 100) *
        Math.exp(-time / 450) *
        0.8;
      const angle = along * hoseBend(Math.max(time - along * 110, 0)) + ripple;
      const [x, y] = points[i - 1]!;
      const step = PIPE_LENGTH / HOSE_SEGMENTS;
      points.push([x + step * Math.cos(angle), y - step * Math.sin(angle)]);
    }
    line.setAttribute('points', points.join(' '));

    // The last of the air runs out through the free end.
    const airAlong = clamp01(time / 420);
    const [airX, airY] = points[Math.round(airAlong * HOSE_SEGMENTS)]!;
    airBulge.setAttribute('cx', String(airX));
    airBulge.setAttribute('cy', String(airY));
    airBulge.setAttribute('r', airAlong < 1 ? '9' : '0');

    hose.style.opacity = String(1 - clamp01((time - 800) / 450));
    if (time < HOSE_DURATION) frame = requestAnimationFrame(draw);
    else hose.style.visibility = 'hidden';
  };

  hose.style.visibility = 'visible';
  draw(start);
  frame = requestAnimationFrame(draw);
  return () => {
    cancelAnimationFrame(frame);
    hose.style.visibility = 'hidden';
  };
};

/**
 * Connects the pipe while the sheet is open, retracts it into the pump when the
 * sheet closes, and swaps it for a hose that flops when the sheet pops.
 */
const usePipeAnimation = (
  pipeRef: RefObject<HTMLDivElement | null>,
  hoseRef: RefObject<SVGSVGElement | null>,
  isOpen: boolean,
  popped: boolean
) => {
  const isFirstRender = useRef(true);
  const wasPopped = useRef(false);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    const pipe = pipeRef.current!;
    const reduceMotion = prefersReducedMotion();
    // Picks up from an interrupted retraction, but a fallen pipe starts over.
    const { a: currentScale } = new DOMMatrix(getComputedStyle(pipe).transform);
    const from = wasPopped.current ? 0 : currentScale;
    wasPopped.current = popped;
    pipe.getAnimations().forEach((animation) => animation.cancel());
    pipe.style.visibility = popped ? 'hidden' : 'visible';
    if (popped) {
      return reduceMotion ? undefined : animateHose(hoseRef.current!);
    }
    // Connecting takes about as long as the sheet takes to rise.
    pipe.animate(
      [
        { transform: `scaleX(${from})` },
        { transform: `scaleX(${isOpen ? 1 : 0})` },
      ],
      {
        duration: reduceMotion ? 1 : isOpen ? 440 : 260,
        easing: isOpen
          ? 'cubic-bezier(0.32, 0.72, 0, 1)'
          : 'cubic-bezier(0.23, 1, 0.32, 1)',
        fill: 'forwards',
      }
    );
  }, [isOpen, popped, pipeRef, hoseRef]);
};

const AirPump = ({
  plungerRef,
}: {
  plungerRef: RefObject<SVGSVGElement | null>;
}) => (
  <>
    {/* The handle and rod, which plunge into the barrel. On its own layer, so
        moving it doesn't repaint the rest of the pump. */}
    <svg
      ref={plungerRef}
      viewBox="0 0 56 88"
      width={56}
      height={88}
      style={PLUNGER_STYLE}
      aria-hidden
    >
      <rect x="25" y="8" width="6" height="42" fill="var(--example-muted)" />
      <rect
        x="8"
        y="2"
        width="40"
        height="9"
        rx="4.5"
        fill="var(--example-text)"
      />
    </svg>
    <svg
      viewBox="0 0 56 88"
      width={56}
      height={88}
      style={BARREL_STYLE}
      aria-hidden
    >
      <rect x="16" y="36" width="24" height="44" rx="4" fill="#2196f3" />
      <rect
        x="6"
        y="80"
        width="44"
        height="7"
        rx="3.5"
        fill="var(--example-text)"
      />
    </svg>
  </>
);

export const InlineSheetExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [step, setStep] = useState(0);
    const [count, setCount] = useState(0);
    const [popped, setPopped] = useState(false);
    const [isBursting, setIsBursting] = useState(false);
    const [animateIn, setAnimateIn] = useState(false);
    const height = INFLATION_STEPS[step]!;
    const pipeRef = useRef<HTMLDivElement>(null);
    const nextBulge = useRef(0);
    const hoseRef = useRef<SVGSVGElement>(null);
    const plungerRef = useRef<SVGSVGElement>(null);
    // Stable, so re-rendering mid-burst doesn't restart it.
    const endBurst = useCallback(() => setIsBursting(false), []);

    const restore = () => {
      setStep(0);
      setIndex(1);
      setAnimateIn(true);
      setPopped(false);
    };

    /** Drives the plunger down at once, from wherever a last press left it. */
    const plunge = () => {
      if (prefersReducedMotion()) return;
      const plunger = plungerRef.current!;
      const { f: current } = new DOMMatrix(getComputedStyle(plunger).transform);
      plunger.getAnimations().forEach((animation) => animation.cancel());
      plunger.animate(
        [
          {
            transform: `translateY(${current}px)`,
            easing: 'cubic-bezier(0.23, 1, 0.32, 1)',
          },
          {
            transform: 'translateY(24px)',
            offset: 0.25,
            easing: 'ease-in-out',
          },
          { transform: 'translateY(0)' },
        ],
        { duration: 320 }
      );
    };

    /**
     * Pushes a bulge of air along the pipe and under the sheet's edge. Each press
     * takes the next bulge, so it doesn't cut off the air already on its way.
     */
    const blowAir = () => {
      if (prefersReducedMotion()) return;
      const bulges = pipeRef.current!.children;
      const bulge = bulges[nextBulge.current]!;
      nextBulge.current = (nextBulge.current + 1) % bulges.length;
      bulge.animate(
        [
          { transform: 'translateX(-14px)', opacity: 1 },
          { transform: `translateX(${PIPE_LENGTH}px)`, opacity: 1 },
        ],
        { duration: 340, easing: 'cubic-bezier(0.25, 0.6, 0.45, 1)' }
      );
    };

    const isOpen = !popped && index > 0;
    // Opens a closed or popped sheet, and otherwise inflates it.
    const pump = () => {
      plunge();
      if (popped) {
        restore();
      } else if (index === 0) {
        setIndex(1);
      } else {
        blowAir();
        setStep((current) => Math.min(current + 1, INFLATION_STEPS.length - 1));
      }
    };

    usePipeAnimation(pipeRef, hoseRef, isOpen, popped);
    const toggle = () => {
      if (popped) restore();
      else setIndex(isOpen ? 0 : 1);
    };

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton
                label={isOpen ? 'Close sheet' : 'Open sheet'}
                onPress={toggle}
              />
            </StageContent>
            <View style={styles.tapButton} pointerEvents="box-none">
              <StageButton
                label={`Tapped ${count} ${count === 1 ? 'time' : 'times'}`}
                onPress={() => setCount((current) => current + 1)}
              />
            </View>
            <div ref={pipeRef} style={PIPE_STYLE} aria-hidden>
              {Array.from({ length: BULGE_COUNT }, (_, i) => (
                <div key={i} style={BULGE_STYLE} />
              ))}
            </div>
            <svg ref={hoseRef} style={HOSE_STYLE} aria-hidden>
              <polyline
                fill="none"
                stroke="var(--example-muted)"
                strokeWidth={8}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <circle fill="var(--example-muted)" />
            </svg>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Pump"
              // On press rather than release, so the pump answers right away.
              onPressIn={pump}
              {...NO_PRESS_DELAY}
              style={styles.pump}
            >
              <AirPump plungerRef={plungerRef} />
            </Pressable>
            {!popped && (
              <StageInlineSheet
                insets={SHEET_INSETS}
                animateIn={animateIn}
                detents={[0, `${height}%`]}
                index={index}
                onIndexChange={setIndex}
                // A detent changing height doesn't fire `onSettle`, so watch the position.
                onPositionChange={(event) => {
                  if (height === 100 && event.nativeEvent.index >= 0.995) {
                    setPopped(true);
                    setIsBursting(true);
                  }
                }}
              >
                <SheetBody title="Inline sheet">
                  <SheetText>
                    It has no scrim, so what's around it keeps working.
                  </SheetText>
                </SheetBody>
              </StageInlineSheet>
            )}
            {isBursting && <SheetBurst onDone={endBurst} />}
          </>
        }
        controls={
          <ControlGroup>
            <Hint>
              An inline sheet has no scrim, so the buttons around it keep
              working. Pump until the sheet overlaps Tapped, and press the part
              still showing.
            </Hint>
            <Hint>
              Press the pump to open the sheet, then to grow it by changing its
              detent. At the top edge, the sheet pops and unmounts.
            </Hint>
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);
const [height, setHeight] = useState(${height});
const [popped, setPopped] = useState(${popped});

return popped ? null : (
  <BottomSheet
    detents={[0, \`\${height}%\`]} // Now '${height}%'.
    index={index}
    onIndexChange={setIndex}
    onPositionChange={(event) => {
      if (height === 100 && event.nativeEvent.index >= 0.995) setPopped(true);
    }}
    surface={
      <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
    }
  >
    {/* ... */}
  </BottomSheet>
);`}
      />
    );
  },
  { height: 360 }
);

// From the pump's barrel to under the sheet's left edge.
const PIPE_STYLE: CSSProperties = {
  position: 'absolute',
  bottom: 28,
  left: 50,
  width: PIPE_LENGTH,
  height: 8,
  backgroundColor: 'var(--example-muted)',
  transformOrigin: 'left center',
};

const BARREL_STYLE: CSSProperties = { position: 'absolute', top: 0, left: 0 };
const PLUNGER_STYLE: CSSProperties = {
  ...BARREL_STYLE,
  willChange: 'transform',
};

/** Its origin is where the pipe leaves the pump, and it draws outside its box. */
const HOSE_STYLE: CSSProperties = {
  position: 'absolute',
  bottom: 32,
  left: 50,
  width: 1,
  height: 1,
  overflow: 'visible',
  pointerEvents: 'none',
  visibility: 'hidden',
};

/** Fatter than the pipe, so the pipe looks swollen where the air is. */
const BULGE_STYLE: CSSProperties = {
  position: 'absolute',
  top: -5,
  left: 0,
  width: 22,
  height: 18,
  borderRadius: 9,
  backgroundColor: 'var(--example-muted)',
  opacity: 0,
};

const styles = StyleSheet.create({
  tapButton: {
    position: 'absolute',
    top: '42%',
    left: 0,
    right: 0,
    marginTop: -26,
    alignItems: 'center',
  },
  pump: {
    position: 'absolute',
    bottom: 14,
    left: 18,
    width: 56,
    height: 88,
  },
});
