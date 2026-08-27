import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Guards the hand-maintained TypeScript mirrors of backend Java enums against drift.
 *
 * Some frontend dropdowns are built entirely from a TypeScript enum object rather than from a backend
 * endpoint. If a constant is added to the Java enum but forgotten in the mirror, the value can never be
 * selected in the UI and a reverse lookup renders `undefined` — a failure that is invisible until a user
 * misses the entry.
 *
 * A mirror enrolls itself by carrying the marker `Corresponds to backend: <path>` in its file comment,
 * where `<path>` is relative to the repository's `backend/` directory. Several comma-separated paths may be
 * named, in which case their constants are unioned — that is how one TypeScript enum mirrors both the core
 * protocol enum and the application enum that extends it. A file holding more than one enum adds a
 * `Mirrored enum: <Name>` line naming which of them the marker is about; without it the first enum in the
 * file is compared.
 */

/** This file lives in `frontend/src`, so the repository root is two levels up. */
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const FRONTEND_APP = path.join(REPO_ROOT, 'frontend/src/app');
const BACKEND = path.join(REPO_ROOT, 'backend');

/**
 * Picks up `* Corresponds to backend: grafiosch-base/src/main/java/.../TaskTypeBase.java`, and the
 * comma-separated multi-source form, which may wrap onto the following comment lines.
 */
const MARKER = /Corresponds to backend:\s*((?:[^\s,]+\.java)(?:\s*,\s*(?:\*\s*)?[^\s,]+\.java)*)/;

/** Picks up `* Mirrored enum: GTNetMessageCodeType`, naming which enum of the file the marker is about. */
const MIRRORED_ENUM = /Mirrored enum:\s*(\w+)/;

/** A Java enum constant with an explicit ordinal argument, e.g. `MY_TASK((byte) 54),` or `MY_TASK(54),`. */
const JAVA_CONSTANT = /^\s*([A-Z][A-Z0-9_]*)\s*\(\s*(?:\(byte\)\s*)?(-?\d+)\s*\)/gm;

/** A TypeScript enum member with an explicit numeric value, e.g. `MY_TASK = 54,`. */
const TS_CONSTANT = /^\s*([A-Z][A-Z0-9_]*)\s*=\s*(-?\d+)/gm;

/** One mirror file and the backend enums it claims to correspond to. */
interface MirrorPair {
  /** Repository-relative path of the TypeScript mirror, used in failure messages. */
  frontendPath: string;
  /** Repository-relative paths of the Java enums, as declared by the marker. */
  backendPaths: string[];
  /** Absolute path of the TypeScript mirror. */
  frontendFile: string;
  /** Absolute paths of the Java enums. */
  backendFiles: string[];
  /** Name of the TypeScript enum to compare, or undefined to take the first one in the file. */
  enumName?: string;
  /** All backend paths in one string, for the test title. */
  backendLabel: string;
}

/**
 * Removes line and block comments so that a constant mentioned in prose or in a commented-out block is
 * not mistaken for a declaration.
 *
 * @param source the raw file content
 * @returns the content with all comments replaced by nothing
 */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Extracts `name -> value` pairs with the given pattern.
 *
 * @param source comment-free source text
 * @param pattern a global regex whose first group is the constant name and second group its numeric value
 * @returns map of constant name to numeric value, in declaration order
 */
function collectConstants(source: string, pattern: RegExp): Map<string, number> {
  const constants = new Map<string, number>();
  for (const match of source.matchAll(new RegExp(pattern))) {
    constants.set(match[1], Number(match[2]));
  }
  return constants;
}

/**
 * Finds the body of an enum declaration.
 *
 * @param source comment-free source text
 * @param enumName the enum to look for, or undefined to take the first declaration in the file
 * @returns the text between the braces of that declaration, or null when it is not there
 */
function enumBody(source: string, enumName?: string): string | null {
  const declaration = enumName ? new RegExp(`\\benum\\s+${enumName}\\b`).exec(source) : /\benum\b/.exec(source);
  if (!declaration) {
    return null;
  }
  const bodyStart = source.indexOf('{', declaration.index);
  if (bodyStart < 0) {
    return null;
  }
  const bodyEnd = source.indexOf('}', bodyStart);
  return source.substring(bodyStart + 1, bodyEnd < 0 ? source.length : bodyEnd);
}

/**
 * Reads the constants of a Java enum. Only the enum body up to its first `;` is considered, so that
 * static fields and methods below the constant list cannot contribute false matches.
 *
 * @param file absolute path of the `.java` file
 * @returns map of constant name to numeric value
 */
function readJavaEnum(file: string): Map<string, number> {
  const body = enumBody(stripComments(fs.readFileSync(file, 'utf8')));
  if (body === null) {
    return new Map();
  }
  const semicolon = body.indexOf(';');
  return collectConstants(semicolon < 0 ? body : body.substring(0, semicolon), JAVA_CONSTANT);
}

/**
 * Reads the constants of every named Java enum and unions them.
 *
 * @param pair the mirror pair whose backend files are read
 * @returns map of constant name to numeric value across all sources
 */
