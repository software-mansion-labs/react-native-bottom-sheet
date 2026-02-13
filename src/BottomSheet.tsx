import type { BottomSheetCommonProps } from './BottomSheetBase';
import { BottomSheetBase } from './BottomSheetBase';

export type BottomSheetProps = BottomSheetCommonProps;

export const BottomSheet = (props: BottomSheetProps) => (
  <BottomSheetBase {...props} />
);
