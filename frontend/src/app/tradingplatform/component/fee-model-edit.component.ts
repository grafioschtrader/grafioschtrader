import {Component, Input, OnInit, Optional} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {combineLatest, switchMap} from 'rxjs';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {HelpIds} from '../../lib/help/help.ids';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {TradingPlatformPlanService} from '../service/trading.platform.plan.service';
import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {Securityaccount} from '../../entities/securityaccount';
import {TransactionCostEstimateRequest, TransactionCostEstimateResult} from '../../entities/transaction.cost.estimate';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {FieldsetModule} from 'primeng/fieldset';
import {ButtonModule} from 'primeng/button';
import {YamlEditorComponent, YamlFieldCompletion} from '../../algo/component/yaml-editor.component';
import {HttpClient} from '@angular/common/http';
import {AppSettings} from '../../shared/app.settings';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

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
      <p-dialog header="{{'FEE_MODEL_YAML' | translate}}" [visible]="visibleDialog"
                [style]="{width: '900px'}"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
        <ng-container *ngTemplateOutlet="feeModelContent"></ng-container>
      </p-dialog>
    } @else {
      <ng-container *ngTemplateOutlet="feeModelContent"></ng-container>
    }

    <ng-template #feeModelContent>
      <yaml-editor [height]="'400px'" [(value)]="feeModelYamlValue" [schema]="feeModelSchema"
                   [fieldCompletions]="evalExCompletions"></yaml-editor>

      <div class="flex justify-end mt-3">
        <p-button [label]="'SAVE' | translate" icon="pi pi-check" (click)="save()" />
      </div>

      <p-fieldset [legend]="'TEST_FEE_ESTIMATION' | translate" [toggleable]="true" [collapsed]="true"
                  styleClass="mt-3">

        <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                      #form="dynamicForm">
        </dynamic-form>

        @if (testResult) {
          @if (testResult.estimatedCost != null) {
            <div class="text-green-600 mt-2">
              <strong>{{'ESTIMATED_COST' | translate}}:</strong> {{testResult.estimatedCost}}
              @if (testResult.matchedRuleName) {
                <span> &mdash; {{'MATCHED_RULE' | translate}}: {{testResult.matchedRuleName}}</span>
              }
            </div>
          }
          @if (testResult.error) {
            <div class="text-red-600 mt-2">
              <strong>Error:</strong> {{testResult.error}}
            </div>
          }
        }

      </p-fieldset>
    </ng-template>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule, FieldsetModule, ButtonModule,
    YamlEditorComponent, NgTemplateOutlet]
})
export class FeeModelEditComponent extends SimpleEditBase implements OnInit {

  static readonly DIALOG_WIDTH = 900;

  @Input() callParam: TradingPlatformPlan;

  isDynamic = false;
  feeModelYamlValue = '';
  feeModelSchema: any;
  evalExCompletions: { [fieldName: string]: YamlFieldCompletion[] };
  testResult: TransactionCostEstimateResult = null;

  private securityaccount: Securityaccount;

