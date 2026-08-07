#!/usr/bin/env node
/**
 * NLS migration tooling for GitHub issue #214 -- "Make the backend the single source for all NLS texts".
 *
 * The backend messages*.properties files are becoming the only place user interface texts are maintained. This script
 * performs the mechanical parts of that migration and the checks that keep it honest.
 *
 * Subcommands
 *   check       PERMANENT, for CI. Fails if a frontend translation file reappears, or if a text used by src/app/lib
 *               is shipped only by grafioschtrader-common -- which works in the full application but leaves a
 *               standalone grafiosch server rendering raw keys.
 *
 * The remaining subcommands performed the one-time migration and are kept for reference and for a future repeat of
 * the same exercise. They operate on frontend translation files, which no longer exist:
 *   scan        Classify every frontend translation key by the layer of the code that consumes it.
 *   reclassify  Move library-consumed keys out of the application translation file into the library one.
 *   shadow      List (or with --apply delete) frontend keys the backend already served. Those were dead already:
 *               MultiTranslateHttpLoader merged the backend last, so the backend value silently won.
 *   move        Move keys from the frontend JSON files into a backend properties pair.
 *
 * The mapping from a backend property key to the key the client uses is NOT reimplemented here. It lives in
 * grafiosch.nls.NlsKeyMapper and is exported to backend/grafioschtrader-server/target/nls-inventory-*.tsv by
 * NlsInventoryTest. Run that test first; this script reads the generated files. Keeping one implementation is the
 * whole point -- a second copy in JavaScript would drift and the drift would be invisible.
 *
 * Usage:
 *   node scripts/nls-tool.mjs check
 *   node scripts/nls-tool.mjs scan
 *   node scripts/nls-tool.mjs reclassify [--apply]
 *   node scripts/nls-tool.mjs shadow [--apply]
 *   node scripts/nls-tool.mjs move --to=base|common --keys=<file> [--area="GT Net"]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const FRONTEND = path.join(ROOT, 'frontend');
const APP_I18N = path.join(FRONTEND, 'src/assets/i18n');
const LIB_I18N = path.join(FRONTEND, 'src/app/lib/assets/i18n');
const INVENTORY = path.join(ROOT, 'backend/grafioschtrader-server/target');

const BUNDLES = {
  base: {
    dir: path.join(ROOT, 'backend/grafiosch-base/src/main/resources/i18n'),
    inventory: path.join(INVENTORY, 'nls-inventory-base.tsv'),
    label: 'grafiosch-base'
  },
  common: {
    dir: path.join(ROOT, 'backend/grafioschtrader-common/src/main/resources/message'),
    inventory: path.join(INVENTORY, 'nls-inventory-common.tsv'),
    label: 'grafioschtrader-common'
  }
};

const LANGUAGES = [
  { tag: 'en', json: 'en.json', properties: 'messages.properties' },
  { tag: 'de', json: 'de.json', properties: 'messages_de.properties' }
];

/** Keys must survive .properties syntax without escaping. '|' is legal and used, a space is not. */
const LEGAL_KEY = /^[A-Za-z0-9_.|]+$/;

/**
 * Tokens that look like translation keys in src/app/lib but are not.
 *
 * The reference scan cannot tell an UPPER_SNAKE translation key from an UPPER_SNAKE TypeScript identifier, and both
 * shapes are common. Enum members deliberately do NOT belong here -- an enum constant rendered with
 * TranslateValue.NORMAL really is resolved as a key at runtime. Only genuinely unrelated identifiers go in this list.
 */
const NOT_NLS_KEYS = new Set([
  // GlobalSessionNames.ID_TENANT = 'idTenant' -- a sessionStorage entry name.
  'ID_TENANT'
]);

// ---------------------------------------------------------------------------------------------- reading and writing

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

