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

const SHARD_COLUMNS = 4;
const SHARD_ROWS = 3;
// In seconds. The blast dies out quickly, and the fall picks up more slowly.
const SHARD_DRAG = 0.16;
const SHARD_FALL_RAMP = 0.35;
const SHARD_FALL_SPEED = 120;
const SHARD_FRAMES = 24;

const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const clamp01 = (value: number) => Math.min(Math.max(value, 0), 1);

const randomBetween = (min: number, max: number) =>
  min + Math.random() * (max - min);

type Point = [x: number, y: number];

/**
 * The points of a ripped edge. It wanders at uneven intervals, because a regular
 * zigzag looks like the edge of a postage stamp rather than a tear.
 */
const tornEdge = ([startX, startY]: Point, [endX, endY]: Point) => {
  const width = endX - startX;
  const height = endY - startY;
  const length = Math.hypot(width, height);
  const steps = Math.max(3, Math.round(length / 2.5));
  let wander = 0;

  return Array.from({ length: steps }, (_, i): Point => {
    if (i === 0) return [startX, startY];
    wander = Math.max(-2.6, Math.min(wander + randomBetween(-1.6, 1.6), 2.6));
    const along = (i + randomBetween(-0.35, 0.35)) / steps;
    return [
      startX + width * along - (height / length) * wander,
      startY + height * along + (width / length) * wander,
    ];
  });
};

/**
 * A scrap of the sheet at a spot in the sheet's area: a small piece of paper
 * with one clean edge, as if from the sheet's border, and the rest ripped.
 */
const makeShard = (column: number, row: number) => {
  const width = randomBetween(14, 26);
  const height = randomBetween(10, 20);
  const corners: Point[] = [
    [-width / 2, -height / 2],
    [width / 2, -height / 2],
    [width / 2, height / 2],
    [-width / 2, height / 2],
  ];
  const cleanEdge = Math.floor(Math.random() * corners.length);
  const points = corners
    .flatMap((corner, i) =>
      i === cleanEdge
        ? [corner]
        : tornEdge(corner, corners[(i + 1) % corners.length]!)
    )
    .join(' ');

  return {
    x: (column + randomBetween(0.1, 0.9)) / SHARD_COLUMNS,
    y: (row + randomBetween(0.1, 0.9)) / SHARD_ROWS,
    width,
    points,
    // Some carry a line of the sheet's content.
    hasLine: Math.random() < 0.35,
  };
};

/** Blasts out, slows like paper in air, then flutters down and fades out. */
const shardKeyframes = (
  directionX: number,
  directionY: number,
  life: number
) => {
  // Gentle enough that most scraps stay on the stage to fall.
  const speed = randomBetween(220, 700);
  const swayWidth = randomBetween(6, 18);
  const swayRate = randomBetween(4, 8);
  const swayPhase = randomBetween(0, Math.PI * 2);
  const spin = randomBetween(-200, 200);
  const flutterRate = randomBetween(5, 10);

  return Array.from({ length: SHARD_FRAMES + 1 }, (_, frame): Keyframe => {
    const time = (frame / SHARD_FRAMES) * life;
    const blast = speed * SHARD_DRAG * (1 - Math.exp(-time / SHARD_DRAG));
    const fall =
      SHARD_FALL_SPEED *
      (time - SHARD_FALL_RAMP * (1 - Math.exp(-time / SHARD_FALL_RAMP)));
    const sway =
      Math.sin(time * swayRate + swayPhase) * swayWidth * clamp01(time / 0.4);
    const x = directionX * blast + sway;
    const y = directionY * blast + fall;
    // Rocks rather than flips, so a scrap never turns edge-on and disappears.
    const flutter = Math.sin(time * flutterRate + swayPhase) * 55;

    return {
      transform: `translate(${x}px, ${y}px) rotate(${spin * time}deg) rotateX(${flutter}deg)`,
      opacity: 1 - clamp01((time / life - 0.6) / 0.4),
    };
  });
};

/**
 * The sheet swelling and bursting into scraps, with a shockwave across the
 * stage. It covers the area the sheet filled, and removes itself when it's done.
 */
