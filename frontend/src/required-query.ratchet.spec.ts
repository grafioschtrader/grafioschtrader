import { parseTemplate, TmplAstElement, TmplAstNode, TmplAstTemplate } from '@angular/compiler';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

type Query = { kind: 'selector' | 'reference'; value: string };
type SourceClass = {
  name: string;
  file: string;
  source: ts.SourceFile;
  declaration: ts.ClassDeclaration;
  bases: string[];
  template?: string;
  selector?: string;
};

/** A structural block can remove its descendants; an ng-template's own reference still exists. */
function hasUnconditionalTarget(template: string, query: Query): boolean {
  const parsed = parseTemplate(template, 'required-query');
  if (parsed.errors?.length) throw new Error(parsed.errors.map((error) => error.toString()).join('\n'));
  const visit = (nodes: TmplAstNode[]): boolean =>
    nodes.some((node) => {
      if (node instanceof TmplAstElement || node instanceof TmplAstTemplate) {
        if (query.kind === 'reference' && node.references.some((ref) => ref.name === query.value)) return true;
        if (node instanceof TmplAstElement) {
          return (query.kind === 'selector' && node.name === query.value) || visit(node.children);
        }
      }
      return false;
    });
  return visit(parsed.nodes);
}

function sourceClasses(): Map<string, SourceClass> {
  const root = dirname(fileURLToPath(import.meta.url));
  const classes = new Map<string, SourceClass>();
  for (const relative of readdirSync(root, { recursive: true })) {
    if (typeof relative !== 'string' || !relative.endsWith('.ts') || relative.endsWith('.spec.ts')) continue;
    const file = join(root, relative);
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
    const visit = (node: ts.Node): void => {
      if (ts.isClassDeclaration(node) && node.name) {
        const entry: SourceClass = {
          name: node.name.text,
          file,
          source,
          declaration: node,
          bases: (node.heritageClauses ?? [])
            .filter((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)
            .flatMap((clause) =>
              clause.types.map((type: ts.ExpressionWithTypeArguments) => type.expression.getText(source))
            )
        };
        for (const decorator of ts.getDecorators(node) ?? []) {
          if (
            !ts.isCallExpression(decorator.expression) ||
            decorator.expression.expression.getText(source) !== 'Component'
          )
            continue;
          const metadata = decorator.expression.arguments[0];
          if (!metadata || !ts.isObjectLiteralExpression(metadata))
            throw new Error(`Non-literal component metadata: ${file}`);
          for (const property of metadata.properties) {
            if (!ts.isPropertyAssignment(property) || !ts.isStringLiteralLike(property.initializer)) continue;
            const name = property.name.getText(source);
            if (name === 'selector') entry.selector = property.initializer.text;
            if (name === 'template') entry.template = property.initializer.text;
            if (name === 'templateUrl')
              entry.template = readFileSync(resolve(dirname(file), property.initializer.text), 'utf8');
          }
        }
        classes.set(entry.name, entry);
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  return classes;
}

describe('required view query contracts', () => {
  it('distinguishes static elements/references from conditional and deferred children', () => {
    const form: Query = { kind: 'selector', value: 'dynamic-form' };
    expect(hasUnconditionalTarget('<p-panel><dynamic-form /></p-panel>', form)).toBe(true);
    expect(hasUnconditionalTarget('@if (ready) { <dynamic-form /> }', form)).toBe(false);
    expect(hasUnconditionalTarget('@defer { <dynamic-form /> }', form)).toBe(false);
    expect(hasUnconditionalTarget('<ng-template><dynamic-form /></ng-template>', form)).toBe(false);
    expect(
      hasUnconditionalTarget('<ng-template #row><span></span></ng-template>', { kind: 'reference', value: 'row' })
    ).toBe(true);
  });

  it('requires every concrete descendant to render the target of a required query unconditionally', () => {
    const classes = sourceClasses();
    const derivesFrom = (entry: SourceClass, base: string, seen = new Set<string>()): boolean => {
      if (seen.has(entry.name)) return false;
      seen.add(entry.name);
      return (
        entry.name === base ||
        entry.bases.some((name) => {
          const parent = classes.get(name);
          return parent != null && derivesFrom(parent, base, seen);
        })
      );
    };
    const violations: string[] = [];
    let checked = 0;
    for (const owner of classes.values()) {
      for (const member of owner.declaration.members) {
        if (!ts.isPropertyDeclaration(member) || !member.initializer || !ts.isCallExpression(member.initializer))
          continue;
        const expression = member.initializer;
        if (expression.expression.getText(owner.source) !== 'viewChild.required') continue;
        const argument = expression.arguments[0];
        if (!argument) throw new Error(`Missing query target: ${owner.file}`);
        const value = ts.isStringLiteralLike(argument)
          ? argument.text
          : classes.get(argument.getText(owner.source))?.selector;
        if (!value)
          throw new Error(`Cannot resolve required query target: ${owner.name}.${member.name.getText(owner.source)}`);
        const query: Query = { kind: ts.isStringLiteralLike(argument) ? 'reference' : 'selector', value };
        for (const editor of classes.values()) {
          if (editor.template == null || !derivesFrom(editor, owner.name)) continue;
          checked++;
          if (!hasUnconditionalTarget(editor.template, query))
            violations.push(`${owner.name}.${member.name.getText(owner.source)} -> ${editor.name}`);
        }
      }
    }
    expect(checked).toBeGreaterThan(0);
    expect(violations).toEqual([]);
  });
});
