export type CaseKey =
  | 'basic-modal'
  | 'native-overlay-modal'
  | 'native-overlay-nested'
  | 'modal-scroll-view'
  | 'modal-flat-list'
  | 'keyboard-content-detent'
  | 'keyboard-aware-list'
  | 'scrim-opacity'
  | 'inline-detents'
  | 'under-status-bar'
  | 'content-larger-detent'
  | 'inline-flat-list'
  | 'inline-nested-flat-list'
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
  href: `/${CaseKey}`;
  throws?: boolean;
};

export const DEMO_CASES: DemoCase[] = [
  {
    key: 'basic-modal',
    title: 'Basic modal',
    href: '/basic-modal',
  },
  {
    key: 'native-overlay-modal',
    title: 'Native overlay modal',
    href: '/native-overlay-modal',
  },
  {
    key: 'native-overlay-nested',
    title: 'Native overlay nested mount',
    href: '/native-overlay-nested',
  },
  {
    key: 'modal-scroll-view',
    title: 'Modal with ScrollView',
    href: '/modal-scroll-view',
  },
  {
    key: 'modal-flat-list',
    title: 'Modal with FlatList',
    href: '/modal-flat-list',
  },
  {
    key: 'keyboard-content-detent',
    title: 'Keyboard content detent',
    href: '/keyboard-content-detent',
  },
  {
    key: 'keyboard-aware-list',
    title: 'Keyboard-aware list',
    href: '/keyboard-aware-list',
  },
  {
    key: 'scrim-opacity',
    title: 'Per-detent scrim opacity',
    href: '/scrim-opacity',
  },
  {
    key: 'inline-detents',
    title: 'Inline with detents',
    href: '/inline-detents',
  },
  {
    key: 'under-status-bar',
    title: 'Under status bar',
    href: '/under-status-bar',
  },
  {
    key: 'content-larger-detent',
    title: 'Content plus larger detent',
    href: '/content-larger-detent',
  },
  {
    key: 'inline-flat-list',
    title: 'Inline with FlatList',
    href: '/inline-flat-list',
  },
  {
    key: 'inline-nested-flat-list',
    title: 'Inline with nested FlatLists',
    href: '/inline-nested-flat-list',
  },
  {
    key: 'invalid-detents',
    title: 'Invalid detents',
    href: '/invalid-detents',
    throws: true,
  },
  {
    key: 'disable-scrollable-negotiation',
    title: 'Disable scrollable negotiation',
    href: '/disable-scrollable-negotiation',
  },
  {
    key: 'programmatic-detent-drag',
    title: 'Programmatic detent drag',
    href: '/programmatic-detent-drag',
  },
  {
    key: 'dynamic-detents',
    title: 'Dynamic detent updates',
    href: '/dynamic-detents',
  },
  {
    key: 'dynamic-content-height',
    title: 'Dynamic content height',
    href: '/dynamic-content-height',
  },
  {
    key: 'snap-callbacks',
    title: 'Snap lifecycle callbacks',
    href: '/snap-callbacks',
  },
  {
    key: 'no-animate-in',
    title: 'No animate in',
    href: '/no-animate-in',
  },
  {
    key: 'ui-thread-position',
    title: 'UI-thread onPositionChange',
    href: '/ui-thread-position',
  },
  {
    key: 'ui-thread-modal-position',
    title: 'UI-thread modal onPositionChange',
    href: '/ui-thread-modal-position',
  },
];
