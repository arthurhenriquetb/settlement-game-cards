import { existsSync, mkdirSync, readdirSync, copyFileSync, cpSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const assets = resolve('app/src/main/assets');
const dataDir = join(assets, 'data');
const frontend = resolve('node_modules/@emulatorjs/emulatorjs');
const core = resolve('node_modules/@emulatorjs/core-mgba');

if (!existsSync(frontend) || !existsSync(core)) {
  throw new Error('Install npm dependencies first: npm install --omit=optional --ignore-scripts');
}
const frontendData = join(frontend, 'data');
if (!existsSync(join(frontendData, 'loader.js'))) {
  throw new Error('The EmulatorJS npm package has no data/loader.js');
}

mkdirSync(dataDir, { recursive: true });
cpSync(frontendData, dataDir, { recursive: true, force: true });

// @emulatorjs/core-mgba is published separately from the UI package.
// Copy the distributable core files (usually mgba-wasm.*) into data/cores.
const destCores = join(dataDir, 'cores');
mkdirSync(destCores, { recursive: true });
const copied = [];
function walk(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const p = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(p);
    } else if (/mgba.*\.(?:js|wasm|data|zip|7z|json)$/i.test(entry.name)) {
      copyFileSync(p, join(destCores, entry.name));
      copied.push(entry.name);
    }
  }
}
walk(core);
if (!copied.some(f => /mgba.*\.(?:wasm|data|zip|7z)/i.test(f))) {
  throw new Error(`No mGBA binary was found in ${core}. Package entries: ${readdirSync(core)}`);
}

const licenses = join(assets, 'licenses');
mkdirSync(licenses, { recursive: true });
for (const [src, name] of [[frontend, 'EmulatorJS-LICENSE.txt'], [core, 'mGBA-LICENSE.txt']]) {
  const license = ['LICENSE', 'LICENSE.txt', 'COPYING'].find(n => existsSync(join(src, n)));
  if (license) copyFileSync(join(src, license), join(licenses, name));
}

const version = `EmulatorJS 4.2.3 and mGBA core 4.2.3\n`;
writeFileSync(join(licenses, 'VERSIONS.txt'), version);
console.log('Bundled EmulatorJS assets and mGBA files:', copied.join(', '));
console.log('Bundle size (top-level files):', readdirSync(dataDir).length);
