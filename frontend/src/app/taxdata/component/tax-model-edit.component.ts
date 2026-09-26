import { ViewChild, Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { SimpleEditBase } from '../../lib/edit/simple.edit.base';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { YamlEditorComponent, YamlFieldCompletion } from '../../algo/component/yaml-editor.component';
import { TaxDataService } from '../service/tax-data.service';
import { TaxCountry } from '../model/tax-data.model';
import { TaxDetailsTableComponent } from './tax-details-table.component';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';

/** Administrator editor and side-effect-free preview of the currently typed model. */
@Component({
  selector: 'tax-model-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    TranslateModule,
    DialogModule,
    ButtonModule,
    DynamicFormModule,
    YamlEditorComponent,
    TaxDetailsTableComponent
  ],
  template: ` <p-dialog
    styleClass="big-dialog"
    [header]="'TAX_MODEL_YAML' | translate"
    [visible]="visibleDialog"
    [modal]="true"
    [style]="{ width: '900px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    <p>{{ 'TAX_MODEL_LIMITATIONS' | translate }}</p>
    <yaml-editor
      #yamlEditor
      format="TAXES"
      [(value)]="yaml"
      [schema]="schema"
      [fieldCompletions]="evalExCompletions"
      [height]="'300px'" />
    @if (tooLarge) {
      <p role="alert">{{ 'TAX_MODEL_TOO_LARGE' | translate }}</p>
    }
    <p-button
      [label]="'SAVE' | translate"
      [disabled]="tooLarge || yamlEditor.validating || !yamlEditor.syntaxValid"
      (click)="save()" />
    <p-button [label]="'TAX_MODEL_CLEAR' | translate" (click)="save(null)" />
    <details>
      <summary>{{ 'TEST_TAX_ESTIMATION' | translate }}</summary>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="preview()" />
      @if (result) {
        <p>{{ (result.complete ? 'TAX_ESTIMATE_COMPLETE' : 'TAX_ESTIMATE_INCOMPLETE') | translate }}</p>
        <tax-details-table mode="estimate" [rows]="[result]" />
        <tax-details-table mode="matches" [rows]="result.matchedRules" [currency]="result.currency" />
        <tax-details-table mode="warnings" [rows]="result.warnings" />
      }
    </details>
  </p-dialog>`
})
export class TaxModelEditComponent extends SimpleEditBase implements OnInit {
  @ViewChild(YamlEditorComponent) yamlEditor: YamlEditorComponent;
  @Input() country: TaxCountry;
  yaml = '';
  schema: object;
  evalExCompletions: { [fieldName: string]: YamlFieldCompletion[] };
  errors: string[] = [];
  result: any;

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private gpsGT: GlobalparameterGTService,
    private service: TaxDataService,
    private http: HttpClient
  ) {
    super(HelpIds.HELP_ALGO_HISTORICAL_RUN_TAX_MODEL, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('eventKind', true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'eventDate', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true),
      ...['units', 'price', 'cleanValue', 'accruedInterest', 'grossIncome'].map((name) =>
        DynamicFieldHelper.createFieldInputNumberHeqF(name, false, 12, 8, false)
      ),
      ...['instrument', 'assetclass', 'mic'].map((name) =>
        DynamicFieldHelper.createFieldInputStringHeqF(name, 40, false)
      ),
      ...['issuerCountry', 'dealerCountry', 'exchangeCountry'].map((name) =>
        DynamicFieldHelper.createFieldSelectStringHeqF(name, false)
      ),
      DynamicFieldHelper.createFieldTriStateCheckbox('exemptInvestor', 'EXEMPT_INVESTOR'),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.evalExCompletions = TaxModelEditComponent.buildEvalExCompletions();
    this.http.get('assets/schemas/tax-model-schema.json').subscribe((schema) => (this.schema = schema));
  }

  protected override initialize(): void {
    this.service.getTaxModel(this.country.idTaxCountry).subscribe((model) => (this.yaml = model.yaml || ''));
    this.service.taxOptions('eventkinds').subscribe((options) => {
      this.configObject.eventKind.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
        this.translateService,
        options,
        false
      );
    });
    this.gpsGT.getCurrencies().subscribe((options) => (this.configObject.currency.valueKeyHtmlOptions = options));
    this.gps.getCountriesForSelectBox().subscribe((options) => {
      for (const key of ['issuerCountry', 'dealerCountry', 'exchangeCountry'])
        this.configObject[key].valueKeyHtmlOptions = options;
    });
    this.form.setDefaultValuesAndEnableSubmit();
  }

  get tooLarge(): boolean {
    return new TextEncoder().encode(this.yaml).length > 65536;
  }

  async save(yaml: string = this.yaml): Promise<void> {
    if (yaml !== null && !(await this.yamlEditor.validateForSubmit())) return;
    this.service
      .saveTaxModel(this.country.idTaxCountry, yaml)
      .subscribe(() => this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)));
  }

  async preview(): Promise<void> {
    if (!(await this.yamlEditor.validateForSubmit())) {
      this.configObject.submit.disabled = false;
      return;
    }
    if (this.tooLarge) return;
    const request: any = { yaml: this.yaml, countryCode: this.country.countryCode };
    this.form.cleanMaskAndTransferValuesToBusinessObject(request);
    for (const field of ['units', 'price', 'cleanValue', 'accruedInterest', 'grossIncome']) request[field] ??= 0;
    this.service.estimateTax(request).subscribe((result) => {
      this.result = result;
      this.configObject.submit.disabled = false;
    });
  }

  /** EvalEx variables, functions and event-kind comparisons accepted by the backend tax evaluator. */
  private static buildEvalExCompletions(): { [fieldName: string]: YamlFieldCompletion[] } {
    const variable = (label: string, type: string, documentation: string): YamlFieldCompletion => ({
      label,
      insertText: label,
      detail: `variable (${type})`,
      documentation
    });
    const variables: YamlFieldCompletion[] = [
      variable('eventKind', 'string', 'BUY, SELL, DIVIDEND or SECURITY_INTEREST'),
      variable('units', 'numeric', 'Absolute number of units'),
      variable('price', 'numeric', 'Price per unit'),
      variable('cleanValue', 'numeric', 'Trade consideration before accrued interest, fees and taxes'),
      variable('accruedInterest', 'numeric', 'Accrued interest transferred with a bond trade'),
      variable('tradeValue', 'numeric', 'Clean value plus accrued interest'),
      variable('grossIncome', 'numeric', 'Gross dividend or securities interest before withholding'),
      variable('currency', 'string', 'Instrument currency as an ISO code'),
      variable('instrument', 'string', 'Investment instrument classification'),
      variable('assetclass', 'string', 'Asset-class classification'),
      variable('mic', 'string', 'Market Identifier Code'),
      variable('issuerCountry', 'string', 'Issuer country as an ISO code'),
      variable('dealerCountry', 'string', 'Dealer country as an ISO code'),
      variable('exchangeCountry', 'string', 'Exchange country as an ISO code'),
      variable('exemptInvestor', 'numeric', '1 for exempt, 0 for explicitly non-exempt; absent when unknown')
    ];
    const functionCompletion = (label: string, insertText: string, documentation: string): YamlFieldCompletion => ({
      label,
      insertText,
      detail: 'function',
      documentation,
      kind: 'Function',
      isSnippet: true
    });
    const functions: YamlFieldCompletion[] = [
      functionCompletion('MAX', 'MAX($1, $2)$0', 'Returns the greater of two values'),
      functionCompletion('MIN', 'MIN($1, $2)$0', 'Returns the lesser of two values'),
      functionCompletion('ABS', 'ABS($1)$0', 'Returns the absolute value'),
      functionCompletion('ROUND', 'ROUND($1, $2)$0', 'Rounds to the requested number of decimal places'),
      functionCompletion('CEILING', 'CEILING($1)$0', 'Rounds up to the nearest integer'),
      functionCompletion('FLOOR', 'FLOOR($1)$0', 'Rounds down to the nearest integer'),
      functionCompletion('IF', 'IF($1, $2, $3)$0', 'Chooses a value according to a condition'),
      functionCompletion('NOT', 'NOT($1)$0', 'Logical negation'),
      functionCompletion('AND', 'AND($1, $2)$0', 'Logical AND of two conditions'),
      functionCompletion('OR', 'OR($1, $2)$0', 'Logical OR of two conditions'),
      functionCompletion('SQRT', 'SQRT($1)$0', 'Square root'),
      functionCompletion('LOG', 'LOG($1)$0', 'Natural logarithm')
    ];
    const eventKinds = ['BUY', 'SELL', 'DIVIDEND', 'SECURITY_INTEREST'].map((kind): YamlFieldCompletion => ({
      label: `eventKind == "${kind}"`,
      insertText: `eventKind == "${kind}"`,
      detail: 'event kind comparison',
      documentation: `Matches ${kind} events`,
      kind: 'Keyword'
    }));
    const condition = [
      ...variables,
      ...functions,
      ...eventKinds,
      {
        label: 'true',
        insertText: 'true',
        detail: 'keyword',
        documentation: 'Always matches; useful for an explicit fallback rule',
        kind: 'Keyword'
      } satisfies YamlFieldCompletion
    ];
    return { condition, expression: [...variables, ...functions] };
  }
}
