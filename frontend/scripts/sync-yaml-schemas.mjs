import { mkdir, readdir, copyFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Angular assets must be inside its workspace. These copies are generated, never edited or committed.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.resolve(root, '../backend/grafioschtrader-server/src/main/resources/schemas');
const target = path.join(root, '.generated/schemas');
await mkdir(target, { recursive: true });
for (const file of await readdir(source)) {
  if (file.endsWith('.json')) await copyFile(path.join(source, file), path.join(target, file));
}