function readJavaEnums(pair: MirrorPair): Map<string, number> {
  const constants = new Map<string, number>();
  const origin = new Map<string, string>();
  pair.backendFiles.forEach((file, index) => {
    for (const [name, value] of readJavaEnum(file)) {
      const existing = constants.get(name);
      expect(
        existing === undefined,
        `${name} is declared both in ${origin.get(name)} and in ${pair.backendPaths[index]}; ` +
          `a mirror cannot union two enums that overlap.`
      ).toBe(true);
      const clash = [...constants].find(([, other]) => other === value);
      expect(
        clash === undefined,
        `${pair.backendPaths[index]} reuses value ${value} for ${name}, already taken by ${clash?.[0]}.`
      ).toBe(true);
      constants.set(name, value);
      origin.set(name, pair.backendPaths[index]);
    }
  });
  return constants;
}

/**
 * Reads the members of the named TypeScript enum.
 *
 * @param file absolute path of the `.ts` mirror
 * @param enumName the enum to read, or undefined to take the first one in the file
 * @returns map of member name to numeric value
 */
function readTypescriptEnum(file: string, enumName?: string): Map<string, number> {
  const body = enumBody(stripComments(fs.readFileSync(file, 'utf8')), enumName);
  return body === null ? new Map() : collectConstants(body, TS_CONSTANT);
}

/**
 * Finds every TypeScript file below `frontend/src/app` that declares a backend correspondence.
 *
 * @returns the discovered mirror pairs, sorted by frontend path for a stable test order
 */
function findMirrorPairs(): MirrorPair[] {
  const pairs: MirrorPair[] = [];
  for (const entry of fs.readdirSync(FRONTEND_APP, { recursive: true, encoding: 'utf8' })) {
    if (!entry.endsWith('.ts') || entry.endsWith('.spec.ts')) {
      continue;
    }
    const frontendFile = path.join(FRONTEND_APP, entry);
    const source = fs.readFileSync(frontendFile, 'utf8');
    const marker = MARKER.exec(source);
    if (marker) {
      const backendPaths = marker[1]
        .split(',')
        .map((declared) =>
          declared
            .replace(/^\s*\*?\s*/, '')
            .trim()
            .replace(/\\/g, '/')
        )
        .filter((declared) => declared.length > 0);
      pairs.push({
        frontendPath: path.relative(REPO_ROOT, frontendFile).replace(/\\/g, '/'),
        backendPaths,
        frontendFile,
        backendFiles: backendPaths.map((backendPath) => path.join(BACKEND, backendPath)),
        enumName: MIRRORED_ENUM.exec(source)?.[1],
        backendLabel: backendPaths.map((backendPath) => path.basename(backendPath)).join(' + ')
      });
    }
  }
  return pairs.sort((a, b) => a.frontendPath.localeCompare(b.frontendPath));
}

/**
 * Describes how the mirror deviates from the backend enum, in the wording a developer needs to fix it.
 *
 * @param backendConstants constants of the Java enum
 * @param frontendConstants constants of the TypeScript mirror
 * @returns one line per deviation, empty when both sides agree
 */
function describeDrift(backendConstants: Map<string, number>, frontendConstants: Map<string, number>): string[] {
  const drift: string[] = [];
  for (const [name, value] of backendConstants) {
    if (!frontendConstants.has(name)) {
      drift.push(`missing in frontend: ${name} = ${value}`);
    } else if (frontendConstants.get(name) !== value) {
      drift.push(
        `value mismatch: ${name} is ${value} in the backend but ${frontendConstants.get(name)} in the frontend`
      );
    }
  }
  for (const [name, value] of frontendConstants) {
    if (!backendConstants.has(name)) {
      drift.push(`missing in backend: ${name} = ${value}`);
    }
  }
  return drift;
}

const mirrorPairs = findMirrorPairs();

describe('backend enums mirrored in the frontend', () => {
  it('finds at least one mirror declaring "Corresponds to backend:"', () => {
    expect(
      mirrorPairs.length,
      `No mirror found below ${FRONTEND_APP}. Either the marker convention was dropped or this test looks in the wrong place.`
    ).toBeGreaterThan(0);
  });

  it.each(mirrorPairs)('$frontendPath matches $backendLabel', (pair: MirrorPair) => {
    pair.backendFiles.forEach((backendFile, index) =>
      expect(
        fs.existsSync(backendFile),
        `${pair.frontendPath} points at ${pair.backendPaths[index]}, which does not exist. Update the "Corresponds to backend:" marker.`
      ).toBe(true)
    );

    const backendConstants = readJavaEnums(pair);
    const frontendConstants = readTypescriptEnum(pair.frontendFile, pair.enumName);

    expect(backendConstants.size, `No constants parsed from ${pair.backendLabel}.`).toBeGreaterThan(0);
    expect(
      frontendConstants.size,
      `No constants parsed from ${pair.frontendPath}${pair.enumName ? ` (enum ${pair.enumName})` : ''}.`
    ).toBeGreaterThan(0);

    const drift = describeDrift(backendConstants, frontendConstants);
    expect(drift, `${pair.frontendPath} has drifted from ${pair.backendLabel}:\n  ${drift.join('\n  ')}`).toEqual([]);
  });
});