  constructor(private tradingPlatformPlanService: TradingPlatformPlanService,
              private securityaccountService: SecurityaccountService,
              private stockexchangeService: StockexchangeService,
              private gpsGT: GlobalparameterGTService,
              private httpClient: HttpClient,
              private messageToastService: MessageToastService,
              public translateService: TranslateService,
              gps: GlobalparameterService,
              @Optional() private dynamicDialogRef: DynamicDialogRef,
              @Optional() private dynamicDialogConfig: DynamicDialogConfig) {
    super(HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN, gps);
    this.isDynamic = !!dynamicDialogRef;
    this.loadFeeModelSchema();
    this.evalExCompletions = this.buildEvalExCompletions();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputNumberHeqF('tradeValue', false, 10, 2, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('units', false, 10, 4, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('mic', false),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', false),
      DynamicFieldHelper.createFieldInputNumberHeqF('fixedAssets', false, 12, 2, false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('specInvestInstrument', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('categoryType', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('tradeDirection', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'transactionDate', false),
      DynamicFieldHelper.createFunctionButton('TEST_FEE_ESTIMATION', (e) => this.testEstimation(e))
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.isDynamic) {
      this.securityaccount = this.dynamicDialogConfig.data.callParam.thisObject;
      this.initialize();
    }
  }

  protected override initialize(): void {
    if (this.isDynamic) {
      this.feeModelYamlValue = this.securityaccount?.feeModelYaml || '';
    } else {
      this.feeModelYamlValue = this.callParam?.feeModelYaml || '';
    }
    this.testResult = null;

    combineLatest([
      this.stockexchangeService.getAllStockexchanges(false),
      this.gpsGT.getCurrencies()
    ]).subscribe(([stockexchanges, currencies]) => {
      this.configObject.mic.valueKeyHtmlOptions = this.createMicOptions(stockexchanges);
      this.configObject.currency.valueKeyHtmlOptions =
        [new ValueKeyHtmlSelectOptions('', '')].concat(currencies);
      this.configObject.specInvestInstrument.valueKeyHtmlOptions =
        this.createOrdinalEnumOptions(SpecialInvestmentInstruments);
      this.configObject.categoryType.valueKeyHtmlOptions =
        this.createOrdinalEnumOptions(AssetclassType);
      this.translateService.get(['BUY', 'SELL']).subscribe(t => {
        this.configObject.tradeDirection.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', ''),
          new ValueKeyHtmlSelectOptions(0, t['BUY']),
          new ValueKeyHtmlSelectOptions(1, t['SELL'])
        ];
      });

      this.form.setDefaultValuesAndEnableSubmit();
    });
  }

  save(): void {
    if (this.isDynamic) {
      const sa = new Securityaccount();
      Object.assign(sa, this.securityaccount);
      sa.feeModelYaml = this.feeModelYamlValue?.trim() || null;
      this.securityaccountService.update(sa).subscribe({
        next: () => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
            {i18nRecord: AppSettings.SECURITYACCOUNT.toUpperCase()});
          this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.UPDATED));
        }
      });
    } else {
      const tradingPlatformPlan = new TradingPlatformPlan();
      Object.assign(tradingPlatformPlan, this.callParam);
      tradingPlatformPlan.feeModelYaml = this.feeModelYamlValue?.trim() || null;
      this.tradingPlatformPlanService.update(tradingPlatformPlan).subscribe({
        next: () => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
            {i18nRecord: AppSettings.TRADING_PLATFORM_PLAN.toUpperCase()});
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
        }
      });
    }
  }

  private createMicOptions(stockexchanges: Stockexchange[]): ValueKeyHtmlSelectOptions[] {
    const options = stockexchanges
      .filter(se => se.mic)
      .map(se => new ValueKeyHtmlSelectOptions(se.mic, se.name + ' - ' + se.mic));
    return [new ValueKeyHtmlSelectOptions('', ''), ...options];
  }

  /**
   * Creates select options from a numeric enum using ordinal values as keys.
   * Unlike SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty which uses enum name strings as keys,
   * this method uses numeric ordinals — required when the backend expects Integer fields.
   */
  private createOrdinalEnumOptions(e: any): ValueKeyHtmlSelectOptions[] {
    const keys: string[] = Object.keys(e).filter(k => typeof e[k] === 'number');
    const translateKeys = keys.map(k => e[e[k]]);
    const options: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    this.translateService.get(translateKeys).subscribe(translations => {
      for (const name of keys) {
        const ordinal: number = e[name];
        options.push(new ValueKeyHtmlSelectOptions(ordinal, translations[name] || name));
      }
    });
    return options;
  }

  /**
   * Tests fee estimation. In dynamic mode (securityaccount), uses inline YAML evaluation.
   * In p-dialog mode (TradingPlatformPlan), saves the plan first then estimates.
   */
  testEstimation(event: any): void {
    if (!this.feeModelYamlValue) {
      return;
    }

    const request: TransactionCostEstimateRequest = {
      idTradingPlatformPlan: this.isDynamic
        ? this.securityaccount?.tradingPlatformPlan?.idTradingPlatformPlan
        : this.callParam?.idTradingPlatformPlan,
      tradeValue: this.configObject.tradeValue.formControl.value || 0,
      units: this.configObject.units.formControl.value || 0,
      specInvestInstrument: this.configObject.specInvestInstrument.formControl.value,
      categoryType: this.configObject.categoryType.formControl.value,
      mic: this.configObject.mic.formControl.value || null,
      currency: this.configObject.currency.formControl.value || null,
      fixedAssets: this.configObject.fixedAssets.formControl.value,
      tradeDirection: this.configObject.tradeDirection.formControl.value,
      transactionDate: this.configObject.transactionDate.formControl.value || null
    };

    if (this.isDynamic) {
      request.yaml = this.feeModelYamlValue?.trim() || null;
      this.securityaccountService.estimateCostFromYaml(request).subscribe({
        next: (result: TransactionCostEstimateResult) => this.testResult = result,
        error: () => this.testResult = {error: 'Request failed'}
      });
    } else {
      if (!this.callParam?.idTradingPlatformPlan) {
        return;
      }
      const tradingPlatformPlan = new TradingPlatformPlan();
      Object.assign(tradingPlatformPlan, this.callParam);
      tradingPlatformPlan.feeModelYaml = this.feeModelYamlValue?.trim() || null;

      this.tradingPlatformPlanService.update(tradingPlatformPlan).pipe(
        switchMap(() => this.tradingPlatformPlanService.estimateTransactionCost(request))
      ).subscribe({
        next: (result: TransactionCostEstimateResult) => this.testResult = result,
        error: () => this.testResult = {error: 'Request failed'}
      });
    }
  }

  submit(value: { [name: string]: any }): void {
    this.save();
  }

  private loadFeeModelSchema(): void {
    this.httpClient.get('assets/schemas/fee-model-schema.json').subscribe({
      next: (schema: any) => this.feeModelSchema = schema,
      error: () => console.warn('Failed to load fee model schema')
    });
  }

  /**
   * Builds EvalEx autocompletion items for the condition and expression YAML fields.
   * Variables and functions match what TransactionCostEvalExEstimator registers on the backend.
   */
  private buildEvalExCompletions(): { [fieldName: string]: YamlFieldCompletion[] } {
    const variables: YamlFieldCompletion[] = [
      {label: 'tradeValue', insertText: 'tradeValue', detail: 'variable (numeric)', documentation: 'Total trade amount (price × units)'},
      {label: 'units', insertText: 'units', detail: 'variable (numeric)', documentation: 'Number of shares/units traded'},
      {label: 'instrument', insertText: 'instrument', detail: 'variable (string)',
        documentation: 'Investment instrument name: DIRECT_INVESTMENT, ETF, MUTUAL_FUND, PENSION_FUNDS, CFD, FOREX, ISSUER_RISK_PRODUCT, NON_INVESTABLE_INDICES'},
      {label: 'assetclass', insertText: 'assetclass', detail: 'variable (string)',
        documentation: 'Asset class name: EQUITIES, FIXED_INCOME, MONEY_MARKET, COMMODITIES, REAL_ESTATE, MULTI_ASSET, CONVERTIBLE_BOND, CREDIT_DERIVATIVE, CURRENCY_PAIR'},
      {label: 'mic', insertText: 'mic', detail: 'variable (string)', documentation: 'Market Identifier Code (e.g., XSWX, XNYS)'},
      {label: 'currency', insertText: 'currency', detail: 'variable (string)', documentation: 'Trade currency ISO code (e.g., CHF, USD)'},
      {label: 'fixedAssets', insertText: 'fixedAssets', detail: 'variable (numeric)', documentation: 'Total portfolio/account value for tier determination'},
      {label: 'tradeDirection', insertText: 'tradeDirection', detail: 'variable (numeric)', documentation: '0 = buy, 1 = sell'},
      {label: 'specInvestInstrument', insertText: 'specInvestInstrument', detail: 'variable (numeric)', documentation: 'Legacy numeric alias for instrument type ordinal'},
      {label: 'categoryType', insertText: 'categoryType', detail: 'variable (numeric)', documentation: 'Legacy numeric alias for asset class type ordinal'},
    ];

    const functions: YamlFieldCompletion[] = [
      {label: 'MAX', insertText: 'MAX($1, $2)$0', detail: 'function', documentation: 'Returns the greater of two values', kind: 'Function', isSnippet: true},
      {label: 'MIN', insertText: 'MIN($1, $2)$0', detail: 'function', documentation: 'Returns the lesser of two values', kind: 'Function', isSnippet: true},
      {label: 'ABS', insertText: 'ABS($1)$0', detail: 'function', documentation: 'Returns the absolute value', kind: 'Function', isSnippet: true},
      {label: 'ROUND', insertText: 'ROUND($1, $2)$0', detail: 'function', documentation: 'Rounds to specified decimal places', kind: 'Function', isSnippet: true},
      {label: 'CEILING', insertText: 'CEILING($1)$0', detail: 'function', documentation: 'Rounds up to the nearest integer', kind: 'Function', isSnippet: true},
      {label: 'FLOOR', insertText: 'FLOOR($1)$0', detail: 'function', documentation: 'Rounds down to the nearest integer', kind: 'Function', isSnippet: true},
      {label: 'IF', insertText: 'IF($1, $2, $3)$0', detail: 'function', documentation: 'IF(condition, trueValue, falseValue)', kind: 'Function', isSnippet: true},
      {label: 'NOT', insertText: 'NOT($1)$0', detail: 'function', documentation: 'Logical negation', kind: 'Function', isSnippet: true},
      {label: 'AND', insertText: 'AND($1, $2)$0', detail: 'function', documentation: 'Logical AND of two conditions', kind: 'Function', isSnippet: true},
      {label: 'OR', insertText: 'OR($1, $2)$0', detail: 'function', documentation: 'Logical OR of two conditions', kind: 'Function', isSnippet: true},
      {label: 'SQRT', insertText: 'SQRT($1)$0', detail: 'function', documentation: 'Square root', kind: 'Function', isSnippet: true},
      {label: 'LOG', insertText: 'LOG($1)$0', detail: 'function', documentation: 'Natural logarithm', kind: 'Function', isSnippet: true},
    ];

    const conditionItems = [...variables, ...functions,
      {label: 'true', insertText: 'true', detail: 'keyword', documentation: 'Catch-all: always matches (use for default rule)', kind: 'Keyword'},
      {label: '==', insertText: '== ', detail: 'operator', documentation: 'Equality comparison (for strings: instrument == "ETF")', kind: 'Keyword'},
      {label: '!=', insertText: '!= ', detail: 'operator', documentation: 'Inequality comparison', kind: 'Keyword'},
    ];
    const expressionItems = [...variables, ...functions];

    return {condition: conditionItems, expression: expressionItems};
  }

}
