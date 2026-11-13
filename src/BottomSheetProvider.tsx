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

import { BottomSheetPortalHostView } from './BottomSheetHostViews';

type PortalSnapshot = Array<[string, ReactNode]>;

interface PortalEntry {
  element: ReactNode;
  order: number;
}

interface PortalContextType {
  addPortal: (key: string, element: ReactNode, order: number) => void;
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

  if (portals.length === 0) {
    return null;
  }

  return (
    // While a sheet in it is open, the native host hides its siblings - the
    // screen behind the sheets - from screen readers. See issue #75.
    <BottomSheetPortalHostView
      style={StyleSheet.absoluteFill}
      pointerEvents="box-none"
    >
      {portals.map(([key, element]) => (
        <View
          key={key}
          style={StyleSheet.absoluteFill}
          pointerEvents="box-none"
          // Keep this wrapper a real native view (Android). As a layout-only
          // view Fabric flattens it, and portal-entry churn (a sheet remounted
          // while the previous instance tears down) makes the differ unflatten
          // it mid-flight - a reparenting batch that can arrive without the
          // wrapper's Create mutation, killing the surface with "Unable to
          // find viewState for tag". See issue #78.
          collapsable={false}
        >
          {element}
        </View>
      ))}
    </BottomSheetPortalHostView>
  );
};

/** Props for {@link BottomSheetProvider}. */
export interface BottomSheetProviderProps {
  /** The app content. Modal sheets render above it. */
  children: ReactNode;
}

/** Provides the portal host required for modal bottom sheets. */
export const BottomSheetProvider = ({ children }: BottomSheetProviderProps) => {
  const [context] = useState<PortalContextType>(() => {
    const portals = new Map<string, PortalEntry>();
    const subscribers = new Set<() => void>();
    let snapshot: PortalSnapshot = [];
    const notify = () => {
      // Higher orders render later, so on top. The sort is stable, so equal
      // orders keep the order in which their portals mounted.
      snapshot = Array.from(portals.entries())
        .sort(([, a], [, b]) => a.order - b.order)
        .map(([key, { element }]) => [key, element]);
      subscribers.forEach((subscriber) => subscriber());
    };
    return {
      addPortal: (key, element, order) => {
        portals.set(key, { element, order });
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
  order = 0,
}: {
  children: ReactNode;
  /** Stacking order among portals: higher renders on top. */
  order?: number;
}) => {
  const context = useContext(PortalContext);
  if (context === null) {
    throw new Error('`Portal` must be used within `BottomSheetProvider`.');
  }

  const { addPortal, removePortal } = context;
  const id = useId();

  useLayoutEffect(() => {
    addPortal(id, children, order);
  }, [id, children, order, addPortal]);
  useLayoutEffect(() => {
    return () => {
      removePortal(id);
    };
  }, [id, removePortal]);
  return null;
};
