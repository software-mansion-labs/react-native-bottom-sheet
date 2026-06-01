import { type Ref } from 'react';

import {
  BottomSheet,
  type BottomSheetInstance,
  type BottomSheetProps,
} from './BottomSheet';

/** Props for the modal bottom-sheet variant rendered through the provider portal. */
export interface ModalBottomSheetProps extends Omit<
  BottomSheetProps,
  'modal'
> {}

/** Bottom sheet presented above the current UI with a scrim. */
export const ModalBottomSheet = ({
  ref,
  ...props
}: ModalBottomSheetProps & { ref?: Ref<BottomSheetInstance> }) => (
  <BottomSheet {...props} ref={ref} modal />
);
