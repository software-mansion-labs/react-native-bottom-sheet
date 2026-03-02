import { createContext, useContext } from 'react';
import type { PanGesture } from 'react-native-gesture-handler';
import type { AnimatedRef, SharedValue } from 'react-native-reanimated';

export interface ScrollableEntry {
  ref: AnimatedRef<any>;
  scrollOffset: SharedValue<number>;
  isGestureActive: SharedValue<boolean>;
}

export interface BottomSheetContextType {
  translateY: SharedValue<number>;
  position: SharedValue<number>;
  index: SharedValue<number>;
  sheetHeight: SharedValue<number>;
  isScrollableLocked: SharedValue<boolean>;
  registerScrollable: (entry: ScrollableEntry) => () => void;
  panGesture: PanGesture;
}

const BottomSheetContext = createContext<BottomSheetContextType | null>(null);

export const BottomSheetContextProvider = BottomSheetContext.Provider;

export const useBottomSheetContext = () => {
  const context = useContext(BottomSheetContext);
  if (context === null) {
    throw new Error(
      '`useBottomSheetContext` must be used within `BottomSheet`.'
    );
  }
  return context;
};
