import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const SRC = path.dirname(fileURLToPath(import.meta.url));
const SIGNAL_FORM_DIR = path.join(SRC, 'app', 'lib', 'dynamic-form', 'signal');

/** Existing reactive form owners. Remove a file when converting it; new owners must use the signal form. */
const REACTIVE_FORM_FILES: readonly string[] = [
  'app/algo/component/algo-assetclass-add-instrument-dialog.component.ts',
  'app/algo/component/algo-assetclass-edit.component.ts',
  'app/algo/component/algo-create-from-portfolio-dynamic-dialog.component.ts',
  'app/algo/component/algo-create-from-watchlist-dynamic-dialog.component.ts',
  'app/algo/component/algo-rule-strategy-create-dynamic-dialog.component.ts',
  'app/algo/component/algo-security-edit.component.ts',
  'app/algo/component/algo-simulation-create-dynamic-dialog.component.ts',
  'app/algo/component/algo-simulation-run-start-dynamic-dialog.component.ts',
  'app/algo/component/algo-strategy-edit.component.ts',
  'app/correlation/component/correlation-add-instrument-dialog.component.ts',
  'app/correlation/component/correlation-chart.component.ts',
  'app/correlation/component/correlation-set-edit.component.ts',
  'app/correlation/component/correlation.component.ts',
  'app/dashboard/component/holding-movers-widget.component.ts',
  'app/dashboard/component/last-sessions-performance-widget.component.ts',
  'app/dashboard/component/performance-total-chart-widget.component.ts',
  'app/genericconnector/component/generic-connector-test-dialog.component.ts',
  'app/genericconnector/component/generic-connector.component.ts',
  'app/historyquote/component/historyquote-delete-dialog.component.ts',
  'app/historyquote/component/historyquote-edit.component.ts',
  'app/historyquote/component/historyquote-legacy-apply-split-dialog.component.ts',
  'app/historyquote/component/historyquote-legacy-edit.component.ts',
  'app/historyquote/component/historyquote-quality-fill-gaps-dialog.component.ts',
  'app/historyquote/component/indicator-edit.component.ts',
  'app/historyquote/component/seasonality.component.ts',
  'app/imptransaction/component/gtnet-import-head-select-dialog.component.ts',
  'app/imptransaction/component/securityaccount-import-set-cashaccount-dialog.component.ts',
  'app/imptransaction/component/securityaccount-import-transaction-edit-head.component.ts',
  'app/imptransaction/component/securityaccount.import.transaction.component.ts',
  'app/imptranstemplate/component/import-transaction-edit-platform.component.ts',
  'app/imptranstemplate/component/import-transaction-edit-template.component.ts',
  'app/imptranstemplate/component/import.transaction.template.component.ts',
  'app/imptranstemplate/component/template-form-check-dialog.component.ts',
  'app/imptranstemplate/component/transform-pdf-to-txt-dialog.component.ts',
  'app/lib/accesstoken/component/user-access-token-create-dialog.component.ts',
  'app/lib/connectorapikey/component/connector.api.key.edit.component.ts',
  'app/lib/dashboard/dashboard-config-dialog.component.ts',
  'app/lib/dynamicdialog/component/limit-transaction-request-dynamic-dialog.component.ts',
  'app/lib/dynamicdialog/component/logout-release-request-dynamic-dialog.component.ts',
  'app/lib/dynamicdialog/component/mail-send-dynamic-dialog.component.ts',
  'app/lib/entitylimit/component/entity.limit.edit.component.ts',
  'app/lib/globalsettings/global.settings-edit.component.ts',
  'app/lib/gnet/component/gtnet-config-edit.component.ts',
  'app/lib/gnet/component/gtnet-config-entity-edit.component.ts',
  'app/lib/gnet/component/gtnet-message-answer-edit.component.ts',
  'app/lib/gnet/component/gtnet-message-edit.component.ts',
  'app/lib/login/component/login.component.ts',
  'app/lib/login/component/nickname-lang-edit.component.ts',
  'app/lib/login/component/password-edit.component.ts',
  'app/lib/login/component/register.component.ts',
  'app/lib/manageclient/component/client-create-dynamic-dialog.component.ts',
  'app/lib/manageclient/component/share-read-access-dynamic-dialog.component.ts',
  'app/lib/taskdatamonitor/component/task-data-change-edit.component.ts',
  'app/lib/udfmeta/components/udf-general-edit.component.ts',
  'app/lib/user/component/user-change-owner-entities-dialog.component.ts',
  'app/lib/user/component/user-edit-component.ts',
  'app/performanceperiod/component/performance-report-dialog.component.ts',
  'app/performanceperiod/component/performance.period.component.ts',
  'app/portfolio/component/portfolio.edit.dynamic.component.ts',
  'app/securityaction/component/security-action-create-dialog.component.ts',
  'app/securityaction/component/security-transfer-create-dialog.component.ts',
  'app/securitycurrency/component/security-derived-edit.component.ts',
  'app/securitycurrency/component/security-udf-edit.component.ts',
  'app/securitycurrency/component/securitycurrency-search-and-set-dialog.component.ts',
  'app/shared/gtnet/component/gtnet-security-import-edit-head.component.ts',
  'app/shared/gtnet/component/gtnet-security-import.component.ts',
  'app/shared/securitycurrency/currencypair-edit.component.ts',
  'app/shared/securitycurrency/security-bond-terms.component.ts',
  'app/shared/securitycurrency/security-edit.component.ts',
  'app/standingorder/component/standing-order-cashaccount-edit.component.ts',
  'app/standingorder/component/standing-order-security-edit.component.ts',
  'app/stockexchange/component/trading-calendar-other-exchange-dynamic-dialog.component.ts',
  'app/stockexchange/component/trading-calendar-rule-set-edit.component.ts',
  'app/taxdata/component/tax-country-create-dialog.component.ts',
  'app/taxdata/component/tax-model-edit.component.ts',
  'app/taxdata/component/tax-statement-export-dialog.component.ts',
  'app/taxdata/component/tax-year-create-dialog.component.ts',
  'app/tenant/component/fee-model-comparison.component.ts',
  'app/tenant/component/tenant-dividends-chart.component.ts',
  'app/tenant/component/tenant-holdings-chart.component.ts',
  'app/tenant/component/tenant.edit.dynamic.component.ts',
  'app/tenant/component/tenant.edit.full.page.component.ts',
  'app/tenantaiprovider/component/tenant-ai-provider-edit.component.ts',
  'app/tradingplatform/component/fee-model-edit.component.ts',
  'app/tradingplatform/component/fx-markup-preview.component.ts',
  'app/tradingplatform/component/trading-platform-plan-edit.component.ts',
  'app/transaction/component/calc-ex-rate-dialog.component.ts',
  'app/transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component.ts',
  'app/transaction/component/transaction-cashaccount-editdouble.component.ts',
  'app/transaction/component/transaction-cashaccount-editsingle.component.ts',
  'app/transaction/component/transaction-export-csv-dialog.component.ts',
  'app/transaction/component/transaction-security-edit.component.ts',
  'app/udfmetasecurity/components/udf-metadata-security-edit.component.ts',
  'app/watchlist/component/watchlist-add-edit-price-problem-instrument.component.ts',
  'app/watchlist/component/watchlist-add-instrument-dialog.component.ts',
  'app/watchlist/component/watchlist-indicator-column-edit.component.ts',
  'app/watchlist/component/watchlist.edit.dynamic.component.ts',
  'grafiosch-host/grafiosch-tenant-edit.component.ts'
];

