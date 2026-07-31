import {
  createContext,
  useContext,
  useId,
  useLayoutEffect,
  useState,
  useSyncExternalStore,
} from 'react';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

type PortalContent = ReactNode | ((isTopmost: boolean) => ReactNode);

type PortalEntry = Readonly<{
  content: PortalContent;
  open: boolean;
}>;

type PortalSnapshot = Array<[string, PortalEntry]>;

interface PortalContextType {
  addPortal: (key: string, portal: PortalEntry) => void;
  removePortal: (key: string) => void;
  subscribe: (callback: () => void) => () => void;
  getSnapshot: () => PortalSnapshot;
}

const PortalContext = createContext<PortalContextType | null>(null);

const PortalHost = () => {
  const context = useContext(PortalContext)!;
  const portals = useSyncExternalStore(
    context.subscribe,
    context.getSnapshot,
    context.getSnapshot
  );

  // The snapshot follows the Map's stable render order. Updating an existing
  // portal never moves it, so the last open entry is also visually topmost.
  let topmostOpenPortal: string | undefined;
  for (const [key, portal] of portals) {
    if (portal.open) {
      topmostOpenPortal = key;
    }
  }

  return portals.map(([key, portal]) => {
    const element =
      typeof portal.content === 'function'
        ? portal.content(key === topmostOpenPortal)
        : portal.content;

    return (
      <View key={key} style={StyleSheet.absoluteFill} pointerEvents="box-none">
        {element}
      </View>
    );
  });
};

/** Provides the portal host required for modal bottom sheets. */
export const BottomSheetProvider = ({ children }: { children: ReactNode }) => {
  const [context] = useState<PortalContextType>(() => {
    const portals = new Map<string, PortalEntry>();
    const subscribers = new Set<() => void>();
    let snapshot: PortalSnapshot = [];
    const notify = () => {
      snapshot = Array.from(portals.entries());
      subscribers.forEach((subscriber) => subscriber());
    };
    return {
      addPortal: (key, element) => {
        portals.set(key, element);
        notify();
      },
      removePortal: (key) => {
        portals.delete(key);
        notify();
      },
      subscribe: (callback) => {
        subscribers.add(callback);
        return () => {
          subscribers.delete(callback);
        };
      },
      getSnapshot: () => snapshot,
    };
  });

  return (
    <PortalContext.Provider value={context}>
      {children}
      <PortalHost />
    </PortalContext.Provider>
  );
};

export const Portal = ({
  children,
  open,
}: {
  children: PortalContent;
  open: boolean;
}) => {
  const context = useContext(PortalContext);
  if (context === null) {
    throw new Error('`Portal` must be used within `BottomSheetProvider`.');
  }

  const { addPortal, removePortal } = context;
  const id = useId();

  useLayoutEffect(() => {
    addPortal(id, { content: children, open });
  }, [id, children, open, addPortal]);
  useLayoutEffect(() => {
    return () => {
      removePortal(id);
    };
  }, [id, removePortal]);
  return null;
};
