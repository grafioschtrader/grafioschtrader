import { readFileSync, writeFileSync } from 'node:fs';

// Optimus UI 2.0.2 recreates processed menu objects whenever its model changes, but its
// MenubarSub template tracks their identity. Use the hierarchical key already assigned by
// createProcessedItems to retain menu DOM nodes (Angular NG0956). Remove this workaround
// once the dependency ships stable tracking. Both compiled-template copies must agree.
const bundle = new URL('../node_modules/@openng/optimus-ui/fesm2022/openng-optimus-ui-menubar.mjs', import.meta.url);
const original = '@for (processedItem of items; track processedItem; let index = $index)';
const replacement = '@for (processedItem of items; track processedItem.key; let index = $index)';
const source = readFileSync(bundle, 'utf8');
const originalCount = source.split(original).length - 1;
const replacementCount = source.split(replacement).length - 1;

if (originalCount === 2 && replacementCount === 0) {
  writeFileSync(bundle, source.replaceAll(original, replacement), 'utf8');
  console.log('Applied Optimus menubar stable tracking fix.');
} else if (originalCount === 0 && replacementCount === 2) {
  console.log('Optimus menubar already uses stable tracking.');
} else {
  throw new Error('Optimus menubar template changed; review or remove fix-optimus-menubar-tracking.mjs.');
}
