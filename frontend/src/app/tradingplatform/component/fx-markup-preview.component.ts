import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { afterNextRender, Component, DestroyRef, effect, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, map, Subscription, switchMap } from 'rxjs';
import { FxQuote } from '../../entities/fx.markup';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Helper } from '../../lib/helper/helper';
import { BaseSettings } from '../../lib/base.settings';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { TradingPlatformPlanService } from '../service/trading.platform.plan.service';

/** Independent FX test form: evaluates the current unsaved document and displays tariff coverage explicitly. */
@Component({
  selector: 'fx-markup-preview',
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule],
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" />
    @if (result(); as quote) {
      <div aria-live="polite">
        <div>{{ 'FX_OUTCOME_' + quote.outcome | translate }}</div>
        @for (field of resultFields; track field.field) {
          @if (field.field !== 'percent' || quote.outcome === 'MATCHED') {
            <div>{{ field.headerKey | translate }}: {{ getValueByPath(quote, field) }}</div>
          }
        }
        @if (quote.status) {
          <div>{{ 'STATUS' | translate }}: {{ 'FX_STATUS_' + quote.status | translate }}</div>
        }
        @if (quote.error) {
          <div class="text-red-600">{{ quote.error }}</div>
        }
      </div>
    }
    @if (requestFailed()) {
      <div class="text-red-600">{{ 'FX_PREVIEW_FAILED' | translate }}</div>
    }
  `
})
export class FxMarkupPreviewComponent extends ShowRecordConfigBase {
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);
  private plans: TradingPlatformPlanService = inject(TradingPlatformPlanService);
  private accounts: SecurityaccountService = inject(SecurityaccountService);

  readonly yaml = input('');
  readonly idSecurityaccount = input<number>();
  readonly idTradingPlatformPlan = input<number | null>();
  readonly form = viewChild.required(DynamicFormComponent);
  config: FieldFormGroup[];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig = { ...this.baseLocale, labelColumns: 4, nonModal: true };
  readonly result = signal<FxQuote | null>(null);
  readonly requestFailed = signal(false);
  private readonly destroyRef = inject(DestroyRef);
  private previewSubscription?: Subscription;
  resultFields: ColumnConfig[] = [
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'percent', 'FX_MARKUP_PERCENT', true, false, {
      maxFractionDigits: 4
    }),
    ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'periodValidFrom', 'VALID_FROM'),
    ShowRecordConfigBase.createColumnConfig(DataType.String, 'ruleName', 'MATCHED_RULE')
  ];

  constructor() {
    super();
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
    afterNextRender(() => this.loadOptions());
    effect(() => {
      this.yaml();
      this.idSecurityaccount();
      this.idTradingPlatformPlan();
      // Results belong to the document and account for which the request was made.
      this.previewSubscription?.unsubscribe();
      this.result.set(null);
      this.requestFailed.set(false);
    });
  }

  private loadOptions(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    forkJoin([this.gpsGT.getCurrencies(), this.plans.getFxKinds()])
      .pipe(
        switchMap(([currencies, kinds]) =>
          this.translateService
            .get(kinds.map((k) => k.value))
            .pipe(map((texts) => ({ currencies, kinds: kinds.map((k) => ({ ...k, value: texts[k.value] })) })))
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ currencies, kinds }) => {
        this.configObject.payCurrency.valueKeyHtmlOptions = currencies;
        this.configObject.receiveCurrency.valueKeyHtmlOptions = currencies;
        this.configObject.kind.valueKeyHtmlOptions = kinds;
      });
  }

  preview(): void {
    this.previewSubscription?.unsubscribe();
    this.result.set(null);
    this.requestFailed.set(false);
    const form = this.form();
    if (!form.form.valid) {
      form.form.markAllAsTouched();
      return;
    }
    const date = Helper.formatDateStringAsString(this.configObject.date, BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    if (date == null) return;
    const value = (name: string) => requireFormControl(this.configObject[name]).value;
    const body = {
      idSecurityaccount: this.idSecurityaccount(),
      idTradingPlatformPlan: this.idTradingPlatformPlan(),
      yaml: this.yaml(),
      request: {
        payCurrency: value('payCurrency'),
        receiveCurrency: value('receiveCurrency'),
        kind: value('kind'),
        amount: value('amount'),
        date,
        mic: value('mic')
      }
    };
    const result =
      this.idSecurityaccount() != null ? this.accounts.estimateFxMarkup(body) : this.plans.estimateFxMarkup(body);
    this.previewSubscription = result.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (quote) => this.result.set(quote),
      error: () => this.requestFailed.set(true)
    });
  }
}
