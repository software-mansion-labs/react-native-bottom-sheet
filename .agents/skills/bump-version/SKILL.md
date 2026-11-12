---
name: bump-version
description:
  Bump the React Native Bottom Sheet version name across the library and example
  app and commit the change.
---

# Bump version

Use the requested version or increment when specified. For a plain bump, read
the current version from the root `package.json` and apply the smallest
increment.

- For a stable version, increment the patch number (`0.18.0` → `0.18.1`).
- For a numbered prerelease, increment the trailing number and keep the same
  base version and prerelease label (`0.18.0-next.1` → `0.18.0-next.2`).

Set all four fields to the resulting version string.

| File                   | Field                        |
| ---------------------- | ---------------------------- |
| `package.json`         | `version`                    |
| `example/package.json` | `version`                    |
| `example/app.json`     | `expo.version`               |
| `bun.lock`             | `workspaces.example.version` |

Verify that all four values match, then immediately commit just the version
edits. Use the new version in the commit message, for example:

```text
chore: bump version to `0.18.1`
```
