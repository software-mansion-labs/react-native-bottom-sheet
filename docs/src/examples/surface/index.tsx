import { useState, type ReactNode } from 'react';

import {
  ControlGroup,
  Example,
  Hint,
  interactiveExample,
  Segmented,
  SheetBody,
  SheetText,
  Slider,
  StageButton,
  StageContent,
  StageInlineSheet,
  StageSheet,
  Toggle,
} from '../../components/InteractiveExample';
import styles from './styles.module.css';

// An evenly scattered sequence (R2), so the stars don't form rows and stay the
// same on every render.
const STARS = Array.from({ length: 34 }, (_, i) => ({
  left: `${((i * 0.5698) % 1) * 100}%`,
  top: ((i * 0.7549) % 1) * 150,
  size: 1 + (i % 3),
  delay: `${-((i * 0.37) % 2.6)}s`,
}));

const NightSurface = () => (
  <div className={`${styles.scene} ${styles.night}`}>
    {STARS.map(({ left, top, size, delay }) => (
      <span
        key={`${left}-${top}`}
        className={styles.star}
        style={{ left, top, width: size, height: size, animationDelay: delay }}
      />
    ))}
    <div className={styles.moon} />
    <svg
      className={styles.mountains}
      viewBox="0 0 400 130"
      preserveAspectRatio="none"
      aria-hidden
    >
      <path
        d="M0 130V78l62-48 56 40 70-62 72 58 52-34 88 50v48z"
        fill="#3a2a70"
      />
      <path d="M0 130V104l84-38 66 30 86-46 74 48 90-24v56z" fill="#120d33" />
    </svg>
    <div className={styles.ground} />
  </div>
);

const AuroraSurface = () => (
  <div className={`${styles.scene} ${styles.aurora}`}>
    <div className={`${styles.light} ${styles.lightGreen}`} />
    <div className={`${styles.light} ${styles.lightBlue}`} />
    <div className={`${styles.light} ${styles.lightPurple}`} />
  </div>
);

interface SurfaceOption {
  value: string;
  label: string;
  swatch: string;
  /** The JSX in the Code tab. */
  code: string;
  element?: ReactNode;
}

const SURFACES: SurfaceOption[] = [
  {
    value: 'night',
    label: 'Night',
    swatch: '#27205e',
    code: '<NightSky style={StyleSheet.absoluteFill} />',
    element: <NightSurface />,
  },
  {
    value: 'aurora',
    label: 'Aurora',
    swatch: '#34e89e',
    code: '<Aurora style={StyleSheet.absoluteFill} />',
    element: <AuroraSurface />,
  },
  {
    value: 'solid',
    label: 'Solid',
    swatch: 'var(--example-surface)',
    code: "<View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />",
  },
];

const PARAGRAPHS = [
  'The surface can be anything you can render, behind your content.',
  'It spans the whole sheet, so a taller sheet shows more of it.',
  'When this text goes away, the sheet shrinks and the surface stays put, with no blank space behind it.',
];

