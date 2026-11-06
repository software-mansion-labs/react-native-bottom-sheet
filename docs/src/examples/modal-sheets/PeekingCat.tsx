import { useEffect, useRef } from 'react';

import styles from './PeekingCat.module.css';

const FUR = '#f2a65a';
const FUR_DARK = '#d27f36';
const CREAM = '#fff4e4';
const PINK = '#f7b5b0';
const INK = '#3a2a2a';

const GREETING_INTERVAL_MS = 10000;
// Waits for the sheet to finish opening, so the first wave gets noticed.
const FIRST_GREETING_DELAY_MS = 800;

const BLINK: Keyframe[] = [
  { transform: 'scaleY(1)' },
  { transform: 'scaleY(0.08)' },
  { transform: 'scaleY(1)' },
];

const WAVE: Keyframe[] = [
  { transform: 'translateY(0) rotate(0deg)' },
  { transform: 'translateY(-8px) rotate(-12deg)', offset: 0.18 },
  { transform: 'translateY(-8px) rotate(12deg)', offset: 0.38 },
  { transform: 'translateY(-8px) rotate(-12deg)', offset: 0.58 },
  { transform: 'translateY(-8px) rotate(12deg)', offset: 0.78 },
  { transform: 'translateY(0) rotate(0deg)' },
];

/**
 * Peeks over the bottom edge of its container, looking up at the reader. While
 * `active`, it blinks and waves every 10 seconds. These are one-off animations
 * rather than a CSS loop, so nothing runs between greetings or while inactive.
 */
export const PeekingCat = ({ active }: { active: boolean }) => {
  const eyesRef = useRef<SVGGElement>(null);
  const pawRef = useRef<SVGGElement>(null);

  useEffect(() => {
    if (!active) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const eyes = eyesRef.current!;
    const paw = pawRef.current!;
    const greet = () => {
      eyes.animate(BLINK, { duration: 240, easing: 'ease-in-out' });
      paw.animate(WAVE, { duration: 1300, easing: 'ease-in-out' });
    };

    const firstGreeting = setTimeout(greet, FIRST_GREETING_DELAY_MS);
    const greetings = setInterval(greet, GREETING_INTERVAL_MS);

    return () => {
      clearTimeout(firstGreeting);
      clearInterval(greetings);
      for (const element of [eyes, paw]) {
        element.getAnimations().forEach((animation) => animation.cancel());
      }
    };
  }, [active]);

  return (
    <svg viewBox="0 0 150 84" className={styles.cat} aria-hidden>
      {/* Ears, wide apart and pointed */}
      <path d="M17 42 L13 5 Q12 0 17 2 L52 23 Z" fill={FUR} />
      <path d="M103 42 L107 5 Q108 0 103 2 L68 23 Z" fill={FUR} />
      <path d="M23 31 L20 11 L40 23 Z" fill={PINK} />
      <path d="M97 31 L100 11 L80 23 Z" fill={PINK} />

      {/* Head: wider than tall, with a flat top and fluffy cheeks */}
      <path
        d="M60 18
         C38 18 19 26 15 46
         C14 52 13 58 10 63
         L16 64 L8 74 L17 74 L12 90
         H108 L103 74 L112 74 L104 64 L110 63
         C107 58 106 52 105 46
         C101 26 82 18 60 18 Z"
        fill={FUR}
      />

      {/* Tabby stripes on the forehead and cheeks */}
      <path
        d="M60 19 V31 M49 20 L51 30 M71 20 L69 30
         M18 52 L28 54 M17 60 L27 60
         M102 52 L92 54 M103 60 L93 60"
        stroke={FUR_DARK}
        strokeWidth="3"
        strokeLinecap="round"
      />

      {/* Eyes, with the highlights high up, so it looks at you */}
      <g ref={eyesRef} className={styles.eyes}>
        <ellipse cx="40" cy="46" rx="8" ry="9" fill={INK} />
        <ellipse cx="80" cy="46" rx="8" ry="9" fill={INK} />
        <circle cx="42.5" cy="42.5" r="3" fill="#ffffff" />
        <circle cx="82.5" cy="42.5" r="3" fill="#ffffff" />
      </g>

      {/* Muzzle: two soft cheeks under a small nose, and a "w" mouth */}
      <ellipse cx="51" cy="68" rx="11" ry="9" fill={CREAM} />
      <ellipse cx="69" cy="68" rx="11" ry="9" fill={CREAM} />
      <path d="M56 58 H64 L60 63 Z" fill={PINK} />
      <path
        d="M60 63 V65 M60 65 Q56 71 51 67 M60 65 Q64 71 69 67"
        fill="none"
        stroke={INK}
        strokeWidth="1.8"
        strokeLinecap="round"
      />

      {/* Whiskers */}
      <path
        d="M40 64 L18 60 M40 69 L18 71 M80 64 L102 60 M80 69 L102 71"
        stroke={INK}
        strokeWidth="1.3"
        strokeLinecap="round"
        opacity="0.6"
      />

      {/* Paw, holding on to the edge */}
      <g ref={pawRef} className={styles.paw}>
        <rect x="115" y="58" width="29" height="38" rx="14" fill={FUR} />
        <path
          d="M124 59 V67 M135 59 V67"
          stroke={FUR_DARK}
          strokeWidth="2.4"
          strokeLinecap="round"
        />
      </g>
    </svg>
  );
};
