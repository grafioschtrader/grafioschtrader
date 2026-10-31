import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
/** Check initialization and prevent assertions from bypassing it in every application source. */
function initializationProblems(program: ts.Program): { problems: string[]; checked: number } {
  const problems: string[] = [];
  let checked = 0;
  for (const source of program.getSourceFiles()) {
    const relativeFile = path.relative(ROOT, source.fileName).replace(/\\/g, '/');
    if (source.isDeclarationFile || relativeFile.startsWith('../') || relativeFile.includes('node_modules/')) continue;
    const diagnostics = program.getSemanticDiagnostics(source).filter((d) => [2564, 2565, 2729].includes(d.code));
    for (const diagnostic of diagnostics) {
      const line = source.getLineAndCharacterOfPosition(diagnostic.start ?? 0).line + 1;
      problems.push(`${relativeFile}:${line} ${ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ')}`);
    }
    const visit = (node: ts.Node): void => {
      if (ts.isPropertyDeclaration(node)) {
        checked++;
        if (node.exclamationToken) {
          const line = source.getLineAndCharacterOfPosition(node.name.getStart()).line + 1;
          problems.push(`${relativeFile}:${line} ${node.name.getText(source)} uses a definite-assignment assertion`);
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  return { problems, checked };
}

function fixtureProgram(source: string, relativeFile = 'resource-fixture.ts'): ts.Program {
  const file = path.join(ROOT, relativeFile).replace(/\\/g, '/');
  const options: ts.CompilerOptions = { strict: true, noEmit: true, target: ts.ScriptTarget.ES2022, types: [] };
  const host = ts.createCompilerHost(options);
  const readSourceFile = host.getSourceFile;
  host.getSourceFile = (filename, languageVersion, onError, shouldCreateNewSourceFile) =>
    filename.replace(/\\/g, '/') === file
      ? ts.createSourceFile(filename, source, languageVersion, true)
      : readSourceFile(filename, languageVersion, onError, shouldCreateNewSourceFile);
  return ts.createProgram([file], options, host);
}

describe('property initialization ratchet', () => {
  it('checks ordinary model and configuration fields as well as resources', () => {
    const result = initializationProblems(
      fixtureProgram(`
      export class Cache { model: object; config: object; asserted!: string; }
    `)
    );
    expect(result.checked).toBe(3);
    expect(result.problems).toHaveLength(3);
    expect(result.problems.some((problem) => problem.includes("Property 'model'"))).toBe(true);
    expect(result.problems.some((problem) => problem.includes("Property 'config'"))).toBe(true);
    expect(result.problems.some((problem) => problem.includes('asserted uses a definite-assignment assertion'))).toBe(
      true
    );
  });

  it('rejects accesses before constructor initialization', () => {
    const result = initializationProblems(
      fixtureProgram(`
      export class Config {
        readonly initial = this.name;
        name: string;
        constructor() { this.name = 'loaded'; }
      }
    `)
    );
    expect(result.problems.some((problem) => problem.includes('before its initialization'))).toBe(true);
  });

  it('accepts absent handles and definite constructor assignments, and rejects missing initialization and assertions', () => {
    const program = fixtureProgram(`
      export {};
      class Subscription { unsubscribe(): void {} }
      interface ResizeObserver { disconnect(): void; }
      interface MutationObserver { disconnect(): void; }
      interface AfterRenderRef { destroy(): void; }
      declare function afterNextRender(): AfterRenderRef;
      type Request = Subscription;
      class Valid {
        optional?: Subscription;
        unset: ResizeObserver | undefined;
        nullable: MutationObserver | null = null;
        aggregate = new Subscription();
        readonly required: Subscription;
        render?: ReturnType<typeof afterNextRender>;
        constructor(enabled: boolean) {
          if (enabled) this.required = new Subscription();
          else this.required = new Subscription();
        }
      }
      class Invalid {
        missing: Request;
        nullable: Subscription | null;
        asserted!: Subscription;
        render: ReturnType<typeof afterNextRender>;
        conditional: Subscription;
        indirect: Subscription;
        constructor(enabled: boolean) {
          if (enabled) this.conditional = new Subscription();
          this.initialize();
        }
        initialize(): void { this.indirect = new Subscription(); }
      }
    `);
    const result = initializationProblems(program);
    expect(result.checked).toBe(12);
    expect(result.problems).toHaveLength(6);
    for (const field of ['missing', 'nullable', 'asserted', 'render', 'conditional', 'indirect']) {
      expect(result.problems.some((problem) => problem.includes(field))).toBe(true);
    }
  });

  it('keeps all application fields initialized without definite-assignment assertions', () => {
    const filename = path.join(ROOT, 'tsconfig.spec.json');
    const config = ts.readConfigFile(filename, ts.sys.readFile);
    expect(config.error).toBeUndefined();
    const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, ROOT);
    expect(parsed.errors).toEqual([]);
    expect(parsed.options.strict).toBe(true);
    const program = ts.createProgram(parsed.fileNames, {
      ...parsed.options,
      noEmit: true
    });
    const result = initializationProblems(program);
    expect(result.checked).toBeGreaterThan(100);
    expect(result.problems).toEqual([]);
  }, 30000);
});