export const SurfaceExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [surfaceName, setSurfaceName] = useState(SURFACES[0]!.value);
    const [paragraphs, setParagraphs] = useState(1);
    const [animates, setAnimates] = useState(true);
    const surface = SURFACES.find(({ value }) => value === surfaceName)!;

    return (
      <Example
        stage={
          <>
            {/* Only while closed, as the sheet's heights would cut through it. */}
            {index === 0 && (
              <StageContent>
                <StageButton label="Open sheet" onPress={() => setIndex(1)} />
              </StageContent>
            )}
            <StageSheet
              detents={[0, 'content', '90%']}
              {...(surface.element === undefined
                ? {}
                : { surface: surface.element })}
              animateContentHeight={animates}
              index={index}
              onIndexChange={setIndex}
              scrimColor="rgba(0, 0, 0, 0.4)"
            >
              <div
                className={
                  surface.element === undefined ? undefined : styles.onScene
                }
              >
                <SheetBody title="Sheet content">
                  {PARAGRAPHS.slice(0, paragraphs).map((paragraph) => (
                    <SheetText key={paragraph}>{paragraph}</SheetText>
                  ))}
                </SheetBody>
              </div>
            </StageSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Segmented
              label="surface"
              options={SURFACES.map(({ value, label, swatch }) => ({
                value,
                label,
                swatch,
              }))}
              value={surfaceName}
              onChange={setSurfaceName}
            />
            <Segmented
              label="Paragraphs"
              options={[
                { value: 1, label: '1' },
                { value: 2, label: '2' },
                { value: 3, label: '3' },
              ]}
              value={paragraphs}
              onChange={setParagraphs}
            />
            <Hint>
              Add paragraphs to the Night sheet, and it reveals the mountains
              that were already there. Without <code>animateContentHeight</code>
              , it does so at once.
            </Hint>
            <Toggle
              label="animateContentHeight"
              value={animates}
              onChange={setAnimates}
            />
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);

<ModalBottomSheet
  detents={[0, 'content', '90%']}${animates ? '' : '\n  animateContentHeight={false}'}
  index={index}
  onIndexChange={setIndex}
  surface={
    ${surface.code}
  }
>
  {/* ${paragraphs} ${paragraphs === 1 ? 'paragraph' : 'paragraphs'} */}
</ModalBottomSheet>;`}
      />
    );
  },
  { height: 360 }
);

const HUE_TRACK =
  'linear-gradient(to right, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00)';

/** A fully saturated color at a hue, in degrees, as `[red, green, blue]`. */
const hueToRgb = (hue: number) =>
  [0, 8, 4].map((offset) => {
    const position = (offset + hue / 30) % 12;
    return Math.round(
      255 * (0.5 - 0.5 * Math.max(-1, Math.min(position - 3, 9 - position, 1)))
    );
  });

const toHex = (hue: number) =>
  `#${hueToRgb(hue)
    .map((channel) => channel.toString(16).padStart(2, '0'))
    .join('')}`;

// Rounded on all sides, a card needs gaps for its bottom corners to show.
const FLOATING_INSETS = { left: 40, right: 40, bottom: 24 };

export const CardShapeExample = interactiveExample(
  () => {
    const [index, setIndex] = useState(1);
    const [isFloating, setIsFloating] = useState(false);
    const [radius, setRadius] = useState(24);
    const [blur, setBlur] = useState(24);
    const [hue, setHue] = useState(220);
    const [opacity, setOpacity] = useState(0.6);
    const shadowRgb = hueToRgb(hue).join(', ');
    const shadow = `0 -4 ${blur} rgba(${shadowRgb}, ${opacity})`;
    const shadowCode = `0 -4 ${Math.round(blur)} rgba(${shadowRgb}, ${opacity.toFixed(2)})`;
    const roundedRadius = Math.round(radius);

    return (
      <Example
        stage={
          <>
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
            <StageInlineSheet
              // The shared look's width cap would leave no room to compare.
              detents={
                isFloating
                  ? [
                      0,
                      { value: 140, insets: FLOATING_INSETS },
                      { value: '90%', insets: FLOATING_INSETS },
                    ]
                  : [0, 140, '90%']
              }
              surfaceStyle={{
                ...(isFloating
                  ? { borderRadius: radius }
                  : {
                      borderTopLeftRadius: radius,
                      borderTopRightRadius: radius,
                    }),
                boxShadow: shadow,
              }}
              index={index}
              onIndexChange={setIndex}
            >
              <SheetBody title="Card shape">
                <SheetText>
                  Drag me up. The sheet clips its surface and content to this
                  shape at every height.
                </SheetText>
              </SheetBody>
            </StageInlineSheet>
          </>
        }
        controls={
          <ControlGroup>
            <Toggle
              label="Round all corners"
              value={isFloating}
              onChange={setIsFloating}
            />
            <Slider
              label="Corner radius"
              value={radius}
              onChange={setRadius}
              max={64}
              decimals={0}
            />
            <Slider
              label="Shadow blur"
              value={blur}
              onChange={setBlur}
              max={64}
              decimals={0}
            />
            <Slider
              label="Shadow color"
              value={hue}
              onChange={setHue}
              max={360}
              formatValue={toHex}
              track={HUE_TRACK}
            />
            <Slider
              label="Shadow opacity"
              value={opacity}
              onChange={setOpacity}
            />
          </ControlGroup>
        }
        code={`const [index, setIndex] = useState(1);

<BottomSheet
  detents={${
    isFloating
      ? `[
    0,
    { value: 140, insets: { left: 40, right: 40, bottom: 24 } },
    { value: '90%', insets: { left: 40, right: 40, bottom: 24 } },
  ]`
      : `[0, 140, '90%']`
  }}
  index={index}
  onIndexChange={setIndex}
  surface={
    <View style={[StyleSheet.absoluteFill, { backgroundColor: 'white' }]} />
  }
  surfaceStyle={{${
    isFloating
      ? `\n    borderRadius: ${roundedRadius},`
      : `\n    borderTopLeftRadius: ${roundedRadius},\n    borderTopRightRadius: ${roundedRadius},`
  }
    boxShadow: '${shadowCode}',
  }}
>
  {/* ... */}
</BottomSheet>;`}
      />
    );
  },
  { height: 360 }
);
