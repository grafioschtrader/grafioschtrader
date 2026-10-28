import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const THIS_FILE = fileURLToPath(import.meta.url);
const SRC = path.dirname(THIS_FILE);

/** Runtime configuration and screen-selected strategies remain explicit in the table hierarchy. */
const TABLE_CONSTRUCTOR_PARAMETERS: Record<string, { parameters: string[]; reason: string }> = {
  'app/gtnet/component/gtnet-exchange-base.component.ts': {
    parameters: ['entityName:string'],
    reason: 'The concrete exchange view chooses its entity name.'
  },
  'app/lib/datashowbase/table.crud.support.menu.security.ts': {
    parameters: ['entityName:string', 'deleteService:DeleteService', 'crudMenuOptions:CrudMenuOptions[]'],
    reason: 'The view chooses the entity, deletion strategy and permitted menu operations.'
  },
  'app/lib/datashowbase/table.crud.support.menu.ts': {
    parameters: ['entityName:string', 'deleteService:DeleteService', 'crudMenuOptions:CrudMenuOptions[]'],
    reason: 'DeleteService is an interface implemented by the concrete entity service, not an injectable token.'
  },
  'app/lib/udfmeta/components/udf.metadata.table.ts': {
    parameters: [
      'classz:ClassConstructor<T>',
      'deleteReadAllService:DeleteReadAllService<UDFMetadata>',
      'entityName:string'
    ],
    reason: 'The metadata view supplies its DTO class, entity name and read/delete strategy.'
  },
  'app/shared/securitycurrency/split.period.table.base.ts': {
    parameters: [
      'dataSortKey:string',
      'maxRowMessageKey:string',
      'classz:ClassConstructor<T>',
      'deleteCreateMultipleService:DeleteCreateMultiple<T>'
    ],
    reason: 'The concrete period view supplies its keys, DTO class and creation/deletion strategy.'
  },
  'app/watchlist/component/add-instrument-table.component.ts': {
    parameters: ['instance:T', 'addSearchToListService:AddSearchToListService<T>'],
    reason: 'The caller chooses the target list and the strategy that adds instruments to it.'
  },
  'app/watchlist/component/dividend.split.table.base.ts': {
    parameters: ['keyfield:string', 'sortField:string', 'groupTitle:string'],
    reason: 'Dividend and split views supply their own key, ordering and group heading.'
  },
  'app/watchlist/component/watchlist.table.ts': {
    parameters: ['watchlistType:WatchListType', 'storeKey:string', "selectMultiMode:'single'|'multiple'"],
    reason: 'Each watchlist tab chooses its kind, persisted settings key and selection mode.'
  }
};

/** Only dialog configuration and the screen-selected persistence strategy remain explicit. */
const FORM_CONSTRUCTOR_PARAMETERS: Record<string, { parameters: string[]; reason: string }> = {
  'app/algo/component/algo.assetclass.security.base.edit.ts': {
    parameters: ['i18nRecord:string', 'serviceEntityUpdate:ServiceEntityUpdate<T>'],
    reason: 'The concrete algo editor chooses its record label and persistence strategy.'
  },
  'app/historyquote/component/historyquote-edit.base.ts': {
    parameters: ['helpId:string', 'i18nRecord:string', 'serviceEntityUpdate:ServiceEntityUpdate<T>'],
    reason: 'History and legacy history editors choose their help, record label and persistence strategy.'
  },
  'app/lib/edit/simple.dynamic.edit.base.ts': {
    parameters: ['helpId:string', 'serviceEntityUpdate:ServiceEntityUpdate<T>'],
    reason: 'The dynamic dialog chooses its help and entity update strategy; dialog context is injected.'
  },
  'app/lib/edit/simple.edit.base.ts': {
    parameters: ['helpId:string'],
    reason: 'Each dialog chooses its own help page.'
  },
  'app/lib/edit/simple.entity.edit.base.ts': {
    parameters: ['helpId:string', 'i18nRecord:string', 'serviceEntityUpdate:ServiceEntityUpdate<T>'],
    reason: 'ServiceEntityUpdate is an interface supplied by the concrete entity editor, not an injectable token.'
  },
  'app/lib/udfmeta/components/base.udf.data.edit.ts': {
    parameters: ['helpId:string'],
    reason: 'The application chooses the help page of its custom-field editor.'
  },
  'app/standingorder/component/standing-order-edit-base.ts': {
    parameters: ['helpId:string'],
    reason: 'Cash and security standing-order editors have different help pages.'
  },
  'app/udfmetasecurity/components/asset.class.type.spec.instrument.ts': {
    parameters: [
      'fieldCategoryType:string',
      'fieldSpecialInvestmentInstrument:string',
      'helpId:string',
      'i18nRecord:string',
      'serviceEntityUpdate:ServiceEntityUpdate<T>'
    ],
    reason: 'The concrete asset-class or custom-field editor chooses field names, help, label and persistence strategy.'
  }
};

