import { Component, useState, type ReactNode } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import { BottomSheet, type Detent } from '@swmansion/react-native-bottom-sheet';

import { DemoScreen, SheetBackground } from '../demoShared';

const INVALID_DETENTS_CONTENT_HEIGHT = 160;

type ValidationCase = Readonly<{
  label: string;
  detents: Detent[];
  index: number;
}>;

const VALIDATION_CASES: ValidationCase[] = [
  { label: 'index: 1.5', detents: [0, 'content'], index: 1.5 },
  {
    label: 'index equal to detents.length',
    detents: [0, 'content'],
    index: 2,
  },
  { label: 'empty detents', detents: [], index: 0 },
  { label: 'numeric detent: -10', detents: [0, -10], index: 0 },
  { label: "percentage detent: '-10%'", detents: [0, '-10%'], index: 0 },
  {
    label: 'native ascending-order validation',
    detents: [360, 120, 'content'],
    index: 1,
  },
];

type ValidationErrorBoundaryState = { error: string | null };

class ValidationErrorBoundary extends Component<
  { children: ReactNode },
  ValidationErrorBoundaryState
> {
  state: ValidationErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown) {
    return { error: error instanceof Error ? error.message : String(error) };
  }

  render() {
    if (this.state.error !== null) {
      return (
        <View style={styles.errorCard}>
          <Text style={styles.errorTitle}>Caught JavaScript Error</Text>
          <Text selectable>{this.state.error}</Text>
        </View>
      );
    }

    return this.props.children;
  }
}

export const InvalidDetentsScreen = () => {
  const [caseIndex, setCaseIndex] = useState(0);
  const validationCase = VALIDATION_CASES[caseIndex]!;

  return (
    <DemoScreen
      title="Invalid props"
      sheet={
        <ValidationErrorBoundary key={caseIndex}>
          <BottomSheet
            detents={validationCase.detents}
            index={validationCase.index}
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
                  Invalid props
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
                The final case reaches native layout to confirm that resolved
                detent ordering is still validated there.
              </Text>
            </View>
          </BottomSheet>
        </ValidationErrorBoundary>
      }
    >
      <Text>
        Case {caseIndex + 1} of {VALIDATION_CASES.length}:{' '}
        {validationCase.label}
      </Text>
      <Button
        title="Show next invalid case"
        onPress={() =>
          setCaseIndex((current) => (current + 1) % VALIDATION_CASES.length)
        }
      />
      <Text>
        Static index and detent errors are caught above the native view. The
        final case intentionally exercises native ascending-order validation.
      </Text>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  errorCard: {
    margin: 20,
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#d11',
    backgroundColor: '#fff4f4',
    gap: 8,
  },
  errorTitle: {
    color: '#d11',
    fontWeight: '700',
  },
});
