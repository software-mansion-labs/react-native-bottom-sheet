import { useMemo, useRef, useState } from 'react';
import {
  Button,
  FlatList,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import {
  BottomSheet,
  ModalBottomSheet,
  programmatic,
} from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  DATA,
  INLINE_FLATLIST_PREVIEW_ITEMS,
  LIST_ITEM_HEIGHT,
  ListRow,
  MODAL_SCRIM_COLOR,
  SECTION_HEIGHT,
  SHEET_HEADER_HEIGHT,
  SheetBackground,
  SheetHeader,
} from './demoShared';

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

export const BasicModalScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Basic modal"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Basic modal" onClose={() => setIndex(0)} />
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Swipe down or tap scrim to dismiss</Text>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

export const ModalScrollViewScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Modal with ScrollView"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Modal with ScrollView"
            onClose={() => setIndex(0)}
          />
          <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
            {DATA.map((item, itemIndex) => (
              <ListRow key={item.id} item={item} index={itemIndex} />
            ))}
          </ScrollView>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

export const ModalFlatListScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Modal with FlatList"
      sheet={
        <ModalBottomSheet
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Modal with FlatList"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

export const ScrimOpacityScreen = () => {
  const [index, setIndex] = useState(0);
  const { height: windowHeight } = useWindowDimensions();

  return (
    <DemoScreen
      title="Per-detent scrim opacity"
      sheet={
        <ModalBottomSheet
          detents={[0, windowHeight / 2, 'content']}
          index={index}
          onIndexChange={setIndex}
          scrimColor={MODAL_SCRIM_COLOR}
          scrimOpacities={[0, 0.5, 1]}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Per-detent scrim opacity"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            ListHeaderComponent={
              <View style={{ padding: 20 }}>
                <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
                  With three detents and scrimOpacities={'{[0, 0.5, 1]}'}, the
                  scrim keeps deepening from the half detent to the full detent
                  instead of snapping to full opacity at the first open detent.
                  Drag between detents to watch it interpolate.
                </Text>
              </View>
            }
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

export const InlineDetentsScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Inline with detents"
      sheet={
        <BottomSheet
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Inline with detents"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text>Section 1</Text>
          </View>
          <View
            style={{
              height: SECTION_HEIGHT,
              alignItems: 'center',
              justifyContent: 'center',
              borderTopWidth: 1,
              borderTopColor: '#eee',
            }}
          >
            <Text>Section 2</Text>
          </View>
        </BottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

export const InlineFlatListScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Inline with FlatList"
      sheet={
        <BottomSheet
          detents={[
            0,
            SHEET_HEADER_HEIGHT +
              LIST_ITEM_HEIGHT * INLINE_FLATLIST_PREVIEW_ITEMS,
            'content',
          ]}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Inline with FlatList"
            onClose={() => setIndex(0)}
          />
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </BottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
    </DemoScreen>
  );
};

const INVALID_DETENTS_CONTENT_HEIGHT = 120;

export const InvalidDetentsScreen = () => {
  return (
    <DemoScreen
      title="Invalid detents"
      sheet={
        <BottomSheet
          detents={[120, 360, 'content']}
          index={1}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <View>
            <View
              style={{
                alignItems: 'center',
                paddingTop: 8,
                paddingBottom: 4,
              }}
            >
              <View
                style={{
                  width: 36,
                  height: 4,
                  borderRadius: 2,
                  backgroundColor: '#ddd',
                }}
              />
            </View>
            <View
              style={{
                height: 64,
                justifyContent: 'center',
                paddingHorizontal: 20,
                borderBottomWidth: 1,
                borderBottomColor: '#eee',
              }}
            >
              <Text style={{ fontSize: 20, fontWeight: 'bold' }}>
                Invalid detents
              </Text>
            </View>
          </View>
          <View
            style={{
              height: INVALID_DETENTS_CONTENT_HEIGHT,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              This sheet should not be allowed.
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              The 360pt detent is taller than the measured sheet content, so the
              native view should report an invalid detent error.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <Text>detents: [{`120, 360, 'content'`}]</Text>
    </DemoScreen>
  );
};

export const DisableScrollableNegotiationScreen = () => {
  const [index, setIndex] = useState(0);

  return (
    <DemoScreen
      title="Disable scrollable negotiation"
      sheet={
        <BottomSheet
          detents={[
            0,
            SHEET_HEADER_HEIGHT +
              LIST_ITEM_HEIGHT * INLINE_FLATLIST_PREVIEW_ITEMS,
            'content',
          ]}
          index={index}
          onIndexChange={setIndex}
          disableScrollableNegotiation
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Disable scrollable negotiation"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              paddingHorizontal: 16,
              paddingVertical: 12,
              borderBottomWidth: 1,
              borderBottomColor: '#eee',
              gap: 6,
            }}
          >
            <Text style={{ fontSize: 15, fontWeight: '600' }}>
              Gestures that start in the list stay with the list.
            </Text>
            <Text style={{ fontSize: 14, lineHeight: 20, color: '#555' }}>
              Try dragging on the rows when the list is already at the top or
              bottom. The sheet should not take over. Drag on the header to move
              the sheet instead.
            </Text>
          </View>
          <FlatList
            data={DATA}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ paddingBottom: 24 }}
            renderItem={({ item, index: itemIndex }) => (
              <ListRow item={item} index={itemIndex} />
            )}
          />
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Open at preview detent" onPress={() => setIndex(1)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
        <Button title="Collapse" onPress={() => setIndex(0)} />
      </View>
    </DemoScreen>
  );
};

export const ProgrammaticDetentDragScreen = () => {
  const [index, setIndex] = useState(0);
  const [position, setPosition] = useState(120);

  return (
    <DemoScreen
      title="Programmatic detent drag"
      sheet={
        <BottomSheet
          detents={[120, 320, programmatic(720)]}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Programmatic detent drag"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: 760,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Start at the programmatic detent
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              Open to 720pt, then drag lightly downward. The sheet should not
              jump to 320pt, and it should snap back to 720pt unless the drag
              clearly commits downward.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Snap to 120pt" onPress={() => setIndex(0)} />
        <Button title="Snap to 320pt" onPress={() => setIndex(1)} />
        <Button
          title="Snap programmatically to 720pt"
          onPress={() => setIndex(2)}
        />
      </View>
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 6,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Current state</Text>
        <Text>detents: [{`120, 320, programmatic(720)`}]</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};

export const DynamicDetentsScreen = () => {
  const [index, setIndex] = useState(0);
  const [middleDetent, setMiddleDetent] = useState(200);
  const [position, setPosition] = useState(0);
  const detents = useMemo(
    () => [0, middleDetent, 'content'] as const,
    [middleDetent]
  );

  return (
    <DemoScreen
      title="Dynamic detent updates"
      sheet={
        <BottomSheet
          detents={[...detents]}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Dynamic detent updates"
            onClose={() => setIndex(0)}
          />
          <View
            style={{
              height: 360,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Watch the middle detent animate
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              With the sheet at index 1, tap the 200pt and 300pt buttons above.
              The active detent should transition smoothly between the two
              heights.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Open at index 1" onPress={() => setIndex(1)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
        <Button title="Collapse" onPress={() => setIndex(0)} />
      </View>
      <View style={{ gap: 12 }}>
        <Button
          title="Use 200pt middle detent"
          onPress={() => setMiddleDetent(200)}
        />
        <Button
          title="Use 300pt middle detent"
          onPress={() => setMiddleDetent(300)}
        />
      </View>
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 6,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Current state</Text>
        <Text>detents: [{`0, ${middleDetent}, 'content'`}]</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};

const SHORT_CONTENT_HEIGHT = 160;
const TALL_CONTENT_HEIGHT = 440;

export const DynamicContentHeightScreen = () => {
  const [index, setIndex] = useState(0);
  const [contentHeight, setContentHeight] = useState(SHORT_CONTENT_HEIGHT);
  const [position, setPosition] = useState(0);

  return (
    <DemoScreen
      title="Dynamic content height"
      sheet={
        <ModalBottomSheet
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          onPositionChange={(event) => setPosition(event.nativeEvent.position)}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Dynamic content height"
            onClose={() => setIndex(0)}
          />
          <View style={{ padding: 20, gap: 12 }}>
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Resize the content while the sheet is open
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              Tap the buttons below to change the content height. Both growing
              and shrinking should animate smoothly, with the surface keeping
              the sheet covered so no blank space appears. The scrim should stay
              fully opaque throughout — it must not dip while the sheet
              re-anchors.
            </Text>
            <Button
              title="Short content"
              onPress={() => setContentHeight(SHORT_CONTENT_HEIGHT)}
            />
            <Button
              title="Tall content"
              onPress={() => setContentHeight(TALL_CONTENT_HEIGHT)}
            />
            <View
              style={{
                height: contentHeight,
                borderRadius: 16,
                backgroundColor: '#dbe7ff',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Text style={{ fontWeight: '600', color: '#345' }}>
                Resizable content · {contentHeight}pt
              </Text>
            </View>
          </View>
        </ModalBottomSheet>
      }
    >
      <Button title="Open sheet" onPress={() => setIndex(1)} />
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 6,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Current state</Text>
        <Text>content height: {contentHeight}pt</Text>
        <Text>index: {index}</Text>
        <Text>position: {position.toFixed(0)}pt</Text>
      </View>
    </DemoScreen>
  );
};

type SnapEventSource = 'press' | 'indexChange' | 'settle';
type SnapEvent = { id: number; source: SnapEventSource; index: number };

const SNAP_EVENT_LABEL: Record<SnapEventSource, string> = {
  press: '⊙ setIndex — programmatic begin',
  indexChange: '▶ onIndexChange — snap committed',
  settle: '■ onSettle — movement ended',
};

const SNAP_EVENT_COLOR: Record<SnapEventSource, string> = {
  press: '#8a5a1a',
  indexChange: '#1f6feb',
  settle: '#1a8a4a',
};

export const SnapCallbacksScreen = () => {
  const [index, setIndex] = useState(0);
  const [events, setEvents] = useState<SnapEvent[]>([]);
  const nextEventId = useRef(0);

  const logEvent = (source: SnapEventSource, eventIndex: number) => {
    setEvents((prev) =>
      [{ id: nextEventId.current++, source, index: eventIndex }, ...prev].slice(
        0,
        12
      )
    );
  };

  const snapTo = (nextIndex: number) => {
    // For a programmatic snap the "begin" moment is right here, at the call
    // site, so no callback is needed for it. onSettle still reports the end.
    logEvent('press', nextIndex);
    setIndex(nextIndex);
  };

  return (
    <DemoScreen
      title="Snap lifecycle callbacks"
      sheet={
        <BottomSheet
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          onIndexChange={(nextIndex) => {
            // Fires the instant a user-driven drag commits to a detent, before
            // the animation settles. Keep controlled state in sync here.
            logEvent('indexChange', nextIndex);
            setIndex(nextIndex);
          }}
          onSettle={(nextIndex) => logEvent('settle', nextIndex)}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader
            title="Snap lifecycle callbacks"
            onClose={() => snapTo(0)}
          />
          <View
            style={{
              height: SECTION_HEIGHT,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Drag the sheet or use the buttons
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              onIndexChange fires the instant a drag commits to a detent;
              onSettle fires when the sheet arrives. For a programmatic snap the
              begin moment is the button press itself, so only onSettle follows.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button title="Snap to collapsed" onPress={() => snapTo(0)} />
        <Button title="Snap to preview" onPress={() => snapTo(1)} />
        <Button title="Snap to content" onPress={() => snapTo(2)} />
      </View>
      <View
        style={{
          padding: 16,
          borderRadius: 16,
          backgroundColor: '#f3f3f3',
          gap: 8,
        }}
      >
        <Text style={{ fontWeight: '600' }}>Event log (newest first)</Text>
        {events.length === 0 ? (
          <Text style={{ color: '#888' }}>
            No events yet. Drag the sheet or tap a button.
          </Text>
        ) : (
          events.map((event) => (
            <Text
              key={event.id}
              style={{
                fontVariant: ['tabular-nums'],
                color: SNAP_EVENT_COLOR[event.source],
              }}
            >
              {SNAP_EVENT_LABEL[event.source]} → index {event.index}
            </Text>
          ))
        )}
      </View>
    </DemoScreen>
  );
};

export const NoAnimateInScreen = () => {
  const [mountKey, setMountKey] = useState(0);
  const [index, setIndex] = useState(1);

  return (
    <DemoScreen
      title="No animate in"
      sheet={
        <BottomSheet
          key={mountKey}
          animateIn={false}
          detents={[0, SHEET_HEADER_HEIGHT + SECTION_HEIGHT, 'content']}
          index={index}
          onIndexChange={setIndex}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="No animate in" onClose={() => setIndex(0)} />
          <View
            style={{
              height: SECTION_HEIGHT,
              paddingHorizontal: 20,
              justifyContent: 'center',
              gap: 12,
            }}
          >
            <Text style={{ fontSize: 18, fontWeight: '600' }}>
              Sheet should appear without sliding up
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: '#555' }}>
              With animateIn={'{false}'} and an initial index of 1, the sheet
              should be at its detent immediately on first layout. Tap "Remount
              sheet" to re-observe the initial layout — it must not animate in.
            </Text>
          </View>
        </BottomSheet>
      }
    >
      <View style={{ gap: 12 }}>
        <Button
          title="Remount sheet"
          onPress={() => {
            setIndex(1);
            setMountKey((value) => value + 1);
          }}
        />
        <Button title="Collapse" onPress={() => setIndex(0)} />
        <Button title="Expand to content" onPress={() => setIndex(2)} />
      </View>
    </DemoScreen>
  );
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