/** The master-detail view chooses its identity fields, help page and deletion strategy. */
const MASTER_CONSTRUCTOR_PARAMETERS: Record<string, { parameters: string[]; reason: string }> = {
  'app/lib/masterdetail/component/single.record.master.view.base.ts': {
    parameters: ['helpId:string', 'mainFieldId:string', 'entityName:string', 'deleteService:DeleteService'],
    reason: 'The concrete view chooses its help, master field, entity label and entity-specific deletion strategy.'
  }
};

/** Follows inheritance across files, without matching constructor examples in comments or strings. */
function hierarchyConstructorParameters(
  sources: Record<string, string>,
  rootName = 'ShowRecordConfigBase'
): Record<string, string[]> {
  const classes = new Map<string, { file: string; base?: string; parameters: string[] }>();
  for (const [file, source] of Object.entries(sources)) {
    const parsed = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
    for (const node of parsed.statements) {
      if (!ts.isClassDeclaration(node) || !node.name) continue;
      const base = node.heritageClauses?.find((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)?.types[0];
      classes.set(node.name.text, {
        file,
        base: base?.expression.getText(parsed),
        parameters: (node.members.find(ts.isConstructorDeclaration)?.parameters ?? []).map((parameter) =>
          `${parameter.name.getText(parsed)}:${parameter.type?.getText(parsed) ?? 'any'}`.replace(/\s/g, '')
        )
      });
    }
  }
  function belongs(name: string, visited = new Set<string>()): boolean {
    if (name === rootName) return true;
    if (visited.has(name)) return false;
    visited.add(name);
    const base = classes.get(name)?.base;
    return !!base && belongs(base, visited);
  }
  const result: Record<string, string[]> = {};
  for (const [name, declaration] of classes) {
    if (belongs(name) && declaration.parameters.length) result[declaration.file] = declaration.parameters;
  }
  return result;
}

/** Request coordination remains here until the resource migration in specification §4.4. */
const ON_CHANGES_REQUESTS: Record<string, string> = {
  'app/historyquote/component/historyquote-legacy.component.ts': 'Loads the selected instrument and legacy history.',
  'app/historyquote/component/historyquote-table.component.ts': 'Loads history for the current time-series parameters.',
  'app/securitycurrency/component/security-historyquote-quality-table.component.ts':
    'Replaces the quality-data request.',
  'app/standingorder/component/standing-order-transaction-table.component.ts':
    'Loads the portfolio before its transactions.',
  'app/taxdata/component/tax-exchange-rate-table.component.ts': 'Loads the selected tax year rates.',
  'app/taxdata/component/tax-year-correction-table.component.ts':
    'Loads corrections for the instrument and selected year.',
  'app/udfmetasecurity/components/udf-fill-suggest-test-result.component.ts':
    'Starts the current fill-suggestion request.'
};

function legacyChangeHandling(source: string): { hook: boolean; initializationFlag: boolean } {
  const parsed = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  const result = { hook: false, initializationFlag: false };
  function visit(node: ts.Node): void {
    if ((ts.isMethodDeclaration(node) || ts.isPropertyDeclaration(node)) && node.name) {
      const name = ts.isIdentifier(node.name) || ts.isStringLiteral(node.name) ? node.name.text : '';
      result.hook ||= name === 'ngOnChanges';
      result.initializationFlag ||= name === 'fieldsInitialized';
    }
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  return result;
}

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
  it('finds constructor parameters through the table inheritance chain and ignores unrelated classes', () => {
    expect(
      hierarchyConstructorParameters({
        'base.ts': 'class ShowRecordConfigBase {} class TableBase extends ShowRecordConfigBase {}',
        'view.ts': 'class View extends TableBase { constructor(private gps: GlobalparameterService) { super(); } }',
        'unrelated.ts': 'class Other { constructor(service: Service) {} } // class Example extends TableBase {}',
        'text.ts': 'const example = `class Example extends TableBase { constructor(service: Service) {} }`;'
      })
    ).toEqual({ 'view.ts': ['gps:GlobalparameterService'] });
    expect(
      hierarchyConstructorParameters({
        'base.ts': 'class ShowRecordConfigBase { protected gps = inject(GlobalparameterService); }',
        'view.ts': 'class View extends ShowRecordConfigBase { constructor() { super(); } }'
      })
    ).toEqual({});
  });

  it('keeps only documented configuration parameters in the table and record hierarchy', () => {
    const sources = Object.fromEntries(
      collectSources(SRC).map((file) => [path.relative(SRC, file).replace(/\\/g, '/'), fs.readFileSync(file, 'utf8')])
    );
    const allowed = Object.fromEntries(
      Object.entries(TABLE_CONSTRUCTOR_PARAMETERS).map(([file, entry]) => [file, entry.parameters])
    );
    expect(
      hierarchyConstructorParameters(sources),
      'Resolve table dependencies with inject(). See frontend/CLAUDE.md.'
    ).toEqual(allowed);
    expect(Object.values(TABLE_CONSTRUCTOR_PARAMETERS).every((entry) => entry.reason.length > 0)).toBe(true);
  });

  it('keeps only documented configuration parameters in the form and edit-dialog hierarchy', () => {
    expect(
      hierarchyConstructorParameters(
        {
          'base.ts': 'class FormBase {} class SimpleEditBase extends FormBase {}',
          'dialog.ts':
            'class Editor extends SimpleEditBase { constructor(private service: EntityService) { super(); } }',
          'other.ts': 'class Other { constructor(service: EntityService) {} }'
        },
        'FormBase'
      )
    ).toEqual({ 'dialog.ts': ['service:EntityService'] });
    const sources = Object.fromEntries(
      collectSources(SRC).map((file) => [path.relative(SRC, file).replace(/\\/g, '/'), fs.readFileSync(file, 'utf8')])
    );
    const allowed = Object.fromEntries(
      Object.entries(FORM_CONSTRUCTOR_PARAMETERS).map(([file, entry]) => [file, entry.parameters])
    );
    expect(
      hierarchyConstructorParameters(sources, 'FormBase'),
      'Resolve dialog dependencies with inject(). See frontend/CLAUDE.md.'
    ).toEqual(allowed);
    expect(Object.values(FORM_CONSTRUCTOR_PARAMETERS).every((entry) => entry.reason.length > 0)).toBe(true);
  });

  it('keeps only documented configuration parameters in the master-detail hierarchy', () => {
    const sources = Object.fromEntries(
      collectSources(SRC).map((file) => [path.relative(SRC, file).replace(/\\/g, '/'), fs.readFileSync(file, 'utf8')])
    );
    const allowed = Object.fromEntries(
      Object.entries(MASTER_CONSTRUCTOR_PARAMETERS).map(([file, entry]) => [file, entry.parameters])
    );
    expect(
      hierarchyConstructorParameters(sources, 'SingleRecordMasterViewBase'),
      'Resolve master-detail dependencies with inject(). See frontend/CLAUDE.md.'
    ).toEqual(allowed);
    expect(Object.values(MASTER_CONSTRUCTOR_PARAMETERS).every((entry) => entry.reason.length > 0)).toBe(true);
  });

  it('matches change hooks and initialization flags without matching examples in comments or strings', () => {
    expect(legacyChangeHandling('class Example { ngOnChanges() {} }')).toEqual({
      hook: true,
      initializationFlag: false
    });
    expect(legacyChangeHandling('class Example { ngOnChanges = () => {}; }').hook).toBe(true);
    expect(legacyChangeHandling('class Example { private fieldsInitialized = false; }')).toEqual({
      hook: false,
      initializationFlag: true
    });
    for (const source of [
      'class Example { readonly rows = computed(() => this.data()); }',
      '// ngOnChanges() {} fieldsInitialized = true',
      'const example = `ngOnChanges() {} fieldsInitialized = true`;'
    ]) {
      expect(legacyChangeHandling(source), source).toEqual({ hook: false, initializationFlag: false });
    }
  });

  it('keeps only the request hooks awaiting resource migration and no fieldsInitialized flags', () => {
    const hooks: string[] = [];
    const initializationFlags: string[] = [];
    for (const file of collectSources(SRC)) {
      const relative = path.relative(SRC, file).replace(/\\/g, '/');
      const found = legacyChangeHandling(fs.readFileSync(file, 'utf8'));
      if (found.hook) hooks.push(relative);
      if (found.initializationFlag) initializationFlags.push(relative);
    }
    expect(initializationFlags, 'Define columns before the row effect. See frontend/CLAUDE.md.').toEqual([]);
    expect(hooks.sort(), 'Use computed() for derived state and effect()/untracked() for input reactions.').toEqual(
      Object.keys(ON_CHANGES_REQUESTS).sort()
    );
    expect(Object.values(ON_CHANGES_REQUESTS).every((reason) => reason.length > 0)).toBe(true);
  });

  it('matches input decorators, including aliases and namespace imports', () => {
    for (const source of [
      'class Example { @Input() value: string; }',
      "import { Input as In } from '@angular/core'; class Example { @In('publicName') value: string; }",
      "import * as ng from '@angular/core'; class Example { @ng.Input() value: string; }"
    ]) {
      expect(hasAngularDecorator(source, ['Input']), source).toBe(true);
    }
    for (const source of [
      'class Example { readonly value = input<string>(); readonly selection = model<string>(); }',
      '// @Input() value',
      'const example = `@Input()`;'
    ]) {
      expect(hasAngularDecorator(source, ['Input']), source).toBe(false);
    }
  });

  it('finds no input decorators in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) => hasAngularDecorator(fs.readFileSync(file, 'utf8'), ['Input']))
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(violations, 'Declare inputs with input() or model(). See frontend/CLAUDE.md.').toEqual([]);
  });

  it('matches query decorators, including aliases and namespace imports', () => {
    const queries = ['ViewChild', 'ViewChildren', 'ContentChild', 'ContentChildren'];
    for (const query of queries) {
      for (const source of [
        `class Example { @${query}('child') child: unknown; }`,
        `import { ${query} as Query } from '@angular/core'; class Example { @Query('child') child: unknown; }`,
        `import * as ng from '@angular/core'; class Example { @ng.${query}('child') child: unknown; }`
      ]) {
        expect(hasAngularDecorator(source, queries), source).toBe(true);
      }
    }
    for (const source of [
      'class Example { readonly child = viewChild(Child); readonly children = contentChildren(Child); }',
      '// @ViewChild(Child)',
      'const example = `@ContentChildren(Child)`;'
    ]) {
      expect(hasAngularDecorator(source, queries), source).toBe(false);
    }
  });

  it('finds no query decorators in either host or the shared library', () => {
    const violations = collectSources(SRC)
      .filter((file) =>
        hasAngularDecorator(fs.readFileSync(file, 'utf8'), [
          'ViewChild',
          'ViewChildren',
          'ContentChild',
          'ContentChildren'
        ])
      )
      .map((file) => path.relative(SRC, file).replace(/\\/g, '/'));
    expect(
      violations,
      'Declare queries with viewChild/viewChildren or contentChild/contentChildren. See frontend/CLAUDE.md.'
    ).toEqual([]);
  });

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