/** Writes a translation file with sorted keys, so a diff only ever shows the intended change. */
function writeJson(file, data) {
  const sorted = {};
  for (const key of Object.keys(data).sort()) {
    sorted[key] = data[key];
  }
  fs.writeFileSync(file, JSON.stringify(sorted, null, 2) + '\n', { encoding: 'utf8' });
  verifyUtf8(file);
}

/**
 * Re-reads a file we just wrote and fails if it is not clean UTF-8. German umlauts being mangled during an edit is a
 * recurring problem in this repository, and catching it at write time is far cheaper than finding it in the UI.
 */
function verifyUtf8(file) {
  const bytes = fs.readFileSync(file);
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  if (text.includes('�')) {
    throw new Error(`${file} contains U+FFFD after writing -- encoding corruption.`);
  }
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    throw new Error(`${file} was written with a UTF-8 BOM.`);
  }
}

/** Parses a .properties file, preserving the exact source lines so unrelated formatting is never touched. */
function readProperties(file) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  const entries = new Map();
  for (const line of lines) {
    const trimmed = line.trimStart();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;
    const separator = trimmed.search(/[=:]/);
    if (separator < 1) continue;
    entries.set(trimmed.slice(0, separator).trim(), trimmed.slice(separator + 1).trimStart());
  }
  return { lines, entries };
}

function readInventory(module) {
  const file = BUNDLES[module].inventory;
  if (!fs.existsSync(file)) {
    throw new Error(
      `Missing ${path.relative(ROOT, file)}.\n` +
      `Generate it first:  cd backend && mvn -q -pl grafioschtrader-server test -Dtest=NlsInventoryTest`);
  }
  const clientKeys = new Map();
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const [rawKey, clientKey] = line.split('\t');
    if (clientKey) clientKeys.set(clientKey, rawKey);
  }
  return clientKeys;
}

// ------------------------------------------------------------------------------------------------ consumer scanning

function sourceFiles() {
  const files = [];
  const walk = dir => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules' && entry.name !== 'assets') walk(full);
      } else if ((entry.name.endsWith('.ts') || entry.name.endsWith('.html')) && !entry.name.endsWith('.spec.ts')) {
        files.push(full);
      }
    }
  };
  walk(path.join(FRONTEND, 'src'));
  return files;
}

/**
 * Splits the frontend sources into the reusable library layer and the application layer, mirroring the backend split
 * between grafiosch-* and grafioschtrader-*.
 */
function layerTexts() {
  const normalize = file => file.split(path.sep).join('/');
  const lib = [];
  const app = [];
  for (const file of sourceFiles()) {
    const normalized = normalize(file);
    // The standalone Grafiosch host counts as library layer, not as an exception: it runs against a server that
    // serves the grafiosch-base bundle alone, so every key it references has to live there too.
    if (normalized.includes('src/app/lib/') || normalized.includes('src/grafiosch-host/')) lib.push(file);
    else app.push(file);
  }
  const join = files => files.map(file => stripComments(fs.readFileSync(file, 'utf8'))).join('\n');
  return { lib: join(lib), app: join(app), libCount: lib.length, appCount: app.length };
}

/**
 * Removes comments before scanning for key references.
 *
 * Documentation prose is full of words that look exactly like translation keys -- "ISIN", "HIGH", "EQUITIES,ETF" all
 * appear in Javadoc here -- and counting them makes the scan report keys nothing actually uses. TS enum members are
 * deliberately NOT stripped: an enum constant rendered with TranslateValue.NORMAL really is resolved as a key at
 * runtime, so those matches are genuine.
 */
function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/.*$/gm, '$1')
    .replace(/<!--[\s\S]*?-->/g, ' ');
}

