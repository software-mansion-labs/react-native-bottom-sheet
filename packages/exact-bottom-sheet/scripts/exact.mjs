// `bun run exact <web|ios> [--run] [--port <n>] [--sim <udid|name>]` from the
// repository root: builds (and with --run, runs) the Exact demo app in
// packages/exact-demo against @swmansion/exact-bottom-sheet.
//
// 1. Finds the pinned Exact checkout (EXACT_HOME, default
//    ~/.cache/exact/checkout), cloning it at the pinned commit when missing.
// 2. Checks the prerequisites and names what is missing.
// 3. Generates the package's files that point into ios/ and src/web
//    (scripts/prepare.mjs) and installs the demo's dependencies.
// 4. Runs the demo's own `exact.mjs` verb: `web-build` / `web --port`, or
//    `ios` / `ios --run`.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { homedir } from 'node:os';
import { delimiter, dirname, resolve } from 'node:path';

import { packageDir, prepare, repoRoot } from './prepare.mjs';

const demoDir = resolve(repoRoot, 'packages/exact-demo');
const pin = JSON.parse(
  readFileSync(resolve(packageDir, 'package.json'), 'utf8')
).exact;
const exactHome = resolve(
  process.env.EXACT_HOME ?? resolve(homedir(), '.cache/exact/checkout')
);

const usage = `usage: bun run exact <web|ios> [--run] [--port <n>] [--sim <udid|name>]

  web          build the demo for the web (target/web-dist); --run serves the dev loop
               (default port 8765, --port to choose)
  ios          build the demo for an iOS simulator and install it; --run launches it
               (--sim or EXACT_SIM picks the simulator when several are booted)

Environment: EXACT_HOME (the Exact checkout, default ~/.cache/exact/checkout),
EXACT_BUN (a Bun ${pin.bun} binary, default ~/.bun-${pin.bun}/bin/bun or bun on PATH).`;

const args = process.argv.slice(2);
const target = args[0];
if (
  !['web', 'ios'].includes(target) ||
  args.includes('--help') ||
  args.includes('-h')
) {
  console.log(usage);
  process.exit(
    target === undefined || args.includes('--help') || args.includes('-h')
      ? 0
      : 1
  );
}
const flag = (name) =>
  args.includes(name) ? args[args.indexOf(name) + 1] : undefined;
const run = args.includes('--run');

const fail = (message) => {
  console.error(`exact: ${message}`);
  process.exit(1);
};
const say = (message) => console.log(`exact: ${message}`);

// The environment every Exact command runs in: rustup's and the pinned Bun's
// bin directories first, without touching the shell profile.
const cargoBin = resolve(homedir(), '.cargo/bin');
const output = (cmd, cmdArgs, options = {}) => {
  const r = spawnSync(cmd, cmdArgs, { encoding: 'utf8', ...options });
  return r.status === 0 ? r.stdout.trim() : null;
};
const findBun = () => {
  const candidates = [
    process.env.EXACT_BUN,
    resolve(homedir(), `.bun-${pin.bun}/bin/bun`),
    'bun',
  ].filter(Boolean);
  for (const bun of candidates) {
    if (output(bun, ['--version']) === pin.bun) return bun;
  }
  return null;
};
const bun = findBun();
if (!bun) {
  fail(
    `Exact pins Bun ${pin.bun}, which was not found. Install it beside your Bun with\n` +
      `  curl -fsSL https://bun.sh/install | BUN_INSTALL=~/.bun-${pin.bun} bash -s bun-v${pin.bun}\n` +
      `(check that the installer left your shell profile alone), or point EXACT_BUN at one.`
  );
}
const env = {
  ...process.env,
  PATH: [cargoBin, dirname(bun), process.env.PATH ?? ''].join(delimiter),
  EXACT2: exactHome,
  BUN: bun,
  // Exact's CLI can send one-line feedback notes to Expo; nothing is sent from
  // here unless the person opts in by setting EXACT_FEEDBACK themselves.
  EXACT_FEEDBACK: process.env.EXACT_FEEDBACK ?? '0',
};
const sh = (cmd, cmdArgs, cwd) => {
  const r = spawnSync(cmd, cmdArgs, { cwd, env, stdio: 'inherit' });
  if (r.status !== 0)
    fail(`${cmd} ${cmdArgs.join(' ')} failed (exit ${r.status ?? r.signal})`);
};

