import {
  createContext,
  useEffect,
  useRef,
  useState,
  type ComponentProps,
} from 'react';
import clsx from 'clsx';

import landing from '../landing.module.css';

export const clamp = (value: number, min = 0, max = 1) =>
  Math.min(max, Math.max(min, value));

export const prefersReducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Whether this slide is the one showing, so hidden ones can stop their work. */
export const SlideActiveContext = createContext(true);

/** Becomes true once the browser has time, so mounting never lands mid-animation. */
export const useWhenIdle = () => {
  const [idle, setIdle] = useState(false);
  useEffect(() => {
    if (typeof requestIdleCallback === 'undefined') {
      const timer = setTimeout(() => setIdle(true), 300);
      return () => clearTimeout(timer);
    }
    const handle = requestIdleCallback(() => setIdle(true), { timeout: 1500 });
    return () => cancelIdleCallback(handle);
  }, []);
  return idle;
};

/** Fires once, when the element comes near the viewport. */
export const useInView = <T extends HTMLElement>(margin = '200px') => {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry!.isIntersecting) {
          setInView(true);
          observer.disconnect();
        }
      },
      { rootMargin: margin }
    );
    observer.observe(ref.current!);
    return () => observer.disconnect();
  }, [margin]);
  return [ref, inView] as const;
};

export const Arrow = () => (
  <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
    <path
      d="M3 9h11M10 4.5 14.5 9 10 13.5"
      stroke="currentColor"
      strokeWidth="1.6"
    />
  </svg>
);

export const Icon = ({
  d,
  size = 22,
  fill = 'none',
  strokeWidth = 1.8,
}: {
  d: string;
  size?: number;
  fill?: string;
  strokeWidth?: number;
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill={fill}
    stroke={fill === 'none' ? 'currentColor' : 'none'}
    strokeWidth={strokeWidth}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden
  >
    <path d={d} />
  </svg>
);

export const Reveal = ({ className, ...props }: ComponentProps<'div'>) => {
  const [ref, inView] = useInView<HTMLDivElement>('-8% 0px');
  return (
    <div
      ref={ref}
      className={clsx(landing.reveal, className)}
      data-in={inView}
      {...props}
    />
  );
};
