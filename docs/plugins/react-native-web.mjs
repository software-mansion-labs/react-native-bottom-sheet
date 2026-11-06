import path from 'node:path';
import { fileURLToPath } from 'node:url';

const docsRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

// Lets the interactive examples render the library from `../src` on
// `react-native-web`. React is pinned to the docs' copy so the library's source
// doesn't pick up a second one from the repo root.
/** @type {import('@docusaurus/types').PluginModule} */
const reactNativeWeb = () => ({
  name: 'react-native-web',
  configureWebpack: () => ({
    mergeStrategy: { 'resolve.extensions': 'prepend' },
    resolve: {
      alias: {
        '@swmansion/react-native-bottom-sheet$': path.resolve(
          docsRoot,
          '../src/index.tsx'
        ),
        'react-native$': 'react-native-web',
        'react': path.join(docsRoot, 'node_modules/react'),
        'react-dom': path.join(docsRoot, 'node_modules/react-dom'),
      },
      extensions: ['.web.tsx', '.web.ts', '.web.js'],
    },
  }),
});

export default reactNativeWeb;