// 1. The checkout.
if (!existsSync(resolve(exactHome, 'scripts/exact.mjs'))) {
  if (existsSync(exactHome))
    fail(
      `${exactHome} exists but is not an Exact checkout (no scripts/exact.mjs)`
    );
  say(`cloning ${pin.repository} at ${pin.commit} into ${exactHome}`);
  mkdirSync(dirname(exactHome), { recursive: true });
  sh('git', ['clone', '--depth', '1', pin.repository, exactHome]);
  sh('git', ['fetch', '--depth', '1', 'origin', pin.commit], exactHome);
  sh('git', ['checkout', '-q', pin.commit], exactHome);
}
const head = output('git', ['rev-parse', 'HEAD'], { cwd: exactHome });
if (head !== pin.commit) {
  console.warn(
    `exact: warning: ${exactHome} is at ${head ?? 'an unknown commit'}, not the pinned ${pin.commit}.\n` +
      `  It is left as it is; to match the pin: git -C ${exactHome} fetch --depth 1 origin ${pin.commit} && git -C ${exactHome} checkout ${pin.commit}`
  );
}

// 2. Prerequisites.
const missing = [];
const toolchain = /channel\s*=\s*"([^"]+)"/.exec(
  readFileSync(resolve(exactHome, 'rust-toolchain.toml'), 'utf8')
)?.[1];
if (!output('rustup', ['--version'], { env })) {
  missing.push(
    'rustup (Rust): curl --proto =https --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path --default-toolchain none'
  );
} else if (
  toolchain &&
  !(output('rustup', ['toolchain', 'list'], { env }) ?? '').includes(toolchain)
) {
  missing.push(
    `Rust ${toolchain}: run Exact's setup, (cd ${exactHome} && ${bun} scripts/exact.mjs setup)`
  );
}
if (!existsSync(resolve(exactHome, 'node_modules'))) {
  missing.push(
    `the checkout's dependencies: (cd ${exactHome} && ${bun} scripts/exact.mjs setup)`
  );
}
if (target === 'ios') {
  if (process.platform !== 'darwin' || !output('xcrun', ['--find', 'swiftc']))
    missing.push('Xcode (for iOS)');
  const sim = 'aarch64-apple-ios-sim';
  if (
    toolchain &&
    !(
      output(
        'rustup',
        ['target', 'list', '--installed', '--toolchain', toolchain],
        { env }
      ) ?? ''
    ).includes(sim)
  ) {
    missing.push(
      `Rust's ${sim} target: rustup target add ${sim} --toolchain ${toolchain}`
    );
  }
}
if (missing.length)
  fail(`missing prerequisites:\n${missing.map((m) => `  - ${m}`).join('\n')}`);

// 3. The package's generated files, then the demo's dependencies. Bun links a
// `file:` dependency file by file, so the package is relinked to pick up the
// generated files.
const prepared = await prepare();
say(
  `generated ${prepared.apple.out.replace(`${repoRoot}/`, '')} and ${prepared.web.out.replace(`${repoRoot}/`, '')}`
);
rmSync(resolve(demoDir, 'node_modules/@swmansion/exact-bottom-sheet'), {
  recursive: true,
  force: true,
});
sh(bun, ['install'], demoDir);

// 4. The build.
const verb =
  target === 'web'
    ? run
      ? ['web', ...(flag('--port') ? ['--port', flag('--port')] : [])]
      : ['web-build']
    : [
        'ios',
        ...(run ? ['--run'] : []),
        ...(flag('--sim') ? ['--sim', flag('--sim')] : []),
      ];
say(
  `bun exact.mjs ${verb.join(' ')} (in packages/exact-demo, EXACT2=${exactHome})`
);
sh(bun, ['exact.mjs', ...verb], demoDir);
