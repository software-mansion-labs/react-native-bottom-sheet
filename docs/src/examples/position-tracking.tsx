import { useRef, useState, type CSSProperties, type RefObject } from 'react';

import {
  Control,
  ControlGroup,
  Example,
  Hint,
  interactiveExample,
  Segmented,
  SheetBody,
  SheetText,
  StageButton,
  StageContent,
  StageInlineSheet,
} from '../components/InteractiveExample';

const DETENTS = [0, 120, '50%', '90%'] as (number | `${number}%`)[];
const LAST_INDEX = DETENTS.length - 1;

// Spread evenly (R2), so the stars don't form rows.
const STARS = Array.from({ length: 30 }, (_, i) => ({
  left: `${((i * 0.5698) % 1) * 100}%`,
  top: `${((i * 0.7549) % 1) * 70}%`,
  size: 1 + (i % 3),
}));

// Around the globe's center, in degrees, and how far in from its rim. Sheets
// cover the middle of the stage, so the outer trees are the ones left showing.
const TREES = [
  { angle: -30, depth: 0, size: 14 },
  { angle: -25, depth: 0, size: 18 },
  { angle: -21.5, depth: 0, size: 13 },
  { angle: -14, depth: 0, size: 15 },
  { angle: -9, depth: 0, size: 19 },
  { angle: -6, depth: 0, size: 13 },
  { angle: 3, depth: 0, size: 14 },
  { angle: 12, depth: 0, size: 17 },
  { angle: 20, depth: 0, size: 14 },
  { angle: 23.5, depth: 0, size: 19 },
  { angle: 24, depth: 18, size: 18 },
  { angle: -8, depth: 22, size: 18 },
  { angle: -23, depth: 26, size: 20 },
  { angle: 5, depth: 30, size: 20 },
  { angle: 17, depth: 30, size: 24 },
  { angle: -16, depth: 34, size: 24 },
  { angle: -3, depth: 48, size: 26 },
];

const NIGHT_LAND = {
  grass: '#12403f',
  river: '#1b3a6b',
  crown: '#0a2b2d',
  trunk: '#0a2024',
};

const DAY_LAND = {
  grass: '#6cbf4b',
  river: '#4fb3e8',
  crown: '#2f8a3c',
  trunk: '#7a5230',
};

const Land = ({ grass, river, crown, trunk }: typeof DAY_LAND) => (
  <>
    <circle cx="500" cy="500" r="500" fill={grass} />
    <path
      d="M582 8C600 22 570 36 600 52S650 76 640 110L672 110C684 78 628 56 606 40S604 20 586 8Z"
      fill={river}
    />
    {TREES.map(({ angle, depth, size }) => (
      <g
        key={`${angle}-${depth}`}
        transform={`rotate(${angle} 500 500) translate(500 ${depth}) scale(${size})`}
      >
        <rect x="-0.07" y="-0.25" width="0.14" height="0.25" fill={trunk} />
        <path d="M-.36-.2L0-.7L.36-.2ZM-.28-.52L0-1L.28-.52Z" fill={crown} />
      </g>
    ))}
  </>
);

const IndexMeter = ({
  fillRef,
}: {
  fillRef: RefObject<HTMLDivElement | null>;
}) => (
  <div style={METER_STYLE}>
    <div ref={fillRef} style={METER_FILL_STYLE} />
    {DETENTS.map((_, i) => (
      <span
        key={i}
        style={{ ...METER_TICK_STYLE, left: `${(i / LAST_INDEX) * 100}%` }}
      />
    ))}
  </div>
);

