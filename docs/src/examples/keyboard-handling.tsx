import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { BottomSheetProvider } from '@swmansion/react-native-bottom-sheet';
import clsx from 'clsx';

import {
  ControlGroup,
  Example,
  Hint,
  interactiveExample,
  MAX_SHEET_WIDTH,
  SheetBody,
  StageButton,
  StageContent,
  StageSheet,
  Toggle,
} from '../components/InteractiveExample';
import styles from './keyboard-handling.module.css';

// Its keys have the proportions of an iPhone's, at the width of the sheet.
const KEYBOARD_HEIGHT = 204;

const LETTERS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'] as const;
const SYMBOLS = ['1234567890', '-/:;()$&@"', ".,?!'"] as const;

const SimulatedKeyboard = ({
  isOpen,
  onType,
  onDelete,
  onDone,
}: {
  isOpen: boolean;
  onType: (text: string) => void;
  onDelete: () => void;
  onDone: () => void;
}) => {
  const [isShifted, setIsShifted] = useState(true);
  const [showsSymbols, setShowsSymbols] = useState(false);
  const [top, middle, bottom] = showsSymbols ? SYMBOLS : LETTERS;

  const renderKeys = (keys: string) =>
    [...keys].map((key) => {
      const label = isShifted ? key.toUpperCase() : key;
      return (
        <button
          key={key}
          type="button"
          tabIndex={-1}
          className={styles.key}
          onClick={() => {
            onType(label);
            setIsShifted(false);
          }}
        >
          {label}
        </button>
      );
    });

  return (
    <div
      className={clsx(styles.keyboard, isOpen && styles.keyboardOpen)}
      style={{ height: KEYBOARD_HEIGHT, maxWidth: MAX_SHEET_WIDTH }}
      inert={!isOpen}
      aria-hidden
      // Pressing a key keeps the focus in the field.
      onMouseDown={(event) => event.preventDefault()}
    >
      <div className={styles.row}>{renderKeys(top)}</div>
      <div className={clsx(styles.row, !showsSymbols && styles.rowInset)}>
        {renderKeys(middle)}
      </div>
      <div className={styles.row}>
        {!showsSymbols && (
          <button
            type="button"
            tabIndex={-1}
            className={clsx(styles.key, styles.keySpecial)}
            onClick={() => setIsShifted((shifted) => !shifted)}
          >
            {isShifted ? '⬆' : '⇧'}
          </button>
        )}
        {renderKeys(bottom)}
        <button
          type="button"
          tabIndex={-1}
          className={clsx(styles.key, styles.keySpecial)}
          onClick={onDelete}
        >
          ⌫
        </button>
      </div>
      <div className={styles.row}>
        <button
          type="button"
          tabIndex={-1}
          className={clsx(styles.key, styles.keySpecial, styles.keyWide)}
          onClick={() => setShowsSymbols((shows) => !shows)}
        >
          {showsSymbols ? 'ABC' : '123'}
        </button>
        <button
          type="button"
          tabIndex={-1}
          className={clsx(styles.key, styles.keySpace)}
          onClick={() => onType(' ')}
        >
          space
        </button>
        <button
          type="button"
          tabIndex={-1}
          className={clsx(
            styles.key,
            styles.keySpecial,
            styles.keyWide,
            styles.keyDone
          )}
          onClick={onDone}
        >
          done
        </button>
      </div>
    </div>
  );
};

/** Opens from the bottom of the screen, as that's where the keyboard opens. */
const DeviceKeyboardSheet = ({ onClosed }: { onClosed: () => void }) => {
  const [index, setIndex] = useState(1);

  return createPortal(
    <div className={styles.screen}>
      <BottomSheetProvider>
        <StageSheet
          animateIn
          detents={[0, { value: 'content', insets: { left: 0, right: 0 } }]}
          animateContentHeight={false}
          index={index}
          onIndexChange={setIndex}
          onSettle={(settled) => {
            if (settled === 0) onClosed();
          }}
          scrimColor="rgba(0, 0, 0, 0.4)"
        >
          <SheetBody title="Add a comment">
            <input className={styles.field} placeholder="Tap to type" />
          </SheetBody>
        </StageSheet>
      </BottomSheetProvider>
    </div>,
    document.body
  );
};

