import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const THIS_FILE = fileURLToPath(import.meta.url);
const SRC = path.dirname(THIS_FILE);

/** Finds Angular decorators, including aliases, without matching names in comments or string literals. */
function hasAngularDecorator(source: string, decoratorNames: readonly string[]): boolean {
  const parsed = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  const names = new Set(decoratorNames);
  for (const node of parsed.statements) {
    if (
      ts.isImportDeclaration(node) &&
      ts.isStringLiteral(node.moduleSpecifier) &&
      node.moduleSpecifier.text === '@angular/core'
    ) {
      const bindings = node.importClause?.namedBindings;
      if (bindings && ts.isNamedImports(bindings)) {
        for (const element of bindings.elements) {
          if (names.has((element.propertyName ?? element.name).text)) names.add(element.name.text);
        }
      }
    }
  }
  function visit(node: ts.Node): true | undefined {
    if (ts.isDecorator(node) && ts.isCallExpression(node.expression)) {
      const callee = node.expression.expression;
      if (
        (ts.isIdentifier(callee) && names.has(callee.text)) ||
        (ts.isPropertyAccessExpression(callee) && names.has(callee.name.text))
      )
        return true;
    }
    return ts.forEachChild(node, visit);
  }
  return !!visit(parsed);
}

/** Host bindings and listeners belong in component or directive host metadata. */
function hasLegacyHostDecorator(source: string): boolean {
  return hasAngularDecorator(source, ['HostListener', 'HostBinding']);
}

/** Detects EventEmitter construction, including imported aliases and namespace access. */
function hasLegacyEventEmitter(source: string): boolean {
  const parsed = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  const names = new Set(['EventEmitter']);
  for (const node of parsed.statements) {
    if (
      ts.isImportDeclaration(node) &&
      ts.isStringLiteral(node.moduleSpecifier) &&
      node.moduleSpecifier.text === '@angular/core'
    ) {
      const bindings = node.importClause?.namedBindings;
      if (bindings && ts.isNamedImports(bindings)) {
        for (const element of bindings.elements) {
          if ((element.propertyName ?? element.name).text === 'EventEmitter') names.add(element.name.text);
        }
      }
    }
  }
  function visit(node: ts.Node): true | undefined {
    if (ts.isNewExpression(node)) {
      const callee = node.expression;
      if (
        (ts.isIdentifier(callee) && names.has(callee.text)) ||
        (ts.isPropertyAccessExpression(callee) && callee.name.text === 'EventEmitter')
      )
        return true;
    }
    return ts.forEachChild(node, visit);
  }
  return !!visit(parsed);
}

/** Finds named Angular common imports, including aliases, without matching comments or template text. */
function hasAngularCommonImport(source: string, name: string): boolean {
  const parsed = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  return parsed.statements.some((node) => {
    if (
      !ts.isImportDeclaration(node) ||
      !ts.isStringLiteral(node.moduleSpecifier) ||
      node.moduleSpecifier.text !== '@angular/common'
    ) {
      return false;
    }
    const bindings = node.importClause?.namedBindings;
    return (
      bindings &&
      ts.isNamedImports(bindings) &&
      bindings.elements.some((element) => (element.propertyName ?? element.name).text === name)
    );
  });
}

/** Checks decorator metadata only: ngModelOptions.standalone still has a separate, necessary meaning. */
function hasRedundantStandaloneMetadata(source: string): boolean {
  const parsed = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  function visit(node: ts.Node): true | undefined {
    if (
      ts.isDecorator(node) &&
      ts.isCallExpression(node.expression) &&
      /^(Component|Directive|Pipe)$/.test(node.expression.expression.getText(parsed))
    ) {
      const metadata = node.expression.arguments[0];
      if (
        metadata &&
        ts.isObjectLiteralExpression(metadata) &&
        metadata.properties.some(
          (property) =>
            ts.isPropertyAssignment(property) &&
            property.name.getText(parsed) === 'standalone' &&
            property.initializer.kind === ts.SyntaxKind.TrueKeyword
        )
      ) {
        return true;
      }
    }
    return ts.forEachChild(node, visit);
  }
  return !!visit(parsed);
}