function referencedIn(text, key) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?<![A-Za-z0-9_.])${escaped}(?![A-Za-z0-9_])`).test(text);
}

let classifyKeysMemo = null;

/**
 * Classifies each key by the layer of its consumer.
 *
 * A literal scan is a lower bound, not the truth: many keys are composed at runtime (from a field name via
 * addColumnFeqH, from an enum constant name, from a string concatenation such as 'HELP_' + x) and therefore appear
 * nowhere as a literal. Those are reported as unresolved so a human classifies them; guessing would silently put texts
 * in the wrong artifact.
 */
function classifyKeys() {
  if (classifyKeysMemo) return classifyKeysMemo;
  const texts = layerTexts();
  const appKeys = Object.keys(readJson(path.join(APP_I18N, 'en.json')));
  const libKeys = Object.keys(readJson(path.join(LIB_I18N, 'en.json')));
  const classify = keys => {
    const result = { lib: [], app: [], both: [], unresolved: [] };
    for (const key of keys) {
      const inLib = referencedIn(texts.lib, key);
      const inApp = referencedIn(texts.app, key);
      if (inLib && inApp) result.both.push(key);
      else if (inLib) result.lib.push(key);
      else if (inApp) result.app.push(key);
      else result.unresolved.push(key);
    }
    return result;
  };
  classifyKeysMemo = { texts, appKeys, libKeys, inAppFile: classify(appKeys), inLibFile: classify(libKeys) };
  return classifyKeysMemo;
}

// -------------------------------------------------------------------------------------------------------- commands

/**
 * Keys that must move from the application file into the library file.
 *
 * A key consumed by src/app/lib -- whether or not the application also uses it -- has to be served by grafiosch-base,
 * because the reusable library must resolve its own texts when the application is not deployed at all. The reverse
 * direction is deliberately not automated: leaving a generic text in the library costs nothing, while moving out a key
 * that the library composes at runtime would break it silently.
 */
function keysToMoveIntoLibrary() {
  const { inAppFile } = classifyKeys();
  return [...inAppFile.lib, ...inAppFile.both].sort();
}

function commandScan() {
  const { texts, inAppFile, inLibFile } = classifyKeys();
  console.log(`Scanned ${texts.libCount} files under src/app/lib and ${texts.appCount} application files.\n`);

  console.log(`In src/assets/i18n, consumed only by src/app/lib      : ${inAppFile.lib.length}`);
  console.log(`In src/assets/i18n, consumed by both layers           : ${inAppFile.both.length}`);
  console.log(`  => must move into the library file                  : ${inAppFile.lib.length + inAppFile.both.length}`);
  console.log(`In src/app/lib/assets/i18n, consumed only by the app  : ${inLibFile.app.length}  (left in place, see code)`);
  console.log(`No literal reference, classified by current location  : ${inAppFile.unresolved.length} app + ${inLibFile.unresolved.length} lib\n`);
  console.log('-- move to src/app/lib/assets/i18n --');
  console.log(keysToMoveIntoLibrary().join('\n') || '(none)');
  return 0;
}

/** Moves the library-consumed keys out of the application file into the library file, in both languages. */
function commandReclassify(options) {
  const keys = keysToMoveIntoLibrary();
  console.log(`${keys.length} key(s) belong in src/app/lib/assets/i18n.`);
  if (!options.apply) {
    console.log('Dry run. Re-run with --apply to move them.');
    return 0;
  }
  let moved = 0;
  for (const language of LANGUAGES) {
    const appFile = path.join(APP_I18N, language.json);
    const libFile = path.join(LIB_I18N, language.json);
    const app = readJson(appFile);
    const lib = readJson(libFile);
    for (const key of keys) {
      if (!(key in app)) continue;
      lib[key] = app[key];
      delete app[key];
      moved++;
    }
    writeJson(appFile, app);
    writeJson(libFile, lib);
  }
  console.log(`Moved ${moved} entries across both languages.`);
  return 0;
}

/**
 * Finds frontend keys the backend already serves. They are invisible today because the loader merges the backend last,
 * so deleting them cannot change what a user sees -- but leaving them would create a duplicate the moment the
 * remaining texts move to the backend.
 */
function commandShadow(options) {
  const backendClientKeys = new Map([...readInventory('base'), ...readInventory('common')]);
  const report = [];
  for (const [label, dir] of [['app', APP_I18N], ['lib', LIB_I18N]]) {
    const keys = Object.keys(readJson(path.join(dir, 'en.json')));
    const shadowed = keys.filter(key => backendClientKeys.has(key));
    report.push({ label, dir, shadowed });
  }

  const appKeys = new Set(Object.keys(readJson(path.join(APP_I18N, 'en.json'))));
  const libKeys = new Set(Object.keys(readJson(path.join(LIB_I18N, 'en.json'))));
  const duplicated = [...appKeys].filter(key => libKeys.has(key));

  const unique = new Set(report.flatMap(entry => entry.shadowed));
  console.log(`Frontend keys already served by the backend: ${unique.size} unique`);
  for (const entry of report) {
    console.log(`  ${entry.label}: ${entry.shadowed.length}`);
  }
  console.log(`Keys defined in BOTH frontend files: ${duplicated.length}`);
  console.log(`  ${duplicated.join(', ') || '(none)'}`);

  if (!options.apply) {
    console.log('\nDry run. Re-run with --apply to delete them.');
    return 0;
  }

  let deleted = 0;
  for (const entry of report) {
    for (const language of LANGUAGES) {
      const file = path.join(entry.dir, language.json);
      const data = readJson(file);
      for (const key of entry.shadowed) {
        if (key in data) {
          delete data[key];
          deleted++;
        }
      }
      writeJson(file, data);
    }
  }
  // A key still defined in both frontend files after the shadow removal is a plain duplicate; the library copy wins
  // because the library is the more general layer, so drop the application copy.
  const stillDuplicated = [...new Set(Object.keys(readJson(path.join(APP_I18N, 'en.json'))))]
    .filter(key => key in readJson(path.join(LIB_I18N, 'en.json')));
  for (const language of LANGUAGES) {
    const file = path.join(APP_I18N, language.json);
    const data = readJson(file);
    for (const key of stillDuplicated) {
      if (key in data) {
        delete data[key];
        deleted++;
      }
    }
    writeJson(file, data);
  }
  console.log(`\nDeleted ${deleted} entries (${stillDuplicated.length} of them remaining lib/app duplicates).`);
  return 0;
}

/**
 * Escapes a value for .properties.
 *
 * Only the characters that actually change meaning are escaped. ':' '=' '#' and '!' are special in a key, or as the
 * first non-blank character of a line, and this writer always emits KEY=value on one physical line -- escaping them
 * would sprinkle stray backslashes through more than a hundred texts.
 *
 * Placeholders are left exactly as they are. ngx-translate uses {{name}} and takes a single quote literally;
 * MessageFormat uses {0} and treats a single quote as an escape. Migrated keys are resolved in the browser, so their
 * quotes must NOT be doubled. NlsPlaceholderDialectTest enforces that the two dialects never meet on one key.
 */
function escapePropertyValue(value) {
  return value.replace(/\\/g, '\\\\').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/\t/g, '\\t');
}

function commandMove(options) {
  const module = options.to;
  if (!BUNDLES[module]) throw new Error(`--to must be "base" or "common", got "${options.to}"`);
  if (!options.keys) throw new Error('--keys=<file with one key per line> is required');
  const keys = fs.readFileSync(path.resolve(ROOT, options.keys), 'utf8')
    .split(/\r?\n/).map(line => line.trim()).filter(line => line && !line.startsWith('#'));

  const sources = [APP_I18N, LIB_I18N];
  const problems = [];
  const moved = {};
  for (const language of LANGUAGES) {
    moved[language.tag] = new Map();
  }

  for (const key of keys) {
    if (!LEGAL_KEY.test(key)) {
      problems.push(`${key}: not a legal .properties key -- rename it in the frontend first`);
      continue;
    }
    // A key the client resolves itself -- a dynamic-form validator message or an authentication failure code -- is
    // stored with the "c." prefix, which NlsKeyMapper strips again. Without it, rule 4 would upper-case "required"
    // into "REQUIRED" and the validator would stop resolving.
    const storedKey = /[a-z]/.test(key) ? 'c.' + key : key;
    for (const language of LANGUAGES) {
      let found = false;
      for (const dir of sources) {
        const data = readJson(path.join(dir, language.json));
        if (key in data) {
          const value = data[key];
          if (typeof value === 'string') {
            moved[language.tag].set(storedKey, value.trim());
          } else if (value && typeof value === 'object' && !Array.isArray(value)) {
            // An object-valued key becomes one flat entry per leaf; NlsKeyMapper rebuilds the object for the client
            // provided the namespace is allow-listed in the module's nls-mapping.properties descriptor.
            for (const [leaf, leafValue] of Object.entries(value)) {
              if (typeof leafValue !== 'string') {
                problems.push(`${key}.${leaf} (${language.tag}): only string leaves can be nested`);
              } else {
                moved[language.tag].set(`${key}.${leaf}`, leafValue.trim());
              }
            }
          } else {
            problems.push(`${key} (${language.tag}): value is not a string, .properties cannot express it`);
          }
          found = true;
          break;
        }
      }
      if (!found) problems.push(`${key}: missing in ${language.json} -- fix the EN/DE gap before moving`);
    }
  }
  if (problems.length) {
    console.error(`Refusing to move, ${problems.length} problem(s):\n  ` + problems.join('\n  '));
    return 1;
  }

  const area = options.area || 'issue #214';
  for (const language of LANGUAGES) {
    const file = path.join(BUNDLES[module].dir, language.properties);
    const block = [``, `# --- migrated from the frontend: ${area} ---`];
    for (const [key, value] of moved[language.tag]) {
      block.push(`${key}=${escapePropertyValue(value)}`);
    }
    fs.appendFileSync(file, block.join('\n') + '\n', { encoding: 'utf8' });
    verifyUtf8(file);
  }
  for (const dir of sources) {
    for (const language of LANGUAGES) {
      const file = path.join(dir, language.json);
      const data = readJson(file);
      let changed = false;
      for (const key of keys) {
        if (key in data) {
          delete data[key];
          changed = true;
        }
      }
      if (changed) writeJson(file, data);
    }
  }
  console.log(`Moved ${keys.length} keys into ${BUNDLES[module].label}.`);
  console.log('Now regenerate the inventory and re-run the guards:');
  // -DskipTests, not -Dmaven.test.skip=true: the latter skips test compilation, so grafiosch-server-base would
  // install an empty tests jar and the following test run could not compile grafioschtrader-server's test sources.
  console.log('  cd backend && mvn -q -pl grafiosch-base,grafiosch-server-base,grafioschtrader-common install '
    + '-DskipTests && mvn -q -pl grafioschtrader-server test -Dtest="Nls*"');
  return 0;
}

