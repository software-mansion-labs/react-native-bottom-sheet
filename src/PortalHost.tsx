import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

export type PortalSnapshot = Array<[string, ReactNode]>;

export const renderPortalHost = (portals: PortalSnapshot) => {
  if (portals.length === 0) return null;

  return (
    // Keep portal wrappers under one non-flattened native parent. Android uses
    // their native sibling order to select the unique accessible Top portal.
    <View
      collapsable={false}
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
    </View>
  );
};