/** Edit-dialog bases whose type parameter selects the form component the dialog queries. */
const FORM_BASES = new Set([
  'SimpleEditBase',
  'SimpleFormEditBase',
  'SimpleEntityEditBase',
  'SimpleDynamicFormBase',
  'SimpleDynamicEditBase'
]);

/**
 * Reactive-forms members a signal form dialog must not use: they reach no control of a signal form, so a dialog
 * calling them silently does nothing. Dependencies are schema rules, display changes FIELD_DISPLAY rules.
 */
const REACTIVE_MEMBERS = new Set([
  'formControl',
  'valueChanges',
  'resetValidator',
  'enableAndVisibleInput',
  'disableAndHideInput',
  'setValidators',
  'addValidators',
  'removeValidators',
  'updateValueAndValidity',
  'enable',
  'disable'
]);

/** Reactive building blocks the signal form implementation itself must not depend on. */
const REACTIVE_IMPLEMENTATION = new Set([
  'FormNotifier',
  'ReactiveFormsModule',
  'UntypedFormGroup',
  'UntypedFormBuilder',
  'requireFormControl'
]);

/** Component templates of a source: inline `template` strings and the files of `templateUrl`. */
function componentTemplate(
  decorator: ts.Decorator,
  source: ts.SourceFile,
  readTemplate: (url: string) => string
): string | undefined {
  if (!ts.isCallExpression(decorator.expression) || decorator.expression.expression.getText(source) !== 'Component') {
    return undefined;
  }
  const metadata = decorator.expression.arguments[0];
  if (!metadata || !ts.isObjectLiteralExpression(metadata)) return undefined;
  for (const property of metadata.properties) {
    if (!ts.isPropertyAssignment(property) || !ts.isStringLiteralLike(property.initializer)) continue;
    const name = property.name.getText(source);
    if (name === 'template') return property.initializer.text;
    if (name === 'templateUrl') return readTemplate(property.initializer.text);
  }
  return undefined;
}