export const LivePositionExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const dayRef = useRef<HTMLDivElement>(null);
    const starsRef = useRef<HTMLDivElement>(null);
    const sunRef = useRef<HTMLDivElement>(null);
    const dayLandRef = useRef<SVGGElement>(null);
    const positionRef = useRef<HTMLSpanElement>(null);
    const indexRef = useRef<HTMLSpanElement>(null);
    const fillRef = useRef<HTMLDivElement>(null);

    return (
      <Example
        stage={
          <>
            {/* Fires on every frame the sheet moves, so it writes straight to
                these elements rather than re-rendering. */}
            <div style={NIGHT_STYLE} aria-hidden>
              <div ref={dayRef} style={DAY_STYLE} />
              <div ref={starsRef} style={FILL_STYLE}>
                {STARS.map(({ left, top, size }) => (
                  <span
                    key={`${left}-${top}`}
                    style={{
                      ...STAR_STYLE,
                      left,
                      top,
                      width: size,
                      height: size,
                    }}
                  />
                ))}
              </div>
              <div ref={sunRef} style={SUN_STYLE} />
              {/* In front of the sun, which sets behind it at index 0. */}
              <svg style={GLOBE_STYLE} viewBox="-20 -40 1040 1040">
                <defs>
                  <radialGradient
                    id="globe-limb"
                    cx="500"
                    cy="500"
                    r="500"
                    gradientUnits="userSpaceOnUse"
                  >
                    <stop offset="0.86" stopColor="#001048" stopOpacity="0" />
                    <stop offset="1" stopColor="#001048" stopOpacity="0.35" />
                  </radialGradient>
                </defs>
                <circle
                  cx="500"
                  cy="500"
                  r="507"
                  fill="rgba(130, 200, 255, 0.16)"
                />
                <circle
                  cx="500"
                  cy="500"
                  r="503"
                  fill="rgba(130, 200, 255, 0.28)"
                />
                <Land {...NIGHT_LAND} />
                <g ref={dayLandRef} style={{ opacity: 0 }}>
                  <Land {...DAY_LAND} />
                </g>
                <circle cx="500" cy="500" r="500" fill="url(#globe-limb)" />
              </svg>
            </div>
            {index === 0 && (
              <StageContent>
                <StageButton label="Open sheet" onPress={() => setIndex(1)} />
              </StageContent>
            )}
            <StageInlineSheet
              detents={DETENTS}
              index={index}
              onIndexChange={setIndex}
              onPositionChange={({ nativeEvent }) => {
                const { position, index: fractional } = nativeEvent;
                const progress = fractional / LAST_INDEX;
                dayRef.current!.style.opacity = String(progress);
                dayLandRef.current!.style.opacity = String(progress);
                starsRef.current!.style.opacity = String(1 - progress * 1.6);
                sunRef.current!.style.transform = `translateY(${-position}px)`;
                positionRef.current!.textContent = position.toFixed(0);
                indexRef.current!.textContent = fractional.toFixed(2);
                fillRef.current!.style.transform = `scaleX(${progress})`;
              }}
            >
              <SheetBody title="Drag me">
                <SheetText>
                  The sun, the sky, the grass and the numbers all follow this
                  sheet.
                </SheetText>
              </SheetBody>
            </StageInlineSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Control
              label="nativeEvent.position"
              value={<span ref={positionRef}>0</span>}
            >
              <></>
            </Control>
            <Control
              label="nativeEvent.index"
              value={<span ref={indexRef}>0.00</span>}
            >
              <IndexMeter fillRef={fillRef} />
            </Control>
            <Segmented
              label="index"
              options={DETENTS.map((_, i) => ({ value: i, label: String(i) }))}
              value={index}
              onChange={setIndex}
            />
            <Hint>
              Drag the sheet slowly. The index passes through the values between
              detents, and setting it here reports the same way.
            </Hint>
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);
const position = useSharedValue(0);
const detentIndex = useSharedValue(1);

const sunStyle = useAnimatedStyle(() => ({
  transform: [{ translateY: -position.value }],
}));
const dayStyle = useAnimatedStyle(() => ({
  opacity: detentIndex.value / 3,
}));

<BottomSheet
  detents={[0, 120, '50%', '90%']}
  index={index}
  onIndexChange={setIndex}
  onPositionChange={({ nativeEvent }) => {
    // Points from the bottom of the screen to the top of the sheet.
    position.value = nativeEvent.position;
    // 0 at the first detent, 1 at the next, and fractions in between.
    detentIndex.value = nativeEvent.index;
  }}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
>
  {/* ... */}
</BottomSheet>;`}
      />
    );
  },
  { height: 400 }
);

const FILL_STYLE: CSSProperties = { position: 'absolute', inset: 0 };

const NIGHT_STYLE: CSSProperties = {
  ...FILL_STYLE,
  overflow: 'hidden',
  background: 'linear-gradient(#070b24, #1c1a4d)',
};

const DAY_STYLE: CSSProperties = {
  ...FILL_STYLE,
  background: 'linear-gradient(#4aa8e8, #ffd9a0)',
  opacity: 0,
};

const STAR_STYLE: CSSProperties = {
  position: 'absolute',
  borderRadius: '50%',
  backgroundColor: '#ffffff',
};

// Rests at the stage's bottom, so a translation by the position puts it on the
// sheet's top edge, half hidden behind it.
const SUN_STYLE: CSSProperties = {
  position: 'absolute',
  bottom: -36,
  left: 'calc(50% - 36px)',
  width: 72,
  height: 72,
  borderRadius: '50%',
  backgroundColor: '#ffc93c',
  boxShadow: '0 0 40px 12px rgba(255, 201, 60, 0.55)',
};

// Rises above the sun's disc, so the sun is out of sight with the sheet closed.
const GLOBE_RISE = 84;

// The drawing's globe is 1000 across, with room around it for the trees.
const GLOBE_STYLE: CSSProperties = {
  position: 'absolute',
  bottom: GLOBE_RISE - 1000,
  left: 'calc(50% - 520px)',
  width: 1040,
  height: 1040,
};

const METER_STYLE: CSSProperties = {
  position: 'relative',
  height: 6,
  borderRadius: 3,
  backgroundColor: 'var(--example-track)',
};

const METER_FILL_STYLE: CSSProperties = {
  ...FILL_STYLE,
  borderRadius: 3,
  backgroundColor: 'var(--example-button)',
  transform: 'scaleX(0)',
  transformOrigin: 'left center',
};

const METER_TICK_STYLE: CSSProperties = {
  position: 'absolute',
  top: -3,
  width: 2,
  height: 12,
  marginLeft: -1,
  backgroundColor: 'var(--example-muted)',
};
