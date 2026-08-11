# Testing

## Android JVM tests

Install JDK 17 and JDK 21 and make both installations discoverable by Gradle.
React Native uses JDK 17 for compilation, while the Android unit-test task uses
JDK 21 because Robolectric requires it to simulate the module's target SDK 36.
Gradle selects the appropriate JDK for each task through Java toolchains.

Generate the native example project and run the Android unit tests with:

```sh
bun run test:android
```

Tests belong in `android/src/test`. Pure Kotlin tests can use JUnit directly.
Tests that need Android framework behavior should use the AndroidX test runner
and Robolectric.

If Gradle cannot find one of the installed JDKs, configure a supported
[toolchain discovery mechanism](https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection),
or provide its location through `org.gradle.java.installations.paths` or
`org.gradle.java.installations.fromEnv`.
