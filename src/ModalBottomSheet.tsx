import type { ReactNode } from 'react';
import type { SharedValue } from 'react-native-reanimated';

import type { BottomSheetCommonProps } from './BottomSheetBase';
import { BottomSheetBase } from './BottomSheetBase';

export interface ModalBottomSheetProps extends BottomSheetCommonProps {
  scrim?: (progress: SharedValue<number>) => ReactNode;
}

export const ModalBottomSheet = ({
  scrim,
  ...props
}: ModalBottomSheetProps) => (
  <BottomSheetBase {...props} modal renderScrim={scrim} />
);
