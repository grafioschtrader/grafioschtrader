import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnInit, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { combineLatest } from 'rxjs';
import { FxQuote } from '../../entities/fx.markup';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { TradingPlatformPlanService } from '../service/trading.platform.plan.service';

/** Independent FX test form: evaluates the current unsaved document and displays tariff coverage explicitly. */
@Component({
  selector: 'fx-markup-preview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DynamicFormModule, TranslateModule],
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"></dynamic-form>
    @if (result) {
      <div aria-live="polite">
        <div>{{ 'FX_OUTCOME_' + result.outcome | translate }}</div>
        @for (field of resultFields; track field.field) {
          @if (field.field !== 'percent' || result.outcome === 'MATCHED') {
            <div>{{ field.headerKey | translate }}: {{ getValueByPath(result, field) }}</div>
          }
        }
        @if (result.status) {
          <div>{{ 'STATUS' | translate }}: {{ 'FX_STATUS_' + result.status | translate }}</div>
        }
        @if (result.error) {
          <div class="text-red-600">{{ result.error }}</div>
        }
      </div>
    }
    @if (requestFailed) {
      <div class="text-red-600">{{ 'FX_PREVIEW_FAILED' | translate }}</div>
    }
  `
})
export class FxMarkupPreviewComponent extends ShowRecordConfigBase implements OnInit, AfterViewInit {
  @Input() yaml = '';
  @Input() idSecurityaccount: number;
  @Input() idTradingPlatformPlan: number;
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  config: FieldFormGroup[];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig = { labelColumns: 4, nonModal: true };
  result: FxQuote;
  requestFailed = false;
  resultFields: ColumnConfig[] = [
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'percent', 'FX_MARKUP_PERCENT', true, false, {
      maxFractionDigits: 4
    }),
    ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'periodValidFrom', 'VALID_FROM'),
    ShowRecordConfigBase.createColumnConfig(DataType.String, 'ruleName', 'MATCHED_RULE')
  ];

  constructor(
    public override translateService: TranslateService,
    gps: GlobalparameterService,
    private gpsGT: GlobalparameterGTService,
    private plans: TradingPlatformPlanService,
    private accounts: SecurityaccountService
  ) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('payCurrency', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('receiveCurrency', true),
      DynamicFieldHelper.createFieldSelectString('kind', 'FX_CONVERSION_KIND', true),
      DynamicFieldHelper.createFieldInputNumberHeqF('amount', true, 14, 2, false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'date', true),
      DynamicFieldHelper.createFieldInputStringHeqF('mic', 4, false),
      DynamicFieldHelper.createFunctionButton('TEST_FX_MARKUP', () => this.preview())
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    combineLatest([this.gpsGT.getCurrencies(), this.plans.getFxKinds()]).subscribe(([currencies, kinds]) => {
      this.configObject.payCurrency.valueKeyHtmlOptions = currencies;
      this.configObject.receiveCurrency.valueKeyHtmlOptions = currencies;
      this.translateService.get(kinds.map((k) => k.value)).subscribe((texts) => {
        this.configObject.kind.valueKeyHtmlOptions = kinds.map((k) => ({ ...k, value: texts[k.value] }));
        this.form.setDefaultValuesAndEnableSubmit();
      });
    });
  }

  preview(): void {
    if (!this.form.form.valid) {
      this.form.form.markAllAsTouched();
      return;
    }
    const value = (name: string) => this.configObject[name].formControl.value;
    const body = {
      idSecurityaccount: this.idSecurityaccount,
      idTradingPlatformPlan: this.idTradingPlatformPlan,
      yaml: this.yaml,
      request: {
        payCurrency: value('payCurrency'),
        receiveCurrency: value('receiveCurrency'),
        kind: value('kind'),
        amount: value('amount'),
        date: value('date'),
        mic: value('mic')
      }
    };
    this.requestFailed = false;
    this.result = null;
    const result =
      this.idSecurityaccount != null ? this.accounts.estimateFxMarkup(body) : this.plans.estimateFxMarkup(body);
    result.subscribe({ next: (quote) => (this.result = quote), error: () => (this.requestFailed = true) });
  }
}
