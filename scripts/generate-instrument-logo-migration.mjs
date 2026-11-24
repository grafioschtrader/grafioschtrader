#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BEGIN_MARKER = '-- BEGIN GENERATED INSTRUMENT_LOGO';
const END_MARKER = '-- END GENERATED INSTRUMENT_LOGO';
const MAX_NAME_LEN = 120;

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, '..');
const LOGOS_ROOT = path.join(REPO_ROOT, 'frontend', 'src', 'assets', 'company-logos');
const SOURCES_JSON = path.join(LOGOS_ROOT, 'sources.json');
const ISSUERS_JSON = path.join(LOGOS_ROOT, 'issuers.json');
const PROD_MIGRATION_DIR = path.join(
  REPO_ROOT,
  'backend',
  'grafioschtrader-server',
  'src',
  'main',
  'resources',
  'db',
  'migration'
);
const TEST_MIGRATION_DIR = path.join(
  REPO_ROOT,
  'backend',
  'grafioschtrader-server',
  'src',
  'test',
  'resources',
  'db',
  'migration',
  'test'
);

function parseArgs(argv) {
  let target = null;
  let version = null;
  let checkOnly = false;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--target') {
      target = argv[++i];
    } else if (arg.startsWith('--target=')) {
      target = arg.slice('--target='.length);
    } else if (arg === '--version') {
      version = argv[++i];
    } else if (arg.startsWith('--version=')) {
      version = arg.slice('--version='.length);
    } else if (arg === '--check') {
      checkOnly = true;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (target && version) {
    throw new Error(
      'Usage: node scripts/generate-instrument-logo-migration.mjs [--target <migration.sql> | --version <x.y.z>] [--check]'
    );
  }
  return {
    targetPath: target ? path.resolve(REPO_ROOT, target) : null,
    version: version ? version.trim().replace(/-SNAPSHOT$/i, '') : null,
    checkOnly
  };
}

function findLatestProdMigration() {
  const files = fs
    .readdirSync(PROD_MIGRATION_DIR)
    .filter((f) => /^V\d+(?:_\d+)*__.*\.sql$/i.test(f))
    .sort(compareMigrationVersions);
  if (files.length === 0) {
    throw new Error(`No Flyway migration files found in ${PROD_MIGRATION_DIR}`);
  }
  const latestVersion = parseMigrationVersion(files[files.length - 1]);
  const sameVersion = files.filter((f) => {
    const v = parseMigrationVersion(f);
    return v && v.length === latestVersion.length && v.every((n, i) => n === latestVersion[i]);
  });
  const withMarker = sameVersion.find((f) =>
    fs.readFileSync(path.join(PROD_MIGRATION_DIR, f), 'utf8').includes(BEGIN_MARKER)
  );
  return path.join(PROD_MIGRATION_DIR, withMarker ?? sameVersion[sameVersion.length - 1]);
}

function parseMigrationVersion(filename) {
  const match = /^V(\d+(?:_\d+)*)__/i.exec(filename);
  if (!match) {
    return null;
  }
  return match[1].split('_').map((part) => Number.parseInt(part, 10));
}

function compareMigrationVersions(a, b) {
  const va = parseMigrationVersion(a);
  const vb = parseMigrationVersion(b);
  if (va && vb) {
    const len = Math.max(va.length, vb.length);
    for (let i = 0; i < len; i++) {
      const diff = (va[i] ?? 0) - (vb[i] ?? 0);
      if (diff !== 0) {
        return diff;
      }
    }
  }
  return a.localeCompare(b);
}

function resolveTargetForVersion(version) {
  if (!/^\d+(?:\.\d+)*$/.test(version)) {
    throw new Error(`Invalid version format "${version}". Expected dot-separated numbers like 0.40.2`);
  }
  const prefix = `V${version.replace(/\./g, '_')}__`;
  const existing = fs
    .readdirSync(PROD_MIGRATION_DIR)
    .filter((f) => f.startsWith(prefix) && f.endsWith('.sql'))
    .sort();
  if (existing.length > 0) {
    // Prefer a file that already owns the generated block; otherwise append to the last migration of this version.
    const withMarker = existing.find((f) =>
      fs.readFileSync(path.join(PROD_MIGRATION_DIR, f), 'utf8').includes(BEGIN_MARKER)
    );
    return {
      targetPath: path.join(PROD_MIGRATION_DIR, withMarker ?? existing[existing.length - 1]),
      existed: true
    };
  }
  return {
    targetPath: path.join(PROD_MIGRATION_DIR, `${prefix}instrument_logo.sql`),
    existed: false
  };
}

