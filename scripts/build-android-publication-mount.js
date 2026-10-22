// Test-only bundle for a ReactSurface that is never attached to a window.
const path = require('path');
const fs = require('fs');
const Metro = require('metro');
const { getDefaultConfig } = require('@react-native/metro-config');

async function main() {
  const root = path.resolve(__dirname, '..');
  const output = path.join(root, 'android/build/publication-jni/mount-assets');
  fs.mkdirSync(output, { recursive: true });
  const config = await Metro.loadConfig({ cwd: root }, getDefaultConfig(root));
  config.maxWorkers = 2;
  config.resolver.useWatchman = false;
  await Metro.runBuild(config, {
    entry: path.join(
      root,
      'tests/android-native',
      process.argv.includes('--failure')
        ? 'publication-failure-entry.js'
        : 'publication-mount-entry.js'
    ),
    platform: 'android',
    dev: false,
    minify: false,
    bundleOut: path.join(
      output,
      process.argv.includes('--failure')
        ? 'failure-mount.bundle'
        : 'publication-mount.bundle'
    ),
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
