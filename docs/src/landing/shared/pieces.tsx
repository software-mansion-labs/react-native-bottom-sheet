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

/** Whether this slide is the one showing, so hidden ones can stop their work. */
export const SlideActiveContext = createContext(true);

/** Fires once, when the element comes near the viewport. */
export const useInView = <T extends HTMLElement>(margin = '200px') => {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        // One batch can hold several changes: the last one is the current.
        if (entries.at(-1)!.isIntersecting) {
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
