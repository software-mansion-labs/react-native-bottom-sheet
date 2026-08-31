# Testing

## Android JVM tests

Install JDK 17 and make it available to Gradle.

Prepare the native example project and run all Android JVM tests with:

```sh
bun run test:android
```

This runs `test:android:prepare` followed by the Gradle unit-test task. Tests
belong in `android/src/test`. Pure Kotlin behavior can use JUnit directly; tests
that need Android framework behavior should use the AndroidX test runner and
Robolectric.

Every Robolectric test class must declare an explicit `@Config(sdk = [...])`.
This keeps its intended Android coverage visible and prevents it from
accidentally inheriting the module's target SDK.

## Android instrumentation tests

Prepare the native project, verify that the instrumentation APK compiles, and
then run it on a connected emulator or device:

```sh
bun run test:android:prepare
bun run test:android:instrumented:assemble
bun run test:android:instrumented
```

Instrumentation tests belong in `android/src/androidTest`. Prefer JVM or
Robolectric coverage when Android framework behavior can be reproduced there.