function findMarkedTestMigration() {
  if (!fs.existsSync(TEST_MIGRATION_DIR)) {
    return null;
  }
  const marked = fs
    .readdirSync(TEST_MIGRATION_DIR)
    .filter((f) => /^V.+\.sql$/i.test(f))
    .sort(compareMigrationVersions)
    .filter((f) => fs.readFileSync(path.join(TEST_MIGRATION_DIR, f), 'utf8').includes(BEGIN_MARKER));
  return marked.length > 0 ? path.join(TEST_MIGRATION_DIR, marked[marked.length - 1]) : null;
}

function sha256Hex(bufferOrString) {
  return crypto.createHash('sha256').update(bufferOrString).digest('hex');
}

function sqlString(value) {
  if (value === null || value === undefined) {
    return 'NULL';
  }
  return "'" + String(value).replace(/\\/g, '\\\\').replace(/'/g, "''").replace(/\r/g, '').replace(/\n/g, '\\n') + "'";
}

function truncateWithSuffix(base, suffix) {
  if (base.length + suffix.length <= MAX_NAME_LEN) {
    return base + suffix;
  }
  return base.slice(0, MAX_NAME_LEN - suffix.length).trimEnd() + suffix;
}

function joinLinesOrNull(values) {
  if (!Array.isArray(values)) {
    return null;
  }
  const cleaned = values.map((v) => String(v).trim()).filter((v) => v.length > 0);
  return cleaned.length > 0 ? cleaned.join('\n') : null;
}

function loadCatalogEntries() {
  const sources = JSON.parse(fs.readFileSync(SOURCES_JSON, 'utf8'));
  const issuers = JSON.parse(fs.readFileSync(ISSUERS_JSON, 'utf8'));
  const entries = [];

  for (const sec of sources.securities ?? []) {
    if (!sec.path) {
      continue;
    }
    const assetPath = String(sec.path).replace(/\\/g, '/').trim();
    const absFile = path.join(LOGOS_ROOT, assetPath);
    if (!fs.existsSync(absFile)) {
      throw new Error(`Missing company SVG file for ${sec.isin}: ${absFile}`);
    }
    const fileBytes = fs.readFileSync(absFile);
    if (fileBytes.length === 0) {
      throw new Error(`Empty company SVG file: ${absFile}`);
    }
    const isin = sec.isin ? String(sec.isin).trim().toUpperCase() : null;
    const rawName = String(sec.name ?? '').trim();
    if (!rawName) {
      throw new Error(`Missing name for company logo ${assetPath}`);
    }
    entries.push({
      isin,
      rawName,
      ticker: sec.ticker ? String(sec.ticker).trim() : '',
      logoScope: 0,
      assetPath,
      sha256: sha256Hex(fileBytes),
      namePatterns: null,
      hosts: null,
      hostSuffixes: null
    });
  }

  for (const issuer of issuers.issuers ?? []) {
    if (!issuer.path) {
      continue;
    }
    const assetPath = String(issuer.path).replace(/\\/g, '/').trim();
    const absFile = path.join(LOGOS_ROOT, assetPath);
    if (!fs.existsSync(absFile)) {
      throw new Error(`Missing issuer SVG file for ${issuer.name}: ${absFile}`);
    }
    const fileBytes = fs.readFileSync(absFile);
    if (fileBytes.length === 0) {
      throw new Error(`Empty issuer SVG file: ${absFile}`);
    }
    const rawName = String(issuer.name ?? '').trim();
    if (!rawName) {
      throw new Error(`Missing name for issuer logo ${assetPath}`);
    }
    entries.push({
      isin: null,
      rawName,
      ticker: '',
      logoScope: 1,
      assetPath,
      sha256: sha256Hex(fileBytes),
      namePatterns: joinLinesOrNull(issuer.namePatterns),
      hosts: joinLinesOrNull(issuer.hosts),
      hostSuffixes: joinLinesOrNull(issuer.hostSuffixes)
    });
  }

  disambiguateAndValidate(entries);
  return entries;
}

function disambiguateAndValidate(entries) {
  const byLowerName = new Map();
  for (const entry of entries) {
    const baseName = entry.rawName.length > MAX_NAME_LEN ? entry.rawName.slice(0, MAX_NAME_LEN).trimEnd() : entry.rawName;
    entry.name = baseName;
    const key = baseName.toLowerCase();
    if (!byLowerName.has(key)) {
      byLowerName.set(key, []);
    }
    byLowerName.get(key).push(entry);
  }

  for (const group of byLowerName.values()) {
    if (group.length <= 1) {
      continue;
    }
    const tickers = group.map((e) => e.ticker.toUpperCase());
    const allHaveUniqueTicker = tickers.every((t) => t.length > 0) && new Set(tickers).size === group.length;
    for (const entry of group) {
      const suffix = allHaveUniqueTicker
        ? ` (${entry.ticker})`
        : entry.isin
          ? ` (${entry.isin})`
          : ' (Issuer)';
      entry.name = truncateWithSuffix(entry.name, suffix);
    }
  }

  const seenNames = new Set();
  const seenIsins = new Set();
  const seenPaths = new Set();
  for (const entry of entries) {
    const nameKey = entry.name.toLowerCase();
    if (seenNames.has(nameKey)) {
      throw new Error(`Duplicate resolved name in catalog: ${entry.name}`);
    }
    seenNames.add(nameKey);

    if (entry.isin) {
      const isinKey = entry.isin.toLowerCase();
      if (seenIsins.has(isinKey)) {
        throw new Error(`Duplicate ISIN in catalog: ${entry.isin}`);
      }
      seenIsins.add(isinKey);
    }

    const pathKey = entry.assetPath.toLowerCase();
    if (seenPaths.has(pathKey)) {
      throw new Error(`Duplicate asset_path in catalog: ${entry.assetPath}`);
    }
    seenPaths.add(pathKey);

    entry.rulesHash = sha256Hex(
      JSON.stringify([entry.namePatterns ?? '', entry.hosts ?? '', entry.hostSuffixes ?? ''])
    ).slice(0, 16);
    entry.fallbackName = truncateWithSuffix(
      entry.name,
      entry.isin ? ` (${entry.isin})` : ' (Standard)'
    );
  }
}

function stripGeneratedBlock(sql) {
  const start = sql.indexOf(BEGIN_MARKER);
  const end = sql.indexOf(END_MARKER);
  if (start === -1 || end === -1 || end < start) {
    return sql;
  }
  return sql.slice(0, start) + sql.slice(end + END_MARKER.length);
}

function scanPriorMigrations(targetPath) {
  const dir = path.dirname(targetPath);
  const targetAbs = path.resolve(targetPath);
  const targetFile = path.basename(targetAbs);
  const migrated = new Map();
  if (!fs.existsSync(dir)) {
    return migrated;
  }

  const files = fs
    .readdirSync(dir)
    .filter((f) => /^V.+\.sql$/i.test(f))
    .sort(compareMigrationVersions);

  const metaRegex = /^-- logo: ([^|\r\n]+) \| ([0-9a-f]{64}) \| ([0-9a-f]{16})$/gm;

  for (const file of files) {
    const fullPath = path.resolve(dir, file);
    if (fullPath !== targetAbs && compareMigrationVersions(file, targetFile) > 0) {
      continue;
    }
    let content = fs.readFileSync(fullPath, 'utf8');
    if (fullPath === targetAbs) {
      content = stripGeneratedBlock(content);
    }
    for (const match of content.matchAll(metaRegex)) {
      migrated.set(match[1].trim(), { sha256: match[2], rulesHash: match[3] });
    }
  }
  return migrated;
}

function buildGeneratedBlock(entries, priorMigrated) {
  const lines = [
    BEGIN_MARKER,
    `-- Generated by scripts/generate-instrument-logo-migration.mjs (${entries.length} catalog entries).`
  ];

  let insertedCount = 0;
  let updatedCount = 0;

  for (const entry of entries) {
    const prev = priorMigrated.get(entry.assetPath);
    if (!prev) {
      insertedCount++;
      lines.push(`-- logo: ${entry.assetPath} | ${entry.sha256} | ${entry.rulesHash}`);
      lines.push(
        `INSERT IGNORE INTO \`instrument_logo\` (\`isin\`, \`name\`, \`logo_scope\`, \`asset_path\`, \`content_sha256\`, \`name_patterns\`, \`hosts\`, \`host_suffixes\`, \`created_by\`, \`last_modified_by\`, \`version\`) VALUES (${sqlString(entry.isin)}, ${sqlString(entry.name)}, ${entry.logoScope}, ${sqlString(entry.assetPath)}, ${sqlString(entry.sha256)}, ${sqlString(entry.namePatterns)}, ${sqlString(entry.hosts)}, ${sqlString(entry.hostSuffixes)}, 0, 0, 0);`
      );
      const isinExpr = entry.isin
        ? `CASE WHEN EXISTS (SELECT 1 FROM \`instrument_logo\` WHERE \`isin\` = ${sqlString(entry.isin)}) THEN NULL ELSE ${sqlString(entry.isin)} END`
        : 'NULL';
      const nameExpr = `CASE WHEN EXISTS (SELECT 1 FROM \`instrument_logo\` WHERE \`name\` = ${sqlString(entry.name)}) THEN ${sqlString(entry.fallbackName)} ELSE ${sqlString(entry.name)} END`;
      lines.push(
        `INSERT IGNORE INTO \`instrument_logo\` (\`isin\`, \`name\`, \`logo_scope\`, \`asset_path\`, \`content_sha256\`, \`name_patterns\`, \`hosts\`, \`host_suffixes\`, \`created_by\`, \`last_modified_by\`, \`version\`) SELECT ${isinExpr}, ${nameExpr}, ${entry.logoScope}, ${sqlString(entry.assetPath)}, ${sqlString(entry.sha256)}, ${sqlString(entry.namePatterns)}, ${sqlString(entry.hosts)}, ${sqlString(entry.hostSuffixes)}, 0, 0, 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM \`instrument_logo\` WHERE \`asset_path\` = ${sqlString(entry.assetPath)});`
      );
    } else if (prev.sha256 !== entry.sha256 || prev.rulesHash !== entry.rulesHash) {
      updatedCount++;
      lines.push(`-- logo: ${entry.assetPath} | ${entry.sha256} | ${entry.rulesHash}`);
      lines.push(
        `UPDATE \`instrument_logo\` SET \`content_sha256\` = ${sqlString(entry.sha256)}, \`name_patterns\` = ${sqlString(entry.namePatterns)}, \`hosts\` = ${sqlString(entry.hosts)}, \`host_suffixes\` = ${sqlString(entry.hostSuffixes)}, \`last_modified_time\` = CURRENT_TIMESTAMP WHERE \`asset_path\` = ${sqlString(entry.assetPath)};`
      );
    }
  }

  lines.push(END_MARKER);
  return { block: lines.join('\n'), insertedCount, updatedCount };
}

function replaceBlockInFile(content, block) {
  const normalized = content.replace(/\r\n/g, '\n');
  const start = normalized.indexOf(BEGIN_MARKER);
  const end = normalized.indexOf(END_MARKER);
  if (start !== -1 && end !== -1 && end >= start) {
    return normalized.slice(0, start) + block + normalized.slice(end + END_MARKER.length);
  }
  const sep = normalized.endsWith('\n') ? '\n' : '\n\n';
  return normalized + sep + block + '\n';
}

function syncMigrationFile(targetPath, entries, checkOnly, allowCreate) {
  const existed = fs.existsSync(targetPath);
  if (!existed && !allowCreate) {
    throw new Error(`Target migration file does not exist: ${targetPath}`);
  }
  const priorMigrated = scanPriorMigrations(targetPath);
  const { block, insertedCount, updatedCount } = buildGeneratedBlock(entries, priorMigrated);
  const original = existed ? fs.readFileSync(targetPath, 'utf8') : '';
  const hasMarker = original.includes(BEGIN_MARKER) && original.includes(END_MARKER);

  if (insertedCount === 0 && updatedCount === 0 && !hasMarker) {
    console.log(`No new or updated instrument logos for ${path.basename(targetPath)}.`);
    return;
  }

  const updated = existed ? replaceBlockInFile(original, block) : `${block}\n`;

  if (checkOnly) {
    if (!existed || original.replace(/\r\n/g, '\n') !== updated) {
      console.error(`Out of date: ${targetPath}`);
      process.exit(1);
    }
    console.log(`Up to date: ${targetPath} (${insertedCount} inserts, ${updatedCount} updates)`);
    return;
  }

  fs.writeFileSync(targetPath, updated, 'utf8');
  const verb = existed ? 'Updated' : 'Created';
  console.log(
    `${verb} ${targetPath}: ${insertedCount} inserts, ${updatedCount} updates (${entries.length} total catalog entries).`
  );
}

function main() {
  const { targetPath, version, checkOnly } = parseArgs(process.argv.slice(2));
  const entries = loadCatalogEntries();

  if (targetPath) {
    syncMigrationFile(targetPath, entries, checkOnly, false);
    return;
  }

  if (version) {
    const resolved = resolveTargetForVersion(version);
    syncMigrationFile(resolved.targetPath, entries, checkOnly, !resolved.existed);
  } else {
    const latest = findLatestProdMigration();
    syncMigrationFile(latest, entries, checkOnly, false);
  }

  const markedTestMigration = findMarkedTestMigration();
  if (markedTestMigration) {
    syncMigrationFile(markedTestMigration, entries, checkOnly, false);
  }
}

main();
