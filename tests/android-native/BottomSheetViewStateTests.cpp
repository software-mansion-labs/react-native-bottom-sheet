#include <react/renderer/components/ReactNativeBottomSheetSpec/BottomSheetViewState.h>
#include <iostream>

using facebook::react::BottomSheetViewState;

int main() {
  int tests = 0;
  int failures = 0;
  auto expect = [&](const char* name, bool valid) {
    ++tests;
    failures += !valid;
    std::cout << (valid ? "PASS " : "FAIL ") << name << '\n';
  };

  BottomSheetViewState initial;
  expect("initial state preserves the existing geometry-only payload",
         initial.getDynamic().size() == 4 &&
         initial.getDynamic().count("portalPublicationId") == 0);

  BottomSheetViewState published;
  published.contentOffsetY = 12.5f;
  published.frameSize = {300, 560};
  published.contentRegionInset = 24;
  published.portalPublicationId = "9007199254740993";
  auto payload = published.getDynamic();
  expect("opaque publication identity is transported without numeric rounding or geometry changes",
         payload.count("portalPublicationId") == 1 &&
         payload["portalPublicationId"].getString() == "9007199254740993" &&
         payload["contentOffsetY"].getDouble() == 12.5 &&
         payload["frameWidth"].getDouble() == 300 &&
         payload["frameHeight"].getDouble() == 560 &&
         payload["contentRegionInset"].getDouble() == 24);

  BottomSheetViewState resized(published,
      folly::dynamic::object("contentOffsetY", 7.25)("frameWidth", 350.0)
          ("frameHeight", 640.0)("contentRegionInset", 32.5)
          ("portalPublicationId", "stale-native-writer"));
  BottomSheetViewState moved(resized, folly::dynamic::object("contentOffsetY", -3.5));
  auto movedPayload = moved.getDynamic();
  expect("full and partial native geometry updates preserve the publication identity without accepting a payload override",
         movedPayload.count("portalPublicationId") == 1 &&
         movedPayload["portalPublicationId"].getString() == "9007199254740993" &&
         movedPayload["contentOffsetY"].getDouble() == -3.5 &&
         movedPayload["frameWidth"].getDouble() == 350 &&
         movedPayload["frameHeight"].getDouble() == 640 &&
         movedPayload["contentRegionInset"].getDouble() == 32.5);

  std::cout << "STATE RESULT tests=" << tests << " failures=" << failures << '\n';
  return failures ? 1 : 0;
}