/** Collects source files of both hosts and the shared library, excluding this guard's examples. */
function collectSources(dir: string, extensions = ['.ts'], out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collectSources(full, extensions, out);
    } else if (extensions.includes(path.extname(entry.name)) && full !== THIS_FILE) {
      out.push(full);
    }
  }
  return out;
}

describe('Angular API ratchet', () => {
  it('matches legacy outputs and EventEmitter construction, including aliases', () => {
    expect(hasAngularDecorator('class Example { @Output() changed = emitter; }', ['Output'])).toBe(true);
    expect(
      hasAngularDecorator(
        "import { Output as Out } from '@angular/core'; class Example { @Out('changed') event = emitter; }",
        ['Output']
      )
    ).toBe(true);
    expect(hasAngularDecorator('class Example { readonly changed = output<string>(); }', ['Output'])).toBe(false);
    expect(hasAngularDecorator('// @Output() changed', ['Output'])).toBe(false);
    for (const source of [
      'const event = new EventEmitter<string>();',
      "import { EventEmitter as Emitter } from '@angular/core'; const event = new Emitter();",
      "import * as ng from '@angular/core'; const event = new ng.EventEmitter();"
    ]) {
      expect(hasLegacyEventEmitter(source), source).toBe(true);
    }
    for (const source of [
      'const event = output<string>();',
      '// new EventEmitter()',
      'const example = `new EventEmitter()`;'
    ]) {
      expect(hasLegacyEventEmitter(source), source).toBe(false);
    }
  });

  it('finds no Output decorators or EventEmitters in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8');
        return hasAngularDecorator(source, ['Output']) || hasLegacyEventEmitter(source);
      })
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(violations, 'Declare outputs with readonly name = output<T>(). See frontend/CLAUDE.md.').toEqual([]);
  });

  it('matches NgModule decorators, including aliases and namespace imports', () => {
    for (const source of [
      '@NgModule({}) class Example {}',
      "import { NgModule as Module } from '@angular/core'; @Module({}) class Example {}",
      "import * as ng from '@angular/core'; @ng.NgModule({}) class Example {}"
    ]) {
      expect(hasAngularDecorator(source, ['NgModule']), source).toBe(true);
    }
    for (const source of [
      '@Component({ imports: [FormsModule] }) class Example {}',
      '// @NgModule({})',
      'const example = `@NgModule({})`;'
    ]) {
      expect(hasAngularDecorator(source, ['NgModule']), source).toBe(false);
    }
  });

  it('finds no own NgModules in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => hasAngularDecorator(fs.readFileSync(file, 'utf8'), ['NgModule']))
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(violations, 'Use standalone components and import tuples. See frontend/CLAUDE.md.').toEqual([]);
  });

  it('matches legacy host decorators, including aliases and namespace imports', () => {
    for (const source of [
      "class Example { @HostListener('input', ['$event']) onInput(event) {} }",
      "class Example { @HostBinding('class.active') active = true; }",
      "import { HostListener as Listen } from '@angular/core'; class Example { @Listen('blur') blur() {} }",
      "import * as ng from '@angular/core'; class Example { @ng.HostBinding('attr.role') role = 'button'; }"
    ]) {
      expect(hasLegacyHostDecorator(source), source).toBe(true);
    }
    for (const source of [
      "@Directive({ host: { '(input)': 'onInput($event)', '[class.active]': 'active()' } }) class Example {}",
      "// @HostListener('blur')",
      "const example = `@HostBinding('class.active')`;"
    ]) {
      expect(hasLegacyHostDecorator(source), source).toBe(false);
    }
  });

  it('finds no HostListener or HostBinding decorators in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => hasLegacyHostDecorator(fs.readFileSync(file, 'utf8')))
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(
      violations,
      'Declare host listeners and bindings in decorator host metadata. See frontend/CLAUDE.md.'
    ).toEqual([]);
  });

  it('matches ngStyle bindings and imports, including aliases', () => {
    expect(/\[ngStyle\]/.test('<div [ngStyle]="styles">')).toBe(true);
    expect(/\[ngStyle\]/.test('<div [style.width.px]="width" [style]="styles">')).toBe(false);
    expect(hasAngularCommonImport("import { NgStyle as Styles } from '@angular/common';", 'NgStyle')).toBe(true);
    expect(hasAngularCommonImport("import { NgTemplateOutlet } from '@angular/common';", 'NgStyle')).toBe(false);
    expect(hasAngularCommonImport("// import { NgStyle } from '@angular/common';", 'NgStyle')).toBe(false);
  });

  it('finds no ngStyle bindings or imports in inline and external templates', () => {
    const violations = collectSources(SRC, ['.ts', '.html'])
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8');
        return /\[ngStyle\]/.test(source) || (file.endsWith('.ts') && hasAngularCommonImport(source, 'NgStyle'));
      })
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(violations, 'Use native style bindings. See frontend/CLAUDE.md.').toEqual([]);
  });

  it('matches ngClass bindings and imports, including aliases', () => {
    expect(/\[ngClass\]/.test('<div [ngClass]="classes">')).toBe(true);
    expect(/\[ngClass\]/.test('<div [class.active]="active" [class]="classes">')).toBe(false);
    expect(hasAngularCommonImport("import { NgClass as Classes } from '@angular/common';", 'NgClass')).toBe(true);
    expect(hasAngularCommonImport("import { NgStyle } from '@angular/common';", 'NgClass')).toBe(false);
    expect(hasAngularCommonImport("// import { NgClass } from '@angular/common';", 'NgClass')).toBe(false);
  });

  it('finds no ngClass bindings or imports in inline and external templates', () => {
    const violations = collectSources(SRC, ['.ts', '.html'])
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8');
        return /\[ngClass\]/.test(source) || (file.endsWith('.ts') && hasAngularCommonImport(source, 'NgClass'));
      })
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(violations, 'Use native class bindings. See frontend/CLAUDE.md.').toEqual([]);
  });

  it('matches CommonModule imports, including aliases, but not individual imports or comments', () => {
    for (const source of [
      "import { CommonModule } from '@angular/common';",
      'import { NgClass, CommonModule as Common } from "@angular/common";'
    ]) {
      expect(hasAngularCommonImport(source, 'CommonModule'), source).toBe(true);
    }
    for (const source of [
      "import { NgClass, NgStyle } from '@angular/common';",
      "// import { CommonModule } from '@angular/common';",
      "const template = `import { CommonModule } from '@angular/common';`;"
    ]) {
      expect(hasAngularCommonImport(source, 'CommonModule'), source).toBe(false);
    }
  });

  it('finds no CommonModule imports in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => hasAngularCommonImport(fs.readFileSync(file, 'utf8'), 'CommonModule'))
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(
      violations,
      'Import only the common directives and pipes used by the template. See frontend/CLAUDE.md.'
    ).toEqual([]);
  });

  it('matches explicit standalone defaults with different spacing', () => {
    for (const source of [
      '@Component({ standalone: true, imports: [] })',
      '@Directive({standalone:true})',
      '@Pipe({ standalone :\n true })'
    ]) {
      expect(hasRedundantStandaloneMetadata(source + '\nclass Example {}'), source).toBe(true);
    }
    for (const source of [
      '@Component({ imports: [] })',
      '@Directive({ standalone: false })',
      '@Component({ template: `<input [ngModelOptions]="{ standalone: true }">` })',
      '// @Component({ standalone: true })',
      'const standalone = true;',
      'notstandalone: true',
      'standalone: trueValue'
    ]) {
      expect(hasRedundantStandaloneMetadata(source + '\nclass Example {}'), source).toBe(false);
    }
  });

  it('finds no redundant standalone metadata in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => hasRedundantStandaloneMetadata(fs.readFileSync(file, 'utf8')))
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(
      violations,
      'Omit standalone: true from decorators; standalone is the default. See frontend/CLAUDE.md.'
    ).toEqual([]);
  });
});
