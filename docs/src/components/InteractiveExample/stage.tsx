import {
  createContext,
  useContext,
  useLayoutEffect,
  useRef,
  useState,
  type ComponentProps,
  type ReactNode,
} from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import {
  BottomSheetProvider,
  ModalBottomSheet,
} from '@swmansion/react-native-bottom-sheet';

import styles from './styles.module.css';

/** The widest a sheet gets, so it keeps its proportions on a wide stage. */
const MAX_SHEET_WIDTH = 360;

const StageWidthContext = createContext(0);

/**
 * The area an example plays in. The provider fills it, so modal sheets and
 * their scrims stay on the stage rather than covering the page.
 */
export const Stage = ({
  height,
  children,
}: {
  height: number;
  children: ReactNode;
}) => {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);

  useLayoutEffect(() => {
    const element = ref.current!;
    const observer = new ResizeObserver(() => setWidth(element.clientWidth));
    setWidth(element.clientWidth);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={ref} className={styles.stage} style={{ height }}>
      <StageWidthContext.Provider value={width}>
        <BottomSheetProvider>{children}</BottomSheetProvider>
      </StageWidthContext.Provider>
    </div>
  );
};

/**
 * Centers what's behind the sheets, such as the button that opens them. Pass
 * `hidden` while a sheet is open, so the sheet doesn't sit on top of it.
 */
export const StageContent = ({
  hidden,
  children,
}: {
  hidden?: boolean;
  children: ReactNode;
}) => (
  <View
    style={[inner.content, { opacity: hidden ? 0 : 1 }]}
    pointerEvents={hidden ? 'none' : 'auto'}
  >
    {children}
  </View>
);

type StageSheetProps = Omit<
  ComponentProps<typeof ModalBottomSheet>,
  'surface'
> & {
  surfaceColor?: string;
};

/** A modal sheet with the shared look, centered at `MAX_SHEET_WIDTH`. */
export const StageSheet = ({ surfaceColor, ...props }: StageSheetProps) => {
  const gap = Math.max(
    0,
    (useContext(StageWidthContext) - MAX_SHEET_WIDTH) / 2
  );

  return (
    <ModalBottomSheet
      // Examples load with their sheets already in place.
      animateIn={false}
      insets={[{ left: gap, right: gap }]}
      surfaceStyle={{
        borderTopLeftRadius: 16,
        borderTopRightRadius: 16,
        boxShadow: '0 -2 16 rgba(0, 16, 72, 0.12)',
      }}
      surface={
        <View
          style={[
            StyleSheet.absoluteFill,
            { backgroundColor: surfaceColor ?? 'var(--example-surface)' },
          ]}
        />
      }
      {...props}
    />
  );
};

export const StageButton = ({
  label,
  onPress,
  secondary,
}: {
  label: string;
  onPress: () => void;
  secondary?: boolean;
}) => (
  <Pressable
    accessibilityRole="button"
    onPress={onPress}
    style={({ pressed }) => [
      inner.button,
      secondary && inner.buttonSecondary,
      { transform: [{ scale: pressed ? 0.97 : 1 }] },
    ]}
  >
    <Text style={[inner.buttonLabel, secondary && inner.buttonLabelSecondary]}>
      {label}
    </Text>
  </Pressable>
);

/** Sheet content with a grabber and a title. */
export const SheetBody = ({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) => (
  <View style={inner.sheetBody}>
    <View style={inner.grabber} />
    <Text style={inner.sheetTitle}>{title}</Text>
    {children}
  </View>
);

export const SheetText = ({ children }: { children: ReactNode }) => (
  <Text style={inner.sheetText}>{children}</Text>
);

const fontFamily = 'var(--ifm-font-family-base)';

const inner = StyleSheet.create({
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    padding: 16,
    // Web-only: fades the content as a sheet opens or closes.
    ...({
      transitionProperty: 'opacity',
      transitionDuration: '160ms',
    } as ViewStyle),
  },
  // Matches the buttons in the Reanimated docs' examples.
  button: {
    alignSelf: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 2,
    backgroundColor: 'var(--example-button)',
    // Web-only: eases the press scale.
    ...({
      transitionProperty: 'transform',
      transitionDuration: '140ms',
      transitionTimingFunction: 'cubic-bezier(0.23, 1, 0.32, 1)',
    } as ViewStyle),
  },
  buttonSecondary: {
    backgroundColor: 'var(--example-muted-surface)',
  },
  buttonLabel: {
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    fontSize: 14,
    fontWeight: '500',
    textTransform: 'uppercase',
    color: '#ffffff',
  },
  buttonLabelSecondary: {
    color: 'var(--example-text)',
  },
  sheetBody: {
    paddingHorizontal: 20,
    paddingBottom: 20,
    gap: 10,
  },
  grabber: {
    alignSelf: 'center',
    width: 32,
    height: 4,
    borderRadius: 2,
    marginTop: 8,
    marginBottom: 6,
    backgroundColor: 'var(--example-track)',
  },
  sheetTitle: {
    fontFamily,
    fontSize: 18,
    fontWeight: '500',
    color: 'var(--example-text)',
  },
  sheetText: {
    fontFamily,
    fontSize: 15,
    lineHeight: 22,
    color: 'var(--example-muted)',
  },
});
