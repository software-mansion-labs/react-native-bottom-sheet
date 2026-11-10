import { useContext, useEffect, useRef } from 'react';

import { SlideActiveContext } from '../../shared/pieces';
import styles from './styles.module.css';

const BRAND = 'https://brand.swmansion.com/logos/products/';

// As on the Enriched docs: [name, logo file without its theme suffix, link].
const LOGOS: [string, string, string | null][] = [
  ['App.js', 'app-js/app-js-horizontal', 'https://appjs.co/'],
  ['Argent', 'argent/argent-full', 'https://argent.swmansion.com/'],
  [
    'AudioAPI',
    'audioapi/audioapi-full',
    'https://docs.swmansion.com/react-native-audio-api/',
  ],
  [
    'Boombox',
    'boombox/boombox-horizontal',
    'https://github.com/membraneframework/boombox',
  ],
  ['Detour', 'detour/detour-full', 'https://detour.swmansion.com/'],
  [
    'Elixir WebRTC',
    'elixir-webrtc/elixir-webrtc-full',
    'https://elixir-webrtc.org/',
  ],
  ['Enriched', 'enriched/enriched-full', 'https://enriched.swmansion.com/'],
  [
    'ExecuTorch',
    'executorch/executorch-full',
    'https://executorch.swmansion.com/',
  ],
  ['Fishjam', 'fishjam/fishjam-full', 'https://fishjam.swmansion.com/'],
  [
    'Freeze',
    'freeze/freeze-full',
    'https://github.com/software-mansion/react-freeze',
  ],
  [
    'Gesture Handler',
    'gesture-handler/gesture-handler-full',
    'https://docs.swmansion.com/react-native-gesture-handler/',
  ],
  [
    'Global Elixir Meetups',
    'global-elixir-meetups/global-elixir-meetups-full',
    null,
  ],
  [
    'Live Debugger',
    'livedebugger/livedebugger-full',
    'https://docs.swmansion.com/live-debugger/',
  ],
  ['Membrane', 'membrane/membrane-full', 'https://membrane.stream/'],
  [
    'Nano Icons',
    'nano-icons/nano-icons-full',
    'https://github.com/software-mansion-labs/react-native-nano-icons',
  ],
  ['Popcorn', 'popcorn/popcorn-full', 'https://popcorn.swmansion.com/'],
  [
    'Private Mind',
    'private-mind/private-mind-full',
    'https://github.com/software-mansion-labs/private-mind',
  ],
  [
    'Protostar',
    'protostar/protostar-full-mono-name-hexagon-horizontal',
    'https://docs.swmansion.com/protostar/',
  ],
  ['Pulsar', 'pulsar/pulsar-full', 'https://docs.swmansion.com/pulsar/'],
  ['Radon IDE', 'radon/radon-full', 'https://radon.swmansion.com/'],
  [
    'react-native-whip-whep',
    'react-native-whip-whep/react-native-whip-whep-full',
    'https://docs.swmansion.com/react-native-whip-whep/',
  ],
  [
    'Reanimated',
    'reanimated/reanimated-full',
    'https://docs.swmansion.com/react-native-reanimated/',
  ],
  ['Repo', 'repo/repo-full', 'https://github.com/software-mansion/rnrepo'],
  ['RTC.ON', 'rtc-on/rtc-on-full', 'https://rtcon.live/'],
  [
    'Scarb',
    'scarb/scarb-full-mono-name-hexagon-horizontal',
    'https://docs.swmansion.com/scarb/',
  ],
  [
    'Screens',
    'screens/screens-full',
    'https://docs.swmansion.com/react-native-screens/',
  ],
  [
    'Setup-ci',
    'setup-ci/setup-ci-full-npx',
    'https://docs.swmansion.com/setup-ci/',
  ],
  ['Smelter', 'smelter/smelter-full', 'https://smelter.dev/'],
  [
    'SVG',
    'svg/svg-full',
    'https://github.com/software-mansion/react-native-svg',
  ],
  ['tsover', 'tsover/tsover-full', 'https://tsover.swmansion.com/'],
  [
    'TypeGPU',
    'typegpu/typegpu-full-color',
    'https://docs.swmansion.com/TypeGPU/',
  ],
  [
    'Worklets',
    'worklets/worklets-full',
    'https://docs.swmansion.com/react-native-reanimated/docs/guides/worklets/',
  ],
];

