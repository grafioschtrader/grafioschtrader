import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Keeps Optimus UI out of the mobile application. The application reuses files of `src/app`, and one value import of a
 * file that depends on Optimus UI would pull the desktop component library into the phone bundle. The check follows
 * every relative value import from `src/gt-mobile/main.ts`; type-only imports are erased by the compiler and ignored.
 */
const SRC = path.dirname(fileURLToPath(import.meta.url));
const ENTRY = path.join(SRC, 'gt-mobile/main.ts');
const FORBIDDEN = '@openng/optimus-ui';

/** Module specifiers of the value imports, re-exports and dynamic imports of one source. */
export function valueImportSpecifiers(source: string): string[] {
  const specifiers: string[] = [];
  const staticImport = /^\s*(import|export)\s+(?!type\b)(?:[^'"`;]*?\s+from\s+)?['"]([^'"]+)['"]/gm;
  const dynamicImport = /\bimport\(\s*['"]([^'"]+)['"]\s*\)/g;
  for (const match of source.matchAll(staticImport)) {
    specifiers.push(match[2]);
  }
  for (const match of source.matchAll(dynamicImport)) {
    specifiers.push(match[1]);
  }
  return specifiers;
}

function resolveRelative(fromFile: string, specifier: string): string | null {
  const base = path.resolve(path.dirname(fromFile), specifier);
  for (const candidate of [base + '.ts', path.join(base, 'index.ts'), base]) {
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
      return candidate;
    }
  }
  return null;
}

/** Walks the value imports from the entry and returns each file importing the forbidden package. */
function filesImportingForbidden(entry: string): string[] {
  const visited = new Set<string>();
  const offenders: string[] = [];
  const pending = [entry];
  while (pending.length) {
    const file = pending.pop();
    if (file === undefined || visited.has(file)) {
      continue;
    }
    visited.add(file);
    for (const specifier of valueImportSpecifiers(fs.readFileSync(file, 'utf8'))) {
      if (specifier.startsWith(FORBIDDEN)) {
        offenders.push(path.relative(SRC, file).replace(/\\/g, '/'));
      } else if (specifier.startsWith('.')) {
        const resolved = resolveRelative(file, specifier);
        if (resolved) {
          pending.push(resolved);
        }
      }
    }
  }
  return offenders;
}

describe('gt-mobile boundary', () => {
  it('recognizes value imports and skips type-only imports', () => {
    const source = [
      "import { A } from './a';",
      "import type { B } from './b';",
      "export { C } from './c';",
      "import './side-effect';",
      "const d = () => import('./d');"
    ].join('\n');
    expect(valueImportSpecifiers(source)).toEqual(['./a', './c', './side-effect', './d']);
  });

  it('reaches no Optimus UI import from the mobile entry point', () => {
    expect(filesImportingForbidden(ENTRY)).toEqual([]);
  });
});
