import {
  createContext,
  useContext,
  useId,
  useLayoutEffect,
  useState,
  useSyncExternalStore,
} from 'react';
import type { ReactNode } from 'react';

import { renderPortalHost, type PortalSnapshot } from './PortalHost';

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

  return renderPortalHost(portals);
};

/** Provides the portal host required for modal bottom sheets. */
export const BottomSheetProvider = ({ children }: { children: ReactNode }) => {
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