// The numbers are the Enriched docs' own (BrandStrip in its design system).
const RADIUS = 1600;
/** One turn every two minutes. */
const TURN_SECONDS = 120;
/** How far a press moves before it turns the wheel instead of clicking. */
const DRAG_SLOP_PX = 6;
/** A fling keeps this share of its speed each frame. */
const FLING_KEEP = 0.92;
const FLING_STOP = 0.02;

/** Turns `degrees` into 0-360. */
const wrap = (degrees: number) => ((degrees % 360) + 360) % 360;

const Logo = ({ name, file }: { name: string; file: string }) => (
  <>
    <img
      className={styles.forLight}
      src={`${BRAND}${file}-on-light.svg`}
      alt={name}
      loading="lazy"
      draggable={false}
    />
    <img
      className={styles.forDark}
      src={`${BRAND}${file}-on-dark.svg`}
      alt={name}
      loading="lazy"
      draggable={false}
    />
  </>
);

const Item = ({
  name,
  file,
  href,
}: {
  name: string;
  file: string;
  href: string | null;
}) => (
  <div className={styles.item}>
    {href === null ? (
      <Logo name={name} file={file} />
    ) : (
      <a href={href} target="_blank" rel="noopener" draggable={false}>
        <Logo name={name} file={file} />
      </a>
    )}
  </div>
);

/**
 * Software Mansion's projects on the rim of a slow wheel that the mouse or a
 * finger can spin, or on a scrolling strip on phones. It turns only while its
 * slide is showing, and pauses under the pointer.
 */