const SheetBurst = ({ onDone }: { onDone: () => void }) => {
  const ref = useRef<HTMLDivElement>(null);
  const [shards] = useState(() =>
    Array.from({ length: SHARD_COLUMNS * SHARD_ROWS }, (_, i) =>
      makeShard(i % SHARD_COLUMNS, Math.floor(i / SHARD_COLUMNS))
    )
  );

  useEffect(() => {
    if (prefersReducedMotion()) {
      onDone();
      return;
    }
    const root = ref.current!;
    const [swell, shockwave, ...pieces] = Array.from(root.children);
    const animations = [
      swell!.animate(
        [
          { transform: 'scale(1)', opacity: 1 },
          { transform: 'scale(1.08)', opacity: 0 },
        ],
        { duration: 170, easing: 'ease-out' }
      ),
      shockwave!.animate(
        [
          { transform: 'scale(0.3)', opacity: 0.5 },
          { transform: 'scale(5)', opacity: 0 },
        ],
        { duration: 500, easing: 'cubic-bezier(0.23, 1, 0.32, 1)' }
      ),
    ];

    let longestLife = 0;
    let lastToFinish = animations[1]!;
    pieces.forEach((piece, i) => {
      const shard = shards[i]!;
      // Away from the sheet's center, and a little upward.
      const offsetX = (shard.x - 0.5) * root.clientWidth;
      const offsetY = (shard.y - 0.5 - 0.15) * root.clientHeight;
      const distance = Math.hypot(offsetX, offsetY) || 1;
      const life = randomBetween(1.4, 2);
      const animation = piece.animate(
        shardKeyframes(offsetX / distance, offsetY / distance, life),
        { duration: life * 1000 }
      );
      animations.push(animation);
      if (life > longestLife) {
        longestLife = life;
        lastToFinish = animation;
      }
    });
    lastToFinish.onfinish = onDone;
    return () => animations.forEach((animation) => animation.cancel());
  }, [onDone, shards]);

  return (
    <div ref={ref} aria-hidden style={BURST_STYLE}>
      <div style={SWELL_STYLE} />
      <div style={SHOCKWAVE_STYLE} />
      {shards.map((shard, i) => (
        <svg
          key={i}
          width={1}
          height={1}
          style={{
            ...SHARD_STYLE,
            left: `${shard.x * 100}%`,
            top: `${shard.y * 100}%`,
          }}
        >
          <polygon points={shard.points} fill="var(--example-scrap)" />
          {shard.hasLine && (
            <rect
              x={-shard.width * 0.3}
              y={-1}
              width={shard.width * 0.6}
              height={2}
              rx={1}
              fill="var(--example-text)"
              opacity={0.25}
            />
          )}
        </svg>
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
const animateHose = (hose: SVGSVGElement, onEnd: () => void) => {
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
    if (time < HOSE_DURATION) {
      frame = requestAnimationFrame(draw);
    } else {
      hose.style.visibility = 'hidden';
      onEnd();
    }
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
  popped: boolean,
  onHoseEnd: () => void
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
      if (reduceMotion) {
        onHoseEnd();
        return;
      }
      return animateHose(hoseRef.current!, onHoseEnd);
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
  }, [isOpen, popped, pipeRef, hoseRef, onHoseEnd]);
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
    const [isHoseFlopping, setIsHoseFlopping] = useState(false);
    const [animateIn, setAnimateIn] = useState(false);
    const height = INFLATION_STEPS[step]!;
    const pipeRef = useRef<HTMLDivElement>(null);
    const nextBulge = useRef(0);
    const hoseRef = useRef<SVGSVGElement>(null);
    const plungerRef = useRef<SVGSVGElement>(null);
    // Stable, so re-rendering mid-burst doesn't restart it.
    const endBurst = useCallback(() => setIsBursting(false), []);
    const endHoseFlop = useCallback(() => setIsHoseFlopping(false), []);

    const restore = () => {
      setStep(0);
      setIndex(1);
      setAnimateIn(true);
      setPopped(false);
      setIsHoseFlopping(false);
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

    usePipeAnimation(pipeRef, hoseRef, isOpen, popped, endHoseFlop);
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
              disabled={isHoseFlopping || isBursting}
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
                    setIsHoseFlopping(true);
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

/** The area the sheet filled when it popped, from `SHEET_INSETS`. */
const BURST_STYLE: CSSProperties = {
  position: 'absolute',
  top: 0,
  right: SHEET_INSETS[0]!.right,
  bottom: 0,
  left: SHEET_INSETS[0]!.left,
  pointerEvents: 'none',
};

const SWELL_STYLE: CSSProperties = {
  position: 'absolute',
  inset: 0,
  borderTopLeftRadius: 16,
  borderTopRightRadius: 16,
  backgroundColor: 'var(--example-surface)',
  boxShadow: '0 -2px 16px rgba(0, 16, 72, 0.12)',
  opacity: 0,
};

const SHOCKWAVE_STYLE: CSSProperties = {
  position: 'absolute',
  top: '50%',
  left: '50%',
  width: 120,
  height: 120,
  marginTop: -60,
  marginLeft: -60,
  border: '6px solid var(--example-button)',
  borderRadius: '50%',
  opacity: 0,
};

/** Shaded like the sheet, which is what sets a scrap apart from the stage. */
const SHARD_STYLE: CSSProperties = {
  position: 'absolute',
  overflow: 'visible',
  filter: 'drop-shadow(0 2px 6px rgba(0, 16, 72, 0.22))',
  opacity: 0,
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
