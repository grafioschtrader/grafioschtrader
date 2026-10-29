import assert from 'node:assert/strict';
import path from 'node:path';
import { test } from 'node:test';
import ts from 'typescript';
import { countByDirectory, findRegressions, lowerBaseline } from './strict-null-count.mjs';

test('an overall decrease cannot hide a regression in one module', () => {
  assert.deepEqual(findRegressions({ entities: 10, lib: 20 }, { entities: 1, lib: 21 }), [
    { directory: 'lib', before: 20, after: 21 }
  ]);
  assert.throws(() => lowerBaseline({ entities: 10, lib: 20 }, { entities: 1, lib: 21 }), /Refusing to increase/);
});

test('new modules start at zero and cleared modules stay protected', () => {
  const baseline = lowerBaseline({ entities: 10, lib: 20 }, { lib: 19 });
  assert.deepEqual(baseline, { entities: 0, lib: 19 });
  assert.equal(findRegressions(baseline, { entities: 1, lib: 19 }).length, 1);
  assert.equal(findRegressions(baseline, { lib: 19, transaction: 1 }).length, 1);
});

test('root files and diagnostics outside application modules are counted', () => {
  const app = path.resolve('fixture/src/app');
  const error = (fileName) => ({ category: ts.DiagnosticCategory.Error, file: fileName && { fileName } });
  assert.deepEqual(
    countByDirectory(
      [
        error(path.join(app, 'entities/security.ts')),
        error(path.join(app, 'entities/transaction.ts')),
        error(path.join(app, 'app.config.ts')),
        error(path.resolve('fixture/src/main.ts')),
        error(),
        { category: ts.DiagnosticCategory.Warning }
      ],
      app
    ),
    { _app: 1, _other: 2, entities: 2 }
  );
});

test('malformed baselines fail instead of silently allowing errors', () => {
  for (const invalid of [null, [], { lib: -1 }, { lib: '20' }, { lib: null }, { lib: 1.5 }]) {
    assert.throws(() => findRegressions(invalid, { lib: 1 }));
  }
});