export const LogoWheel = () => {
  const isActive = useContext(SlideActiveContext);
  const wheel = useRef<HTMLDivElement>(null);
  const rotation = useRef(0);

  useEffect(() => {
    if (!isActive) return;
    const element = wheel.current!;
    const spin = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 0
      : 360 / TURN_SECONDS / 1000;

    let velocity = 0;
    let isHovered = false;
    let press: {
      pointerId: number;
      x: number;
      y: number;
      target: HTMLElement;
    } | null = null;
    let isDragging = false;
    let didDrag = false;
    let angle = 0;

    const apply = () =>
      element.style.setProperty('--wheel-rot', `${wrap(rotation.current)}deg`);

    const angleAt = (x: number, y: number) => {
      const rect = element.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;
      return (Math.atan2(y - centerY, x - centerX) * 180) / Math.PI;
    };

    const logoOf = (target: EventTarget | null) =>
      target instanceof Element
        ? target.closest<HTMLElement>(`.${CSS.escape(styles.logo!)}`)
        : null;

    const onDown = (event: PointerEvent) => {
      const target = logoOf(event.target);
      // The slide is a sheet too: a press on a logo is the wheel's.
      if (target === null || event.button !== 0) return;
      event.stopPropagation();
      press = {
        pointerId: event.pointerId,
        x: event.clientX,
        y: event.clientY,
        target,
      };
      didDrag = false;
      velocity = 0;
    };

    const onMove = (event: PointerEvent) => {
      if (press === null || event.pointerId !== press.pointerId) return;
      if (!isDragging) {
        const travel = Math.hypot(
          event.clientX - press.x,
          event.clientY - press.y
        );
        if (travel < DRAG_SLOP_PX) return;
        isDragging = true;
        // Throws for a pointer the browser no longer tracks.
        try {
          press.target.setPointerCapture(event.pointerId);
        } catch {}
        didDrag = true;
        angle = angleAt(event.clientX, event.clientY);
        return;
      }
      const next = angleAt(event.clientX, event.clientY);
      let delta = next - angle;
      if (delta > 180) delta -= 360;
      else if (delta < -180) delta += 360;
      rotation.current = wrap(rotation.current + delta);
      velocity = delta;
      angle = next;
    };

    const onUp = (event: PointerEvent) => {
      if (press === null || event.pointerId !== press.pointerId) return;
      if (press.target.hasPointerCapture(event.pointerId)) {
        press.target.releasePointerCapture(event.pointerId);
      }
      press = null;
      isDragging = false;
    };

    // The spin waits while the mouse rests on a logo.
    const onOver = (event: PointerEvent) => {
      isHovered =
        event.pointerType === 'mouse' && logoOf(event.target) !== null;
    };
    const onOut = (event: PointerEvent) => {
      isHovered =
        event.pointerType === 'mouse' && logoOf(event.relatedTarget) !== null;
    };

    // The library follows touches on their own, so they stop here as well.
    const onTouchStart = (event: TouchEvent) => {
      if (logoOf(event.target) !== null) event.stopPropagation();
    };

    const onClick = (event: MouseEvent) => {
      if (!didDrag) return;
      event.preventDefault();
      event.stopPropagation();
    };

    let last = performance.now();
    let handle = requestAnimationFrame(function tick(now) {
      const dt = Math.min(now - last, 64);
      last = now;
      if (!isDragging) {
        if (Math.abs(velocity) > FLING_STOP) {
          rotation.current = wrap(rotation.current + velocity);
          velocity *= FLING_KEEP;
        } else if (!isHovered) {
          rotation.current = wrap(rotation.current + spin * dt);
        }
      }
      apply();
      handle = requestAnimationFrame(tick);
    });

    element.addEventListener('pointerdown', onDown);
    element.addEventListener('pointermove', onMove);
    element.addEventListener('pointerup', onUp);
    element.addEventListener('pointercancel', onUp);
    element.addEventListener('pointerover', onOver);
    element.addEventListener('pointerout', onOut);
    element.addEventListener('touchstart', onTouchStart, { passive: true });
    element.addEventListener('click', onClick, true);
    return () => {
      cancelAnimationFrame(handle);
      element.removeEventListener('pointerdown', onDown);
      element.removeEventListener('pointermove', onMove);
      element.removeEventListener('pointerup', onUp);
      element.removeEventListener('pointercancel', onUp);
      element.removeEventListener('pointerover', onOver);
      element.removeEventListener('pointerout', onOut);
      element.removeEventListener('touchstart', onTouchStart);
      element.removeEventListener('click', onClick, true);
    };
  }, [isActive]);

  return (
    <>
      <div className={styles.wheelFrame}>
        <div className={styles.wheel} ref={wheel}>
          {LOGOS.map(([name, file, href], i) => {
            const radians = (i / LOGOS.length) * 2 * Math.PI;
            const x = Math.cos(radians) * RADIUS;
            const y = Math.sin(radians) * RADIUS;
            return (
              <div
                key={name}
                className={styles.spoke}
                style={{
                  transform: `translate3d(-50%, -50%, 0) translate3d(${x}px, ${y}px, 0)`,
                }}
              >
                <div className={styles.logo}>
                  <Item name={name} file={file} href={href} />
                </div>
              </div>
            );
          })}
        </div>
      </div>
      <div className={styles.strip}>
        {/* Twice over, so moving it by half loops without a seam. */}
        <div className={styles.stripTrack} data-active={isActive}>
          {[0, 1].map((copy) => (
            <div
              key={copy}
              className={styles.stripCopy}
              aria-hidden={copy === 1}
              inert={copy === 1}
            >
              {LOGOS.map(([name, file, href]) => (
                <Item key={name} name={name} file={file} href={href} />
              ))}
            </div>
          ))}
        </div>
      </div>
    </>
  );
};
