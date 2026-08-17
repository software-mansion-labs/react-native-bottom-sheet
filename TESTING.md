# Testing

## Android JVM tests

Install JDK 17 and make it available to Gradle.

Generate the native example project and run the Android unit tests with:

```sh
bun run test:android
```

Tests belong in `android/src/test`. Pure Kotlin tests can use JUnit directly.
Tests that need Android framework behavior should use the AndroidX test runner
and Robolectric. `BottomSheetViewRequestCloseTest` runs on API 27 and 35: API 27
covers the pre-Android P `ViewCompat` path, while API 35 covers the current
keyboard and Back implementations. The portal host and coordinator tests run on
API 35 because they do not exercise version-specific behavior.

Set an explicit `@Config(sdk = [...])` on new Robolectric test classes. This
keeps their intended Android coverage visible and prevents them from
accidentally inheriting the module's target SDK.
