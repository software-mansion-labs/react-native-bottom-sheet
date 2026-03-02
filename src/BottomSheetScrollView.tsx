import type { Ref, ReactElement } from 'react';
import {
  ScrollView,
  type NativeScrollEvent,
  type ScrollViewProps,
} from 'react-native';
import type { SharedValue } from 'react-native-reanimated';

import { bottomSheetScrollable } from './bottomSheetScrollable';

export type BottomSheetScrollViewProps = Omit<
  ScrollViewProps,
  'onScroll' | 'scrollEnabled' | 'ref'
> & {
  scrollEnabled?: boolean | SharedValue<boolean | undefined>;
  onScroll?: (event: NativeScrollEvent) => void;
  ref?: Ref<ScrollView>;
};

export const BottomSheetScrollView = bottomSheetScrollable(ScrollView) as (
  props: BottomSheetScrollViewProps
) => ReactElement;