/**
 * Permanent guard, meant for CI.
 *
 * Two things can regress now that the migration is done. A translation file could be re-introduced in the frontend,
 * quietly restoring the split this issue removed. And -- the more likely one -- a text used by src/app/lib could be
 * added to grafioschtrader-common, which works fine in the full application and breaks the moment the reusable
 * library is deployed on its own, because grafiosch-test-integration serves the grafiosch-base bundle alone.
 *
 * The second check is what makes standalone-library completeness verifiable at all: it compares the keys the library
 * layer actually references against the keys grafiosch-base actually ships.
 */
/**
 * Texts that src/app/lib references but only grafioschtrader-common ships.
 *
 * Only literal references can be found; keys composed at runtime (addColumnFeqH from a field name, 'HELP_' + x) are
 * invisible to any scan. The result is therefore a lower bound on the problem -- it under-reports, it does not invent.
 */
function keysOwnedByTheWrongModule() {
  const baseKeys = new Set(readInventory('base').keys());
  const commonKeys = [...readInventory('common').keys()];
  const libraryText = layerTexts().lib;
  return commonKeys
    .filter(key => !baseKeys.has(key) && !NOT_NLS_KEYS.has(key) && referencedIn(libraryText, key))
    .sort();
}

/** Moves the keys found by {@link keysOwnedByTheWrongModule} from grafioschtrader-common into grafiosch-base. */
function commandPromote(options) {
  const keys = keysOwnedByTheWrongModule();
  console.log(`${keys.length} key(s) must move from grafioschtrader-common to grafiosch-base.`);
  if (!options.apply) {
    console.log(keys.join('\n'));
    console.log('\nDry run. Re-run with --apply to move them.');
    return 0;
  }
  // The properties files are keyed by the RAW key, which is not always the client key: "note" is delivered as "NOTE",
  // "message.com.type" as "MESSAGE_COM_TYPE". Resolve back through the inventory before touching any file.
  const rawKeyOfClientKey = readInventory('common');
  const wanted = new Set(keys.map(key => rawKeyOfClientKey.get(key) ?? key));
  for (const language of LANGUAGES) {
    const commonFile = path.join(BUNDLES.common.dir, language.properties);
    const baseFile = path.join(BUNDLES.base.dir, language.properties);
    const kept = [];
    const taken = [];
    for (const line of fs.readFileSync(commonFile, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trimStart();
      const separator = trimmed.search(/[=:]/);
      const key = separator > 0 && !trimmed.startsWith('#') && !trimmed.startsWith('!')
        ? trimmed.slice(0, separator).trim() : null;
      if (key && wanted.has(key)) {
        taken.push(trimmed);
      } else {
        kept.push(line);
      }
    }
    fs.writeFileSync(commonFile, kept.join('\r\n'), {encoding: 'utf8'});
    fs.appendFileSync(baseFile, ['', '# --- moved from grafioschtrader-common: consumed by src/app/lib (issue #214) ---',
      ...taken, ''].join('\r\n'), {encoding: 'utf8'});
    verifyUtf8(commonFile);
    verifyUtf8(baseFile);
    console.log(`  ${language.tag}: moved ${taken.length} entries`);
  }
  return 0;
}

function commandCheck() {
  let failures = 0;

  for (const [label, dir] of [['src/assets/i18n', APP_I18N], ['src/app/lib/assets/i18n', LIB_I18N]]) {
    if (fs.existsSync(dir)) {
      failures++;
      console.error(`${label} exists again. Texts belong in the backend messages*.properties files (issue #214).`);
    }
  }

  const missing = keysOwnedByTheWrongModule();
  if (missing.length) {
    failures++;
    console.error(`${missing.length} key(s) are referenced by src/app/lib but shipped only by grafioschtrader-common. `
      + `A standalone grafiosch server would render them as raw keys. Move them to `
      + `backend/grafiosch-base/src/main/resources/i18n/messages*.properties:\n  ${missing.join('\n  ')}`);
  }

  console.log(failures === 0 ? 'check: OK' : `check: ${failures} problem group(s)`);
  return failures === 0 ? 0 : 1;
}

// ------------------------------------------------------------------------------------------------------ entry point

function parseArguments(argv) {
  const options = {};
  for (const argument of argv) {
    const match = /^--([^=]+)(?:=(.*))?$/.exec(argument);
    if (match) options[match[1]] = match[2] === undefined ? true : match[2];
  }
  return options;
}

const [command, ...rest] = process.argv.slice(2);
const options = parseArguments(rest);
const commands = {
  check: commandCheck, promote: commandPromote,
  scan: commandScan, reclassify: commandReclassify, shadow: commandShadow, move: commandMove
};

if (!commands[command]) {
  console.error(`Usage: node scripts/nls-tool.mjs <${Object.keys(commands).join('|')}> [options]`);
  process.exit(2);
}
try {
  process.exit(commands[command](options) || 0);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
