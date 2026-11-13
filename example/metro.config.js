const path = require('path');
const { getDefaultConfig } = require('@expo/metro-config');
const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');
const config = getDefaultConfig(__dirname);

// Watch the monorepo root so Metro can see the library's source.
config.watchFolders = [root];

// The Exact experiment in `packages/` is no part of this app, and its build
// output runs to tens of thousands of files: keep Metro from crawling it.
config.resolver.blockList = [
  ...[config.resolver.blockList].flat().filter(Boolean),
  new RegExp(`^${escapeRegExp(path.join(root, 'packages'))}[\\\\/]`),
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// With Bun's hoisted layout, dependencies live in the root `node_modules`.
// Resolve from the example first, then fall back to the root.
config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, 'node_modules'),
  path.resolve(root, 'node_modules'),
];

// Point the workspace package at the repo root and consume it from source,
// so editing the library's `src` is reflected in the example without a rebuild.
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  [pkg.name]: root,
};

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === pkg.name || moduleName.startsWith(`${pkg.name}/`)) {
    context = {
      ...context,
      mainFields: ['source', ...context.mainFields],
      unstable_conditionNames: ['source', ...context.unstable_conditionNames],
    };
  }

  // `context.resolveRequest` is Metro's default resolver.
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
