import { requireInitialized } from '../../lib/helper/required-state';
import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, afterNextRender, effect, inject, Injector, signal, viewChild, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { forkJoin, Subscription } from 'rxjs';
import { SimpleEditBase } from '../../lib/edit/simple.edit.base';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Helper } from '../../lib/helper/helper';
import { BaseSettings } from '../../lib/base.settings';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TradingPlatformPlan } from '../../entities/tradingplatformplan';
import { TradingPlatformPlanService } from '../service/trading.platform.plan.service';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { StockexchangeService } from '../../stockexchange/service/stockexchange.service';
import { Stockexchange } from '../../entities/stockexchange';
import { Securityaccount } from '../../entities/securityaccount';
import {
  TransactionCostEstimateRequest,
  TransactionCostEstimateResult
} from '../../entities/transaction.cost.estimate';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { FieldsetModule } from '@openng/optimus-ui/fieldset';
import { ButtonModule } from '@openng/optimus-ui/button';
import { YamlEditorComponent, YamlFieldCompletion } from '../../algo/component/yaml-editor.component';
import { HttpClient } from '@angular/common/http';
import { AppSettings } from '../../shared/app.settings';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { FxMarkupPreviewComponent } from './fx-markup-preview.component';

/**
 * Dialog component for editing the fee model YAML and testing fee estimations.
 * Supports two modes:
 * - p-dialog mode: opened from TradingPlatformPlan table, uses @Input() callParam
 * - dynamic dialog mode: opened from security account tree context menu, uses DynamicDialogConfig
 */
@Component({
  selector: 'fee-model-edit',
  template: `
    @if (!isDynamic) {
      <p-dialog
        styleClass="big-dialog"
        header="{{ 'FEE_MODEL_YAML' | translate }}"
        [visible]="visibleDialog()"
        [style]="{ width: '900px' }"
        (onShow)="onShow($event)"
        (onHide)="onHide($event)"
        [modal]="true">
        <ng-container *ngTemplateOutlet="feeModelContent" />
      </p-dialog>
    } @else {
      <div style="max-height: calc(100vh - 140px); overflow-y: auto;">
        <ng-container *ngTemplateOutlet="feeModelContent" />
      </div>
    }

    <ng-template #feeModelContent>
      <p>{{ 'CUSTODY_EDITOR_HELP' | translate }}</p>
      <yaml-editor
        #yamlEditor
        [format]="isDynamic ? 'FEES_ACCOUNT' : 'FEES'"
        [height]="'400px'"
        [value]="feeModelYamlValue()"
        (valueChange)="feeModelYamlValue.set($event)"
        [schema]="feeModelSchema()"
        [fieldCompletions]="evalExCompletions" />

      <div class="flex justify-end mt-3">
        <p-button
          [label]="'SAVE' | translate"
          [disabled]="yamlEditor.validating || !yamlEditor.syntaxValid"
          (click)="save()">
          <i class="pi pi-check" pButtonIcon></i>
        </p-button>
      </div>

      <p-fieldset [legend]="'TEST_FEE_ESTIMATION' | translate" [toggleable]="true" [collapsed]="true" styleClass="mt-3">
        <dynamic-form
          [config]="config"
          [formConfig]="formConfig"
          [translateService]="translateService"
          #form="dynamicForm" />

        @if (testResult(); as result) {
          @if (result.estimatedCost != null) {
            <div class="text-green-600 mt-2">
              <strong>{{ 'ESTIMATED_COST' | translate }}:</strong>
              {{ result.estimatedCost }}
              @if (result.matchedRuleName) {
                <span> &mdash; {{ 'MATCHED_RULE' | translate }}: {{ result.matchedRuleName }}</span>
              }
            </div>
          }
          @if (result.error) {
            <div class="text-red-600 mt-2"><strong>Error:</strong> {{ result.error }}</div>
          }
        }
      </p-fieldset>
      <p-fieldset [legend]="'FX_CONVERSION' | translate" [toggleable]="true" [collapsed]="true" styleClass="mt-3">
        <fx-markup-preview
          [yaml]="feeModelYamlValue()"
          [idSecurityaccount]="isDynamic ? (securityaccount.idSecuritycashAccount ?? undefined) : undefined"
          [idTradingPlatformPlan]="callParam()?.idTradingPlatformPlan" />
      </p-fieldset>
    </ng-template>
  `,
  imports: [
    DialogModule,
    ...DYNAMIC_FORM_IMPORTS,
    TranslateModule,
    FieldsetModule,
    ButtonModule,
    YamlEditorComponent,
    FxMarkupPreviewComponent,
    NgTemplateOutlet
  ]
})
export class FeeModelEditComponent extends SimpleEditBase implements OnInit {
  private tradingPlatformPlanService: TradingPlatformPlanService = inject(TradingPlatformPlanService);
  private securityaccountService: SecurityaccountService = inject(SecurityaccountService);
  private stockexchangeService: StockexchangeService = inject(StockexchangeService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);
  private httpClient: HttpClient = inject(HttpClient);
  private messageToastService: MessageToastService = inject(MessageToastService);
  public translateService: TranslateService = inject(TranslateService);
  private dynamicDialogRef = inject(DynamicDialogRef, { optional: true });
  private dynamicDialogConfig = inject(DynamicDialogConfig, { optional: true });

