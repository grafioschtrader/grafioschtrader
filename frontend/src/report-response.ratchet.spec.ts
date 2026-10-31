import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const RESPONSE_CONTRACTS: Record<string, string[]> = {
  'app/algo/model/algo.security.ts': ['AlgoSecurityStrategyImplType'],
  'app/entities/view/globalparameters.client.ts': ['GlobalparametersClient'],
  'app/lib/proposechange/model/propose.change.entity.whit.entity.ts': ['ProposeChangeEntityWithEntity'],
  'app/lib/udfmeta/model/udf.metadata.ts': ['UDFSpecialTypeDisableUser', 'UDFSpecialTypeDisableUserId'],
  'app/algo/model/input.and.show.definition.strategy.ts': ['InputAndShowDefinitionStrategy'],
  'app/entities/import.transaction.pos.failed.ts': ['ImportTransactionPosFailed'],
  'app/entities/import.transaction.pos.ts': ['ImportTransactionPos', 'PersistedImportTransactionPos'],
  'app/securityaccount/component/combine.template.and.imp.trans.pos.ts': ['CombineTemplateAndImpTransPos'],
  'app/securitycurrency/model/historyquote.quality.group.ts': [
    'HistoryquoteQualityIds',
    'HistoryquoteQualityGroup',
    'HistoryquoteQualityHead'
  ],
  'app/securitycurrency/model/security.currencypair.derived.links.ts': ['SecurityCurrencypairDerivedLinks'],
  'app/entities/trading.days.minus.ts': ['TradingDaysMinus'],
  'app/entities/trading.days.plus.ts': ['TradingDaysPlus'],
  'app/entities/trading.period.transaction.summary.ts': ['TradingPeriodTransactionSummary'],
  'app/entities/view/securitycurrency.position.ts': ['SecuritycurrencyPosition'],
  'app/entities/view/securitydividends/security.dividends.grand.total.ts': ['SecurityDividendsGrandTotal'],
  'app/entities/view/securitydividends/security.dividends.year.group.ts': ['SecurityDividendsYearGroup'],
  'app/entities/view/transactioncost/transaction.cost.grand.summary.ts': ['TransactionCostGrandSummary'],
  'app/entities/view/transactioncost/transaction.cost.group.summary.ts': ['TransactionCostGroupSummary'],
  'app/entities/view/transactioncost/transaction.cost.position.ts': ['TransactionCostPosition'],
  'app/shared/gtnet/model/gtnet-security-imp-gap.ts': ['GTNetSecurityImpGap'],
  'app/shared/types/tenant.limit.ts': ['TenantLimit'],
  'app/shared/securitycurrency/ifeed.connector.ts': ['Description'],
  'app/lib/types/string.response.ts': ['StringResponse'],
  'app/lib/taskdatamonitor/types/task.data.change.ts': ['TaskDataChangeFormConstraints'],
  'app/entities/view/currencypair.with.transaction.ts': ['CurrencypairTransactionData'],
  'app/imptranstemplate/component/form.template.check.ts': ['FormTemplateCheckResult'],
  'app/performanceperiod/model/trading-days.ts': ['FirstAndMissingTradingDays']
};

/** Selected JSON contracts may share a file with enums and constructed entities. */
function namedResponseViolations(source: string, names: readonly string[]): string[] {
  const parsed = ts.createSourceFile('response.ts', source, ts.ScriptTarget.Latest, true);
  return names.flatMap((name) => {
    const declaration = parsed.statements.find(
      (statement) =>
        (ts.isInterfaceDeclaration(statement) || ts.isClassDeclaration(statement)) && statement.name?.text === name
    );
    if (!declaration || !ts.isInterfaceDeclaration(declaration)) return [`${name}: expected a JSON interface`];
    return declaration.members
      .filter((member) => !ts.isPropertySignature(member) && !ts.isIndexSignatureDeclaration(member))
      .map((member) => `${name}.${member.name?.getText(parsed)}`);
  });
}

/** JSON report shapes carry properties; their files introduce no constructors, defaults or executable imports. */
function runtimeDeclarations(source: string): string[] {
  const parsed = ts.createSourceFile('report.ts', source, ts.ScriptTarget.Latest, true);
  const violations: string[] = [];
  for (const statement of parsed.statements) {
    if (ts.isInterfaceDeclaration(statement)) {
      for (const member of statement.members) {
        if (!ts.isPropertySignature(member)) violations.push(`${statement.name.text}.${member.name?.getText(parsed)}`);
      }
    } else if (ts.isImportDeclaration(statement) && statement.importClause?.isTypeOnly) {
      continue;
    } else if (ts.isTypeAliasDeclaration(statement) || (ts.isExportDeclaration(statement) && statement.isTypeOnly)) {
      continue;
    } else {
      violations.push(statement.getText(parsed));
    }
  }
  return violations;
}

describe('report response model ratchet', () => {
  it('distinguishes JSON contracts from runtime neighbours and supports indicator dictionaries', () => {
    expect(
      namedResponseViolations(
        `
      class Entity { getId() { return 1; } }
      enum Kind { FIRST }
      interface Response { [indicator: \`ind_\${number}\`]: number | null; rows: Entity[]; kind: Kind; }
    `,
        ['Response']
      )
    ).toEqual([]);
    expect(namedResponseViolations('class Response { value!: number; }', ['Response'])).toEqual([
      'Response: expected a JSON interface'
    ]);
    expect(namedResponseViolations('', ['Response'])).toEqual(['Response: expected a JSON interface']);
    expect(namedResponseViolations('interface Response { load(): void; }', ['Response'])).toEqual(['Response.load']);
  });

  it('accepts property contracts and rejects runtime classes, methods and value imports', () => {
    expect(
      runtimeDeclarations(`
      import type { Position } from './position';
      export type Amount = number | null;
      export interface Group<T extends Position> { positions: T[]; amount: Amount; disposalCost?: number; }
      export interface Total<T extends Position> extends Group<T> { currency: string; }
      export type { Position };
    `)
    ).toEqual([]);
    expect(runtimeDeclarations('export class Result { amount!: number; }')).toHaveLength(1);
    expect(runtimeDeclarations('export class Result { amount = 0; }')).toHaveLength(1);
    expect(runtimeDeclarations('export class Result { constructor(public amount: number) {} }')).toHaveLength(1);
    expect(runtimeDeclarations('import { Position } from "./position";')).toHaveLength(1);
    expect(runtimeDeclarations('import "./initialize";')).toHaveLength(1);
    expect(runtimeDeclarations('export interface Result { calculate(): number; }')).toEqual(['Result.calculate']);
  });

  it('keeps cash-account and security-position responses as data-only contracts', () => {
    const directory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'app/entities/view');
    const files = readdirSync(directory).filter((file) => /^(account|security)\.position\..*\.ts$/.test(file));
    expect(files.length).toBeGreaterThan(0);
    const violations = files.flatMap((file) =>
      runtimeDeclarations(readFileSync(path.join(directory, file), 'utf8')).map((violation) => `${file}: ${violation}`)
    );
    expect(violations).toEqual([]);
  });
  it('keeps the registered transport models as JSON contracts', () => {
    const src = path.dirname(fileURLToPath(import.meta.url));
    const violations = Object.entries(RESPONSE_CONTRACTS).flatMap(([file, names]) =>
      namedResponseViolations(readFileSync(path.join(src, file), 'utf8'), names).map(
        (violation) => file + ': ' + violation
      )
    );
    expect(violations).toEqual([]);
  });
});
