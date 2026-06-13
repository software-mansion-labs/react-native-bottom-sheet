import type { ComponentProps, ReactNode } from 'react';
import { router } from 'expo-router';
import { Text, TouchableOpacity, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const HANDLE_HEIGHT = 16;
export const HEADER_HEIGHT = 64;
export const SHEET_HEADER_HEIGHT = HANDLE_HEIGHT + HEADER_HEIGHT;
export const SECTION_HEIGHT = 176;
export const LIST_ITEM_HEIGHT = 48;
export const INLINE_FLATLIST_PREVIEW_ITEMS = 5;
export const MODAL_SCRIM_COLOR = 'rgba(0, 0, 0, 0.5)';
export const SHEET_BOTTOM_PADDING = 24;

export const DATA = Array.from({ length: 48 }, (_, i) => ({
  id: String(i),
  title: `Item ${i + 1}`,
}));

export const useSheetBottomPadding = (padding = SHEET_BOTTOM_PADDING) => {
  const insets = useSafeAreaInsets();
  return insets.bottom + padding;
};

export const CloseButton = ({ onPress }: { onPress: () => void }) => (
  <TouchableOpacity
    onPress={onPress}
    style={{
      width: 40,
      height: 40,
      borderRadius: 20,
      backgroundColor: '#eee',
      alignItems: 'center',
      justifyContent: 'center',
    }}
  >
    <Ionicons name="close" size={24} color="black" />
  </TouchableOpacity>
);

export const SheetBackground = ({
  children,
  style,
}: {
  children?: ReactNode;
  style?: ComponentProps<typeof View>['style'];
}) => (
  <View
    style={[
      {
        backgroundColor: 'white',
        borderTopLeftRadius: 16,
        borderTopRightRadius: 16,
        shadowColor: '#000',
        shadowOpacity: 0.19,
        shadowRadius: 5.62,
        elevation: 6,
        shadowOffset: { height: 4 },
      },
      style,
    ]}
  >
    {children}
  </View>
);

export const SheetHeader = ({
  title,
  onClose,
}: {
  title: string;
  onClose: () => void;
}) => (
  <View>
    <View style={{ alignItems: 'center', paddingTop: 8, paddingBottom: 4 }}>
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
        flexDirection: 'row',
        alignItems: 'center',
        gap: 16,
        height: HEADER_HEIGHT,
        paddingHorizontal: 16,
        borderBottomWidth: 1,
        borderBottomColor: '#eee',
      }}
    >
      <CloseButton onPress={onClose} />
      <Text style={{ fontSize: 20, fontWeight: 'bold' }}>{title}</Text>
    </View>
  </View>
);

export const CaseRow = ({
  title,
  onPress,
  isError,
}: {
  title: string;
  onPress: () => void;
  isError?: boolean;
}) => (
  <TouchableOpacity onPress={onPress} style={{ padding: 16 }}>
    <Text style={{ fontSize: 17, color: isError ? '#d11' : '#1f1f1f' }}>
      {title}
    </Text>
  </TouchableOpacity>
);

export const DemoScreen = ({
  title,
  children,
  sheet,
}: {
  title: string;
  children: ReactNode;
  sheet?: ReactNode;
}) => {
  const insets = useSafeAreaInsets();

  return (
    <View style={{ flex: 1, backgroundColor: 'white' }}>
      <View
        style={{
          paddingTop: insets.top + 8,
          paddingHorizontal: 16,
          paddingBottom: 12,
          borderBottomWidth: 1,
          borderBottomColor: '#ededed',
          backgroundColor: 'white',
          flexDirection: 'row',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <TouchableOpacity
          onPress={() => router.back()}
          style={{
            width: 40,
            height: 40,
            borderRadius: 20,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: '#f3f3f3',
          }}
        >
          <Ionicons name="arrow-back" size={20} color="black" />
        </TouchableOpacity>
        <Text style={{ fontSize: 20, fontWeight: '700', flexShrink: 1 }}>
          {title}
        </Text>
      </View>
      <View
        style={{
          flex: 1,
          paddingHorizontal: 20,
          paddingTop: 16,
          gap: 16,
          backgroundColor: 'white',
        }}
      >
        <View style={{ gap: 12 }}>{children}</View>
      </View>
      {sheet}
    </View>
  );
};

export const ListRow = ({
  item,
  index,
}: {
  item: (typeof DATA)[number];
  index: number;
}) => (
  <View
    style={{
      height: LIST_ITEM_HEIGHT,
      justifyContent: 'center',
      paddingHorizontal: 16,
      borderBottomWidth: index === DATA.length - 1 ? 0 : 1,
      borderBottomColor: '#eee',
    }}
  >
    <Text>{item.title}</Text>
  </View>
);