  readonly yamlEditor = viewChild(YamlEditorComponent);
  static readonly DIALOG_WIDTH = 900;

  readonly callParam = input<TradingPlatformPlan>();

  isDynamic = false;
  readonly feeModelYamlValue = signal('');
  readonly feeModelSchema = signal<any>(null);
  evalExCompletions: { [fieldName: string]: YamlFieldCompletion[] };
  readonly testResult = signal<TransactionCostEstimateResult | null>(null);

  private readonly injector = inject(Injector);
  private estimateSubscription?: Subscription;
  private estimateRevision = 0;

  private initializedSecurityaccount?: Securityaccount;

  get securityaccount(): Securityaccount {
    return requireInitialized(this.initializedSecurityaccount, 'FeeModelEditComponent.securityaccount');
  }

  set securityaccount(value: Securityaccount) {
    this.initializedSecurityaccount = value;
  }

  constructor() {
    super(HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN);
    this.isDynamic = !!this.dynamicDialogRef;
    this.loadFeeModelSchema();
    this.evalExCompletions = this.buildEvalExCompletions();
    effect(() => {
      this.feeModelYamlValue();
      this.estimateRevision++;
      this.estimateSubscription?.unsubscribe();
      this.testResult.set(null);
    });
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputNumberHeqF('tradeValue', false, 10, 2, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('units', false, 10, 4, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('mic', false),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', false),
      DynamicFieldHelper.createFieldInputNumberHeqF('fixedAssets', false, 12, 2, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('portfolioTotal', false, 12, 2, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('tenantTotal', false, 12, 2, false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('specInvestInstrument', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('categoryType', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('tradeDirection', false),
      DynamicFieldHelper.createFieldSelectStringHeqF('settlementCurrency', false),
      DynamicFieldHelper.createFieldInputNumberHeqF('tradesInMonth', false, 4, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('tradesInQuarter', false, 4, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('tradesInYear', false, 5, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('securityTradesInMonth', false, 4, 0, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'transactionDate', false),
      DynamicFieldHelper.createFunctionButton('TEST_FEE_ESTIMATION', (e) => this.testEstimation(e))
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.isDynamic) {
      if (!this.dynamicDialogConfig) throw new Error('FeeModelEditComponent requires dialog configuration');
      this.securityaccount = this.dynamicDialogConfig.data.callParam.thisObject;
      afterNextRender(() => this.initialize(), { injector: this.injector });
    }
  }

  protected override initialize(): void {
    if (this.isDynamic) {
      this.feeModelYamlValue.set(this.initializedSecurityaccount?.feeModelYaml || '');
    } else {
      this.feeModelYamlValue.set(this.callParam()?.feeModelYaml || '');
    }
    this.testResult.set(null);
    this.form()?.setDefaultValuesAndEnableSubmit();
    const enumKeys = (e: any): string[] => Object.keys(e).filter((key) => typeof e[key] === 'number');
    const instrumentKeys = enumKeys(SpecialInvestmentInstruments);
    const categoryKeys = enumKeys(AssetclassType);
    forkJoin([
      this.stockexchangeService.getAllStockexchanges(false),
      this.gpsGT.getCurrencies(),
      this.translateService.get([...instrumentKeys, ...categoryKeys, 'BUY', 'SELL'])
    ])
      .pipe(this.untilDialogClosed())
      .subscribe(([stockexchanges, currencies, texts]) => {
        this.configObject.mic.valueKeyHtmlOptions = this.createMicOptions(stockexchanges);
        this.configObject.currency.valueKeyHtmlOptions = [new ValueKeyHtmlSelectOptions('', '')].concat(currencies);
        this.configObject.settlementCurrency.valueKeyHtmlOptions = [new ValueKeyHtmlSelectOptions('', '')].concat(
          currencies
        );
        this.configObject.specInvestInstrument.valueKeyHtmlOptions = this.createOrdinalEnumOptions(
          SpecialInvestmentInstruments,
          instrumentKeys,
          texts
        );
        this.configObject.categoryType.valueKeyHtmlOptions = this.createOrdinalEnumOptions(
          AssetclassType,
          categoryKeys,
          texts
        );
        this.configObject.tradeDirection.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', ''),
          new ValueKeyHtmlSelectOptions(0, texts['BUY']),
          new ValueKeyHtmlSelectOptions(1, texts['SELL'])
        ];
      });
  }

  async save(): Promise<void> {
    const untilClosed = this.untilDialogClosed<Securityaccount | TradingPlatformPlan>();
    const editor = this.yamlEditor();
    if (!editor || !(await editor.validateForSubmit())) return;
    if (this.isDynamic) {
      const sa = new Securityaccount();
      Object.assign(sa, this.securityaccount);
      sa.feeModelYaml = this.feeModelYamlValue().trim() || null;
      this.securityaccountService
        .update(sa)
        .pipe(untilClosed)
        .subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
              i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.SECURITYACCOUNT)
            });
            this.dynamicDialogRef?.close(new ProcessedActionData(ProcessedAction.UPDATED));
          }
        });
    } else {
      const tradingPlatformPlan = new TradingPlatformPlan();
      Object.assign(tradingPlatformPlan, this.callParam());
      tradingPlatformPlan.feeModelYaml = this.feeModelYamlValue().trim() || null;
      this.tradingPlatformPlanService
        .update(tradingPlatformPlan)
        .pipe(untilClosed)
        .subscribe({
          next: () => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
              i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_PLATFORM_PLAN)
            });
            this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
          }
        });
    }
  }

  private createMicOptions(stockexchanges: Stockexchange[]): ValueKeyHtmlSelectOptions[] {
    const options = stockexchanges
      .filter((se): se is Stockexchange & { mic: string } => !!se.mic)
      .map((se) => new ValueKeyHtmlSelectOptions(se.mic, se.name + ' - ' + se.mic));
    return [new ValueKeyHtmlSelectOptions('', ''), ...options];
  }

  /**
   * Creates select options from a numeric enum using ordinal values as keys.
   * Unlike SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty which uses enum name strings as keys,
   * this method uses numeric ordinals — required when the backend expects Integer fields.
   */
  private createOrdinalEnumOptions(
    e: any,
    keys: string[],
    translations: { [key: string]: string }
  ): ValueKeyHtmlSelectOptions[] {
    return [
      new ValueKeyHtmlSelectOptions('', ''),
      ...keys.map((name) => new ValueKeyHtmlSelectOptions(e[name], translations[name] || name))
    ];
  }

  /**
   * Tests fee estimation. In dynamic mode (securityaccount), uses inline YAML evaluation.
   * Both modes evaluate unsaved YAML without persisting the plan.
   */
  async testEstimation(event: any): Promise<void> {
    const revision = ++this.estimateRevision;
    this.estimateSubscription?.unsubscribe();
    const untilClosed = this.untilDialogClosed<TransactionCostEstimateResult>();
    const editor = this.yamlEditor();
    if (!editor || !(await editor.validateForSubmit())) return;
    if (revision !== this.estimateRevision) return;
    this.testResult.set(null);

    const request: TransactionCostEstimateRequest = {
      idSecurityaccount: this.initializedSecurityaccount?.idSecuritycashAccount,
      idTradingPlatformPlan: this.isDynamic
        ? this.initializedSecurityaccount?.tradingPlatformPlan?.idTradingPlatformPlan
        : this.callParam()?.idTradingPlatformPlan,
      tradeValue: requireFormControl(this.configObject.tradeValue).value || 0,
      units: requireFormControl(this.configObject.units).value || 0,
      specInvestInstrument: requireFormControl(this.configObject.specInvestInstrument).value,
      categoryType: requireFormControl(this.configObject.categoryType).value,
      mic: requireFormControl(this.configObject.mic).value || null,
      currency: requireFormControl(this.configObject.currency).value || null,
      fixedAssets: requireFormControl(this.configObject.fixedAssets).value,
      portfolioTotal: requireFormControl(this.configObject.portfolioTotal).value,
      tenantTotal: requireFormControl(this.configObject.tenantTotal).value,
      tradeDirection: requireFormControl(this.configObject.tradeDirection).value,
      settlementCurrency: requireFormControl(this.configObject.settlementCurrency).value || null,
      tradesInMonth: requireFormControl(this.configObject.tradesInMonth).value,
      tradesInQuarter: requireFormControl(this.configObject.tradesInQuarter).value,
      tradesInYear: requireFormControl(this.configObject.tradesInYear).value,
      securityTradesInMonth: requireFormControl(this.configObject.securityTradesInMonth).value,
      transactionDate: Helper.formatDateStringAsString(
        this.configObject.transactionDate,
        BaseSettings.FORMAT_DATE_SHORT_NATIVE
      )
    };

    request.yaml = this.feeModelYamlValue().trim() || null;
    const estimate = this.isDynamic
      ? this.securityaccountService.estimateCostFromYaml(request)
      : this.tradingPlatformPlanService.estimateTransactionCost(request);
    this.estimateSubscription = estimate.pipe(untilClosed).subscribe({
      next: (result) => this.testResult.set(result),
      error: () => this.testResult.set({ error: 'Request failed' })
    });
  }

  submit(value: { [name: string]: any }): void {
    this.save();
  }

  private loadFeeModelSchema(): void {
    this.httpClient
      .get('assets/schemas/fee-model-schema.json')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (schema: any) => this.feeModelSchema.set(schema),
        error: () => console.warn('Failed to load fee model schema')
      });
  }

  /**
   * Builds EvalEx autocompletion items for the condition and expression YAML fields.
   * Variables and functions match what TransactionCostEvalExEstimator registers on the backend.
   */
  private buildEvalExCompletions(): {
    [fieldName: string]: YamlFieldCompletion[];
  } {
    const variables: YamlFieldCompletion[] = [
      {
        label: 'tradeValue',
        insertText: 'tradeValue',
        detail: 'variable (numeric)',
        documentation: 'Total trade amount (price × units)'
      },
      {
        label: 'units',
        insertText: 'units',
        detail: 'variable (numeric)',
        documentation: 'Number of shares/units traded'
      },
      {
        label: 'instrument',
        insertText: 'instrument',
        detail: 'variable (string)',
        documentation:
          'Investment instrument name: DIRECT_INVESTMENT, ETF, MUTUAL_FUND, PENSION_FUNDS, CFD, FOREX, ISSUER_RISK_PRODUCT, NON_INVESTABLE_INDICES'
      },
      {
        label: 'assetclass',
        insertText: 'assetclass',
        detail: 'variable (string)',
        documentation:
          'Asset class name: EQUITIES, FIXED_INCOME, MONEY_MARKET, COMMODITIES, REAL_ESTATE, MULTI_ASSET, CONVERTIBLE_BOND, CREDIT_DERIVATIVE, CURRENCY_PAIR'
      },
      {
        label: 'mic',
        insertText: 'mic',
        detail: 'variable (string)',
        documentation: 'Market Identifier Code (e.g., XSWX, XNYS)'
      },
      {
        label: 'currency',
        insertText: 'currency',
        detail: 'variable (string)',
        documentation: 'Trade currency ISO code (e.g., CHF, USD)'
      },
      {
        label: 'fixedAssets',
        insertText: 'fixedAssets',
        detail: 'variable (numeric)',
        documentation: 'Total portfolio/account value for tier determination'
      },
      {
        label: 'portfolioTotal',
        insertText: 'portfolioTotal',
        detail: 'variable (numeric)',
        documentation:
          'Total value (securities, cash balance, closed margin results) of the portfolio of the security account on the last day before the trade, in the portfolio currency'
      },
      {
        label: 'tenantTotal',
        insertText: 'tenantTotal',
        detail: 'variable (numeric)',
        documentation:
          'Total value of all portfolios of the tenant on the last day before the trade, in the tenant currency'
      },
      {
        label: 'tradeDirection',
        insertText: 'tradeDirection',
        detail: 'variable (numeric)',
        documentation: '0 = buy, 1 = sell'
      },
      {
        label: 'specInvestInstrument',
        insertText: 'specInvestInstrument',
        detail: 'variable (numeric)',
        documentation: 'Legacy numeric alias for instrument type ordinal'
      },
      {
        label: 'categoryType',
        insertText: 'categoryType',
        detail: 'variable (numeric)',
        documentation: 'Legacy numeric alias for asset class type ordinal'
      }
    ];

    const functions: YamlFieldCompletion[] = [
      {
        label: 'MAX',
        insertText: 'MAX($1, $2)$0',
        detail: 'function',
        documentation: 'Returns the greater of two values',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'settlementCurrency',
        insertText: 'settlementCurrency',
        detail: 'variable (string)',
        documentation: 'ISO code of the cash account the trade settles in, for currency conversion mark-ups'
      },
      {
        label: 'tradesInMonth',
        insertText: 'tradesInMonth',
        detail: 'variable (numeric)',
        documentation: 'Earlier trades of the security account in the calendar month'
      },
      {
        label: 'tradesInQuarter',
        insertText: 'tradesInQuarter',
        detail: 'variable (numeric)',
        documentation: 'Earlier trades of the security account in the calendar quarter, e.g. one free trade per quarter'
      },
      {
        label: 'tradesInYear',
        insertText: 'tradesInYear',
        detail: 'variable (numeric)',
        documentation: 'Earlier trades of the security account in the calendar year'
      },
      {
        label: 'securityTradesInMonth',
        insertText: 'securityTradesInMonth',
        detail: 'variable (numeric)',
        documentation: 'Earlier trades of this security in the calendar month, e.g. one free order per ETF and month'
      },
      {
        label: 'exchangeCount',
        insertText: 'exchangeCount',
        detail: 'custody variable (numeric)',
        documentation: 'Distinct exchanges held or traded in the billing period, filtered by exchangeCondition'
      },
      {
        label: 'positionCount',
        insertText: 'positionCount',
        detail: 'custody variable (numeric)',
        documentation: 'Number of observed positions in the custody amount expression'
      },
      {
        label: 'MIN',
        insertText: 'MIN($1, $2)$0',
        detail: 'function',
        documentation: 'Returns the lesser of two values',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'ABS',
        insertText: 'ABS($1)$0',
        detail: 'function',
        documentation: 'Returns the absolute value',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'ROUND',
        insertText: 'ROUND($1, $2)$0',
        detail: 'function',
        documentation: 'Rounds to specified decimal places',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'CEILING',
        insertText: 'CEILING($1)$0',
        detail: 'function',
        documentation: 'Rounds up to the nearest integer',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'FLOOR',
        insertText: 'FLOOR($1)$0',
        detail: 'function',
        documentation: 'Rounds down to the nearest integer',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'IF',
        insertText: 'IF($1, $2, $3)$0',
        detail: 'function',
        documentation: 'IF(condition, trueValue, falseValue)',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'NOT',
        insertText: 'NOT($1)$0',
        detail: 'function',
        documentation: 'Logical negation',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'AND',
        insertText: 'AND($1, $2)$0',
        detail: 'function',
        documentation: 'Logical AND of two conditions',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'OR',
        insertText: 'OR($1, $2)$0',
        detail: 'function',
        documentation: 'Logical OR of two conditions',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'SQRT',
        insertText: 'SQRT($1)$0',
        detail: 'function',
        documentation: 'Square root',
        kind: 'Function',
        isSnippet: true
      },
      {
        label: 'LOG',
        insertText: 'LOG($1)$0',
        detail: 'function',
        documentation: 'Natural logarithm',
        kind: 'Function',
        isSnippet: true
      }
    ];

    const conditionItems = [
      ...variables,
      ...functions,
      {
        label: 'true',
        insertText: 'true',
        detail: 'keyword',
        documentation: 'Catch-all: always matches (use for default rule)',
        kind: 'Keyword'
      },
      {
        label: '==',
        insertText: '== ',
        detail: 'operator',
        documentation: 'Equality comparison (for strings: instrument == "ETF")',
        kind: 'Keyword'
      },
      {
        label: '!=',
        insertText: '!= ',
        detail: 'operator',
        documentation: 'Inequality comparison',
        kind: 'Keyword'
      }
    ];
    const expressionItems = [...variables, ...functions];

    const variable = (label: string): YamlFieldCompletion => ({ label, insertText: label, detail: 'custody variable' });
    const builtinFunctions = functions.filter((item) => item.kind === 'Function');
    const custodyInstruments = ['instrument', 'assetclass', 'isin', 'currency', 'mic'].map(variable);
    const amount = [
      ...['assetValue', 'accountValue', 'exchangeCount', 'positionCount'].map(variable),
      ...builtinFunctions
    ];
    const position = [...custodyInstruments, ...['positionValue', 'accountValue'].map(variable), ...builtinFunctions];
    const fxVariables = [
      ['payCurrency', 'Currency the client gives up'],
      ['receiveCurrency', 'Currency the client receives'],
      ['kind', 'TRADE, TRANSFER or INCOME'],
      ['amount', 'Conversion value in payCurrency at the EOD mid rate, before markup'],
      ['tierAmount', 'Conversion value in fx.amountCurrency at the EOD mid rate; requires amountCurrency'],
      ['payClass', 'Class of payCurrency in fx.currencyClasses, otherwise empty'],
      ['receiveClass', 'Class of receiveCurrency in fx.currencyClasses, otherwise empty'],
      ['mic', 'Security MIC for TRADE/INCOME; empty for TRANSFER']
    ].map(([label, documentation]) => ({ label, insertText: label, detail: 'FX variable', documentation }));
    const fx = [...fxVariables, ...builtinFunctions];
    return {
      'fx.rules.condition': fx,
      'fx.rules.expression': fx,
      'fx.periods.rules.condition': fx,
      'fx.periods.rules.expression': fx,
      condition: conditionItems,
      expression: expressionItems,
      'custody.periods.amount': amount,
      'custody.periods.exchangeCondition': [...custodyInstruments, ...builtinFunctions],
      'custody.periods.creditEligibility': [...custodyInstruments, variable('tradeDirection'), ...builtinFunctions],
      'custody.periods.valueRules.condition': position,
      'custody.periods.valueRules.expression': position
    };
  }
}
