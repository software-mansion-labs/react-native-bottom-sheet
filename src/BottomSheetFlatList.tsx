import { FlatList, type NativeScrollEvent } from 'react-native';
import type {
  FlatListPropsWithLayout,
  SharedValue,
} from 'react-native-reanimated';
import type { Ref, ReactElement } from 'react';

import { bottomSheetScrollable } from './bottomSheetScrollable';

export type BottomSheetFlatListProps<T> = Omit<
  FlatListPropsWithLayout<T>,
  'onScroll' | 'scrollEnabled' | 'ref'
> & {
  scrollEnabled?: boolean | SharedValue<boolean | undefined>;
  onScroll?: (event: NativeScrollEvent) => void;
  ref?: Ref<FlatList<T>>;
};

export const BottomSheetFlatList = bottomSheetScrollable(FlatList) as <T>(
  props: BottomSheetFlatListProps<T>
) => ReactElement;
