import {
  createContext,
  useContext,
  useEffect,
  useId,
  useReducer,
  useState,
} from 'react';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

interface PortalContextType {
  addPortal: (key: string, element: ReactNode) => void;
  removePortal: (key: string) => void;
  subscribe: (callback: () => void) => () => void;
  getPortals: () => Map<string, ReactNode>;
}

const PortalContext = createContext<PortalContextType | null>(null);

const PortalHost = () => {
  const context = useContext(PortalContext)!;
  const [, forceRender] = useReducer((x: number) => x + 1, 0);
  useEffect(() => {
    return context.subscribe(forceRender);
  }, [context]);

  return Array.from(context.getPortals().entries()).map(([key, element]) => (
    <View key={key} style={StyleSheet.absoluteFill} pointerEvents="box-none">
      {element}
    </View>
  ));
};

export const BottomSheetProvider = ({ children }: { children: ReactNode }) => {
  const [context] = useState<PortalContextType>(() => {
    const portals = new Map<string, ReactNode>();
    const subscribers = new Set<() => void>();
    const notify = () => {
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
      getPortals: () => portals,
    };
  });

  return (
    <PortalContext.Provider value={context}>
      {children}
      <PortalHost />
    </PortalContext.Provider>
  );
};

export const Portal = ({ children }: { children: ReactNode }) => {
  const context = useContext(PortalContext);
  if (context === null) {
    throw new Error('`Portal` must be used within `BottomSheetProvider`.');
  }

  const { addPortal, removePortal } = context;
  const id = useId();

  useEffect(() => {
    addPortal(id, children);
  }, [id, children, addPortal]);
  useEffect(() => {
    return () => {
      removePortal(id);
    };
  }, [id, removePortal]);
  return null;
};
