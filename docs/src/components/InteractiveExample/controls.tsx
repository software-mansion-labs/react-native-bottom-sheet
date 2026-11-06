import {
  useCallback,
  useId,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import clsx from 'clsx';

import styles from './styles.module.css';

// The row below the stage. These are plain DOM controls: they belong to the
// docs page, not to the example.

/** A column of the row below the stage. */
export const ControlGroup = ({ children }: { children: ReactNode }) => (
  <div className={styles.controlGroup}>{children}</div>
);

export const Control = ({
  label,
  value,
  children,
}: {
  label: string;
  value?: ReactNode;
  children: ReactNode;
}) => (
  <div className={styles.control}>
    <div className={styles.controlLabel}>
      <span>{label}</span>
      {value !== undefined && (
        <span className={styles.controlValue}>{value}</span>
      )}
    </div>
    {children}
  </div>
);

export const Hint = ({ children }: { children: ReactNode }) => (
  <p className={styles.hint}>{children}</p>
);

export const Slider = ({
  label,
  value,
  onChange,
  min = 0,
  max = 1,
  decimals = 2,
  formatValue,
  track,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  /** Rounds what's shown. The value itself moves continuously, without steps. */
  decimals?: number;
  /** Replaces the rounded number shown beside the label. */
  formatValue?: (value: number) => string;
  /** Paints the track, for sliders whose range is easier to see than to read. */
  track?: string;
}) => (
  <Control
    label={label}
    value={formatValue ? formatValue(value) : value.toFixed(decimals)}
  >
    <input
      type="range"
      className={clsx(
        styles.slider,
        track !== undefined && styles.sliderPainted
      )}
      style={
        track === undefined
          ? undefined
          : ({ '--slider-track': track } as CSSProperties)
      }
      aria-label={label}
      min={min}
      max={max}
      step="any"
      value={value}
      onChange={(event) => onChange(Number(event.target.value))}
    />
  </Control>
);

export const Toggle = ({
  label,
  value,
  onChange,
}: {
  label: ReactNode;
  value: boolean;
  onChange: (value: boolean) => void;
}) => (
  <label className={styles.toggle}>
    <span>{label}</span>
    <input
      type="checkbox"
      role="switch"
      checked={value}
      onChange={(event) => onChange(event.target.checked)}
    />
    <span className={styles.toggleTrack} aria-hidden />
  </label>
);

export const Segmented = <Value extends string | number>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: {
    value: Value;
    label: string;
    /** Shown instead of `label` when the control is too narrow to fit it. */
    compactLabel?: string | undefined;
    swatch?: string;
  }[];
  value: Value;
  onChange: (value: Value) => void;
}) => {
  const name = useId();

  return (
    <Control label={label}>
      <div className={styles.segmented} role="radiogroup" aria-label={label}>
        {options.map((option) => (
          <label
            key={option.value}
            className={clsx(
              styles.segment,
              option.value === value && styles.segmentActive
            )}
          >
            <input
              type="radio"
              name={name}
              aria-label={option.label}
              checked={option.value === value}
              onChange={() => onChange(option.value)}
            />
            {option.swatch !== undefined && (
              <span
                className={styles.swatch}
                style={{ backgroundColor: option.swatch }}
              />
            )}
            {option.compactLabel === undefined ? (
              option.label
            ) : (
              <>
                <span className={styles.segmentFullLabel}>{option.label}</span>
                <span className={styles.segmentCompactLabel}>
                  {option.compactLabel}
                </span>
              </>
            )}
          </label>
        ))}
      </div>
    </Control>
  );
};

export interface LogEntry {
  id: number;
  message: string;
}

export const useEventLog = () => {
  const [entries, setEntries] = useState<LogEntry[]>([]);
  const nextId = useRef(0);
  const log = useCallback((message: string) => {
    const id = nextId.current++;
    setEntries((current) => [{ id, message }, ...current].slice(0, 20));
  }, []);

  const clear = useCallback(() => setEntries([]), []);

  return { entries, log, clear };
};

/** Colors the leading callback or component, as in the docs' code blocks. */
const LogMessage = ({ message }: { message: string }) => {
  const [, head = message, rest] = /^(<[^>]*>|\w+)(.*)$/.exec(message) ?? [];

  return (
    <>
      <span
        className={
          message.startsWith('<') ? styles.logComponent : styles.logCallback
        }
      >
        {head}
      </span>
      {rest}
    </>
  );
};

export const EventLog = ({
  entries,
  onClear,
  empty,
}: {
  entries: LogEntry[];
  onClear: () => void;
  empty: string;
}) => (
  <Control
    label="Events"
    value={
      <button type="button" className={styles.logClear} onClick={onClear}>
        Clear
      </button>
    }
  >
    <ol className={styles.log} aria-live="polite">
      {entries.length === 0 && <li className={styles.logEmpty}>{empty}</li>}
      {entries.map((entry) => (
        <li key={entry.id} className={styles.logEntry}>
          <LogMessage message={entry.message} />
        </li>
      ))}
    </ol>
  </Control>
);
