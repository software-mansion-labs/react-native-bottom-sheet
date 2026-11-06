import {
  createContext,
  useContext,
  useLayoutEffect,
  useMemo,
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
  BottomSheet,
  BottomSheetProvider,
  ModalBottomSheet,
  type ModalDetent,
  type ModalDetentConfig,
} from '@swmansion/react-native-bottom-sheet';

import styles from './styles.module.css';

/** The widest a sheet gets, so it keeps its proportions on a wide stage. */
export const MAX_SHEET_WIDTH = 360;

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
      {/* Sheets mount once the width is known, as a sheet eases between insets. */}
      {width > 0 && (
        <StageWidthContext.Provider value={width}>
          <BottomSheetProvider>{children}</BottomSheetProvider>
        </StageWidthContext.Provider>
      )}
    </div>
  );
};

/** Centers what's behind the sheets, such as the button that opens them. */
export const StageContent = ({ children }: { children: ReactNode }) => (
  <View style={inner.content}>{children}</View>
);

type StageSheetProps = Omit<
  ComponentProps<typeof ModalBottomSheet>,
  'surface'
> & {
  /** Replaces the plain surface, for examples that paint their own. */
  surface?: ReactNode;
  surfaceColor?: string;
};

type StageInlineSheetProps = Omit<
  ComponentProps<typeof BottomSheet>,
  'surface'
> & {
  /** Replaces the plain surface, for examples that paint their own. */
  surface?: ReactNode;
  surfaceColor?: string;
};

const DEFAULT_DETENTS: ModalDetent[] = [0, 'content'];

const SHEET_SHAPE = {
  borderTopLeftRadius: 16,
  borderTopRightRadius: 16,
  boxShadow: '0 -2 16 rgba(0, 16, 72, 0.12)',
};

/** A detent's insets, with side gaps that center the sheet unless it sets its own. */
const withSideGaps = (detent: ModalDetent, gap: number): ModalDetentConfig => {
  const config = typeof detent === 'object' ? detent : { value: detent };
  return { ...config, insets: { left: gap, right: gap, ...config.insets } };
};

/** The shared sheet look, centered at `MAX_SHEET_WIDTH`. */
const useSheetLook = (
  detents: ReadonlyArray<ModalDetent>,
  surfaceColor?: string
) => {
  const gap = Math.max(
    0,
    (useContext(StageWidthContext) - MAX_SHEET_WIDTH) / 2
  );
  const surface = useMemo(
    () => (
      <View
        style={[
          StyleSheet.absoluteFill,
          { backgroundColor: surfaceColor ?? 'var(--example-surface)' },
        ]}
      />
    ),
    [surfaceColor]
  );

  return {
    // Examples load with their sheets already in place.
    animateIn: false,
    detents: detents.map((detent) => withSideGaps(detent, gap)),
    surfaceStyle: SHEET_SHAPE,
    surface,
  };
};

/** A modal sheet with the shared look. */
export const StageSheet = ({
  detents = DEFAULT_DETENTS,
  surfaceColor,
  ...props
}: StageSheetProps) => (
  <ModalBottomSheet {...useSheetLook(detents, surfaceColor)} {...props} />
);

/** An inline sheet with the shared look. */
export const StageInlineSheet = ({
  detents = DEFAULT_DETENTS,
  surfaceColor,
  ...props
}: StageInlineSheetProps) => (
  <BottomSheet {...useSheetLook(detents, surfaceColor)} {...props} />
);

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
    backgroundColor: 'var(--example-sheet-line)',
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