/** Finds actual reactive form templates, excluding comments, test fixtures and similarly named layout elements. */
function rendersReactiveForm(source: string, readTemplate: (url: string) => string = () => ''): boolean {
  const file = ts.createSourceFile('component.ts', source, ts.ScriptTarget.Latest, true);
  let found = false;
  const visit = (node: ts.Node): void => {
    if (ts.isClassDeclaration(node)) {
      found ||= (ts.getDecorators(node) ?? []).some((decorator) => {
        const template = componentTemplate(decorator, file, readTemplate);
        return template !== undefined && /<dynamic-form[\s/>]/.test(template.replace(/<!--[\s\S]*?-->/g, ''));
      });
    }
    ts.forEachChild(node, visit);
  };
  visit(file);
  return found;
}

/**
 * Checks the components of one source file against the signal form contract:
 * - a dialog rendering `<signal-dynamic-form>` passes SignalDynamicFormComponent as the form type of its edit base,
 *   and one rendering `<dynamic-form>` does not, otherwise the base class queries the wrong component;
 * - a dialog on the signal form uses no reactive-forms members and prepares its fields without FormNotifier
 *   instrumentation (`prepareFieldsAndErrors(…, false)`).
 *
 * @param source - TypeScript source of the file
 * @param readTemplate - Reads an external template by its `templateUrl`
 * @returns One message per violation, empty when the file complies
 */
function signalFormViolations(source: string, readTemplate: (url: string) => string = () => ''): string[] {
  const file = ts.createSourceFile('component.ts', source, ts.ScriptTarget.Latest, true);
  const violations: string[] = [];
  const visitClass = (node: ts.ClassDeclaration): void => {
    const name = node.name?.text ?? '(anonymous)';
    const template = (ts.getDecorators(node) ?? [])
      .map((decorator) => componentTemplate(decorator, file, readTemplate))
      .find((text) => text !== undefined);
    if (template === undefined) return;
    const signalForm = /<signal-dynamic-form[\s/>]/.test(template);
    const reactiveForm = /<dynamic-form[\s/>]/.test(template);
    const formBase = (node.heritageClauses ?? [])
      .filter((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)
      .flatMap((clause) => [...clause.types])
      .find((type) => FORM_BASES.has(type.expression.getText(file)));
    const signalFormType = !!formBase?.typeArguments?.some((type) =>
      /\bSignalDynamicFormComponent\b/.test(type.getText(file))
    );
    if (signalForm && formBase && !signalFormType) {
      violations.push(
        `${name}: renders <signal-dynamic-form> but its ${formBase.expression.getText(file)} queries the reactive form`
      );
    }
    if (reactiveForm && signalFormType) {
      violations.push(`${name}: renders <dynamic-form> but declares SignalDynamicFormComponent as its form type`);
    }
    if (!signalForm) return;
    const visit = (child: ts.Node): void => {
      if (ts.isPropertyAccessExpression(child) && REACTIVE_MEMBERS.has(child.name.text)) {
        violations.push(`${name}: uses the reactive-forms member '${child.name.text}'`);
      }
      if (ts.isIdentifier(child) && child.text === 'requireFormControl') {
        violations.push(`${name}: uses requireFormControl`);
      }
      if (
        ts.isCallExpression(child) &&
        ts.isPropertyAccessExpression(child.expression) &&
        child.expression.name.text === 'prepareFieldsAndErrors' &&
        child.arguments[2]?.getText(file) !== 'false'
      ) {
        violations.push(`${name}: prepareFieldsAndErrors without 'false' instruments the fields with FormNotifier`);
      }
      ts.forEachChild(child, visit);
    };
    node.members.forEach(visit);
  };
  const walk = (node: ts.Node): void => {
    if (ts.isClassDeclaration(node)) visitClass(node);
    ts.forEachChild(node, walk);
  };
  walk(file);
  return violations;
}

/** Identifiers of reactive building blocks in a source, outside comments and strings. */
function reactiveImplementationUses(source: string): string[] {
  const file = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  const found = new Set<string>();
  const visit = (node: ts.Node): void => {
    if (ts.isIdentifier(node) && REACTIVE_IMPLEMENTATION.has(node.text)) found.add(node.text);
    ts.forEachChild(node, visit);
  };
  visit(file);
  return [...found];
}

function sources(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, out);
    else if (entry.name.endsWith('.ts') && !entry.name.endsWith('.spec.ts')) out.push(full);
  }
  return out;
}