const paddingCode = (hasPadding: boolean, animates: boolean) => {
  const sheetProps = `detents={[0, 'content']}${animates ? '' : '\n  animateContentHeight={false}'}
  index={index}
  onIndexChange={setIndex}
  surface={/* ... */}`;

  return hasPadding
    ? `import { useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';

const [index, setIndex] = useState(1);
const { height } = useReanimatedKeyboardAnimation();

// The keyboard's height is negative, as it moves content up.
const paddingStyle = useAnimatedStyle(() => ({
  paddingBottom: -height.value,
}));

<ModalBottomSheet
  ${sheetProps}
>
  <Animated.View style={paddingStyle}>
    <TextInput placeholder="Tap to type" />
  </Animated.View>
</ModalBottomSheet>;`
    : `const [index, setIndex] = useState(1);

<ModalBottomSheet
  ${sheetProps}
>
  <TextInput placeholder="Tap to type" />
</ModalBottomSheet>;`;
};

const DeviceKeyboardExample = () => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <Example
      stage={
        <>
          <StageContent>
            <StageButton label="Open sheet" onPress={() => setIsOpen(true)} />
          </StageContent>
          {isOpen && <DeviceKeyboardSheet onClosed={() => setIsOpen(false)} />}
        </>
      }
      controls={
        <ControlGroup>
          <Hint>
            Open the sheet and tap the field to bring up your keyboard.
          </Hint>
        </ControlGroup>
      }
      code={paddingCode(true, false)}
    />
  );
};

const SimulatedKeyboardExample = () => {
  const [index, setIndex] = useState(1);
  const [text, setText] = useState('');
  const [isKeyboardOpen, setIsKeyboardOpen] = useState(false);
  const [hasPadding, setHasPadding] = useState(true);
  const [animates, setAnimates] = useState(false);
  const fieldRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isKeyboardOpen) fieldRef.current?.focus({ preventScroll: true });
    else fieldRef.current?.blur();
  }, [isKeyboardOpen]);

  return (
    <Example
      stage={
        <>
          {index === 0 && (
            <StageContent>
              <StageButton label="Open sheet" onPress={() => setIndex(1)} />
            </StageContent>
          )}
          <StageSheet
            detents={[0, 'content']}
            animateContentHeight={animates}
            index={index}
            onIndexChange={(next) => {
              setIndex(next);
              if (next === 0) setIsKeyboardOpen(false);
            }}
            scrimColor="rgba(0, 0, 0, 0.4)"
          >
            <SheetBody title="Add a comment">
              <input
                ref={fieldRef}
                className={styles.field}
                placeholder="Tap to type"
                value={text}
                onChange={(event) => setText(event.target.value)}
                onFocus={() => setIsKeyboardOpen(true)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') setIsKeyboardOpen(false);
                }}
              />
            </SheetBody>
            {/* A spacer, as on web the sheet watches its children's content
                  boxes, which their padding doesn't change. */}
            <div
              className={styles.padding}
              style={{
                height: hasPadding && isKeyboardOpen ? KEYBOARD_HEIGHT : 0,
              }}
            />
          </StageSheet>
          <SimulatedKeyboard
            isOpen={isKeyboardOpen}
            onType={(typed) => setText((current) => current + typed)}
            onDelete={() => setText((current) => current.slice(0, -1))}
            onDone={() => setIsKeyboardOpen(false)}
          />
        </>
      }
      controls={
        <ControlGroup>
          <Toggle
            label="Simulated keyboard"
            value={isKeyboardOpen}
            onChange={(isOpen) => {
              setIsKeyboardOpen(isOpen);
              if (isOpen) setIndex(1);
            }}
          />
          <Toggle
            label="Keyboard padding"
            value={hasPadding}
            onChange={setHasPadding}
          />
          <Hint>
            The sheet only knows about its content, so without the padding the
            keyboard covers it. With <code>animateContentHeight</code>, the
            sheet adds its own resize spring and falls behind the keyboard.
          </Hint>
          <Toggle
            label="animateContentHeight"
            value={animates}
            onChange={setAnimates}
          />
          <div className={styles.disclaimer}>
            <div className={styles.disclaimerTitle}>
              <svg
                className={styles.disclaimerIcon}
                viewBox="0 0 16 16"
                aria-hidden
              >
                <circle cx="8" cy="8" r="6.5" />
                <path d="M8 4.5v4.5M8 11.25v.25" />
              </svg>
              Disclaimer
            </div>
            <Hint>
              This keyboard is simulated, only to show how the sheet behaves on
              a phone. Try this page on a mobile device to see the real one.
            </Hint>
          </div>
        </ControlGroup>
      }
      code={paddingCode(hasPadding, animates)}
    />
  );
};

// The device's keyboard opens at the bottom of the screen, outside of the stage,
// so only a device without one gets a simulated keyboard to play with there.
export const KeyboardPaddingExample = interactiveExample(
  () =>
    window.matchMedia('(hover: none)').matches ? (
      <DeviceKeyboardExample />
    ) : (
      <SimulatedKeyboardExample />
    ),
  { height: 420 }
);
