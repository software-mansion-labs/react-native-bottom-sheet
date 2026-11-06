// Bundles the prototype into one self-contained `index.html`, using the real
// library from this checkout's `src/`. Run from the repository root:
//
//   node notes/landing-proposal/build.mjs
//
// esbuild comes through `bunx`, so the repository gains no dependency.
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const read = (file) => readFileSync(join(here, file), 'utf8');

const { status, stdout, stderr } = spawnSync(
  'bunx',
  [
    'esbuild@0.25.12',
    join(here, 'src/app.jsx'),
    '--bundle',
    '--minify',
    '--format=iife',
    '--jsx=automatic',
    '--target=es2022',
    '--alias:react-native=react-native-web',
    '--alias:@swmansion/react-native-bottom-sheet=./src/index.tsx',
    '--resolve-extensions=.web.tsx,.web.ts,.web.jsx,.web.js,.tsx,.ts,.jsx,.js,.json',
    '--define:process.env.NODE_ENV="production"',
    '--define:global=window',
    '--define:__DEV__=false',
    '--log-level=warning',
  ],
  { encoding: 'utf8', maxBuffer: 1024 * 1024 * 32 }
);
if (status !== 0) {
  process.stderr.write(stderr);
  process.exit(status ?? 1);
}
process.stderr.write(stderr);

const svgDataUri = (file) =>
  `data:image/svg+xml;base64,${readFileSync(join(here, file)).toString('base64')}`;
const html = read('src/template.html')
  .replace('/*__STYLES__*/', () => read('src/styles.css'))
  .replace('/*__SCRIPT__*/', () => stdout.replaceAll('</script', '<\\/script'))
  .replace('__GOBI_LIGHT__', () => svgDataUri('src/assets/gobi-light.svg'))
  .replace('__GOBI_DARK__', () => svgDataUri('src/assets/gobi-dark.svg'));

writeFileSync(join(here, 'index.html'), html);
console.log(`index.html: ${(html.length / 1024).toFixed(0)} kB`);