const SIGNAL_DIALOG = `
@Component({ template: '<p-dialog><signal-dynamic-form [config]="config" /></p-dialog>' })
export class GoodDialog extends SimpleFormEditBase<SignalDynamicFormComponent<Model>> {
  ngOnInit(): void {
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }
}`;

describe('signal form ratchet', () => {
  it('recognizes inline and external reactive forms without matching comments or layout components', () => {
    expect(rendersReactiveForm("@Component({ template: '<dynamic-form />' }) class Dialog {}")).toBe(true);
    expect(
      rendersReactiveForm("@Component({ templateUrl: './form.html' }) class Dialog {}", () => '<dynamic-form\n />')
    ).toBe(true);
    expect(
      rendersReactiveForm(
        "// <dynamic-form />\n@Component({ template: '<!-- <dynamic-form /> --><dynamic-form-layout />' }) class Dialog {}"
      )
    ).toBe(false);
    expect(rendersReactiveForm(SIGNAL_DIALOG)).toBe(false);
  });

  it('allows only the remaining reactive form owners and requires converted files to leave the inventory', () => {
    const reactiveFiles = sources(SRC)
      .filter((file) =>
        rendersReactiveForm(fs.readFileSync(file, 'utf8'), (url) =>
          fs.readFileSync(path.resolve(path.dirname(file), url), 'utf8')
        )
      )
      .map((file) => path.relative(SRC, file).replaceAll(path.sep, '/'))
      .sort();
    expect(reactiveFiles).toEqual([...REACTIVE_FORM_FILES].sort());
  });

  it('accepts a signal form dialog with the signal form type and without reactive members', () => {
    expect(signalFormViolations(SIGNAL_DIALOG)).toEqual([]);
  });

  it('rejects a mismatched form type, reactive members and FormNotifier instrumentation', () => {
    const missingType = SIGNAL_DIALOG.replace('<SignalDynamicFormComponent<Model>>', '');
    expect(signalFormViolations(missingType)).toEqual([
      'GoodDialog: renders <signal-dynamic-form> but its SimpleFormEditBase queries the reactive form'
    ]);
    const reactiveTemplate = SIGNAL_DIALOG.replace('<signal-dynamic-form', '<dynamic-form');
    expect(signalFormViolations(reactiveTemplate)).toEqual([
      'GoodDialog: renders <dynamic-form> but declares SignalDynamicFormComponent as its form type'
    ]);
    const reactiveMembers = SIGNAL_DIALOG.replace(
      '  ngOnInit(): void {',
      `  initialize(): void {
    this.configObject.name.formControl.valueChanges.subscribe();
    requireFormControl(this.configObject.currency).disable();
  }
  ngOnInit(): void {`
    ).replace(', this.config, false)', ', this.config)');
    expect(signalFormViolations(reactiveMembers)).toEqual([
      "GoodDialog: uses the reactive-forms member 'valueChanges'",
      "GoodDialog: uses the reactive-forms member 'formControl'",
      "GoodDialog: uses the reactive-forms member 'disable'",
      'GoodDialog: uses requireFormControl',
      "GoodDialog: prepareFieldsAndErrors without 'false' instruments the fields with FormNotifier"
    ]);
  });

  it('keeps reactive dialogs and mentions in comments or strings out of the check', () => {
    const reactiveDialog = `
@Component({ template: '<dynamic-form [config]="config" />' })
export class ReactiveDialog extends SimpleFormEditBase {
  init(): void { requireFormControl(this.configObject.x).disable(); }
}`;
    expect(signalFormViolations(reactiveDialog)).toEqual([]);
    expect(reactiveImplementationUses("// FormNotifier is not used\nconst text = 'UntypedFormGroup';")).toEqual([]);
    expect(reactiveImplementationUses('const notifier = new FormNotifier();')).toEqual(['FormNotifier']);
  });

  it('holds for every component of both hosts and the library', () => {
    const violations = sources(SRC).flatMap((file) =>
      signalFormViolations(fs.readFileSync(file, 'utf8'), (url) =>
        fs.readFileSync(path.resolve(path.dirname(file), url), 'utf8')
      ).map((violation) => `${path.relative(SRC, file)}: ${violation}`)
    );
    expect(violations).toEqual([]);
  });

  it('builds the signal form without FormNotifier and without reactive form groups', () => {
    const uses = sources(SIGNAL_FORM_DIR).flatMap((file) =>
      reactiveImplementationUses(fs.readFileSync(file, 'utf8')).map((name) => `${path.relative(SRC, file)}: ${name}`)
    );
    expect(uses).toEqual([]);
  });
});
