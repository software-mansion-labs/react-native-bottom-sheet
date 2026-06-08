export type CaseKey =
  | 'basic-modal'
  | 'modal-scroll-view'
  | 'modal-flat-list'
  | 'scrim-opacity'
  | 'inline-detents'
  | 'inline-flat-list'
  | 'invalid-detents'
  | 'disable-scrollable-negotiation'
  | 'programmatic-detent-drag'
  | 'dynamic-detents'
  | 'dynamic-content-height'
  | 'snap-callbacks'
  | 'no-animate-in'
  | 'ui-thread-position'
  | 'ui-thread-modal-position';

export type DemoCase = {
  key: CaseKey;
  title: string;
  description: string;
  href: `/${CaseKey}`;
  throws?: boolean;
};

export const DEMO_CASES: DemoCase[] = [
  {
    key: 'basic-modal',
    title: 'Basic modal',
    description: 'Simple modal bottom sheet with a fixed-height body.',
    href: '/basic-modal',
  },
  {
    key: 'modal-scroll-view',
    title: 'Modal with ScrollView',
    description: 'Modal bottom sheet containing a vertical ScrollView.',
    href: '/modal-scroll-view',
  },
  {
    key: 'modal-flat-list',
    title: 'Modal with FlatList',
    description: 'Modal bottom sheet containing a FlatList.',
    href: '/modal-flat-list',
  },
  {
    key: 'scrim-opacity',
    title: 'Per-detent scrim opacity',
    description:
      'Three-detent modal with scrimOpacities={[0, 0.5, 1]} so the scrim deepens at every detent.',
    href: '/scrim-opacity',
  },
  {
    key: 'inline-detents',
    title: 'Inline with detents',
    description: 'Inline sheet with fixed and content detents.',
    href: '/inline-detents',
  },
  {
    key: 'inline-flat-list',
    title: 'Inline with FlatList',
    description: 'Inline sheet with FlatList content and preview detent.',
    href: '/inline-flat-list',
  },
  {
    key: 'invalid-detents',
    title: 'Invalid detents',
    description: 'Inline sheet with a fixed detent taller than its content.',
    href: '/invalid-detents',
    throws: true,
  },
  {
    key: 'disable-scrollable-negotiation',
    title: 'Disable scrollable negotiation',
    description:
      'Inline sheet showing that list gestures stay with the touched scrollable.',
    href: '/disable-scrollable-negotiation',
  },
  {
    key: 'programmatic-detent-drag',
    title: 'Programmatic detent drag',
    description:
      'Drag from a programmatic detent without exposing it as a normal target.',
    href: '/programmatic-detent-drag',
  },
  {
    key: 'dynamic-detents',
    title: 'Dynamic detent updates',
    description:
      'Toggle the middle detent while index 1 is active to verify animated updates.',
    href: '/dynamic-detents',
  },
  {
    key: 'dynamic-content-height',
    title: 'Dynamic content height',
    description:
      'Resize the content of a modal sheet: grow animates, shrink snaps, scrim stays opaque.',
    href: '/dynamic-content-height',
  },
  {
    key: 'snap-callbacks',
    title: 'Snap lifecycle callbacks',
    description:
      'Logs onIndexChange (snap committed) and onSettle (movement ended) for drags and programmatic snaps.',
    href: '/snap-callbacks',
  },
  {
    key: 'no-animate-in',
    title: 'No animate in',
    description:
      'Inline sheet with animateIn={false}: it should appear at its detent without sliding up.',
    href: '/no-animate-in',
  },
  {
    key: 'ui-thread-position',
    title: 'UI-thread onPositionChange',
    description:
      'createAnimatedComponent(BottomSheet) with a Reanimated worklet handling onPositionChange synchronously on the UI thread.',
    href: '/ui-thread-position',
  },
  {
    key: 'ui-thread-modal-position',
    title: 'UI-thread modal onPositionChange',
    description:
      'createAnimatedComponent(ModalBottomSheet): a worklet onPositionChange on a portal-rendered modal, via the in-place host anchor.',
    href: '/ui-thread-modal-position',
  },
];
