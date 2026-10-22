import type { ComponentProps } from 'react';
import type { BottomSheetProps } from '@swmansion/react-native-bottom-sheet';

type NativeProps = ComponentProps<
  Parameters<NonNullable<BottomSheetProps['wrapNativeView']>>[0]
>;
type IndexEvent = Parameters<
  NonNullable<NativeProps['onIndexChange']>
>[0]['nativeEvent']['index'];

export const validIndex: NativeProps['index'] = 1;
export const validEventIndex: IndexEvent = 1;
// @ts-expect-error The published native wrapper requires a numeric index.
export const invalidIndex: NativeProps['index'] = 'bad';
// @ts-expect-error The published event retains its numeric payload.
export const invalidEventIndex: IndexEvent = 'bad';
