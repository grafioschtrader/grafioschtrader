import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { Subscription } from 'rxjs';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { InputType } from '../../lib/dynamic-form/models/input.type';
import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import saveAs from '../../lib/filesaver/filesaver';
import { TenantService } from '../../tenant/service/tenant.service';
import { HoldingService } from '../service/holding.service';
import {
  PerformanceReportOptions,
  PerformanceReportScope,
  reportOptionsForScope,
  PerformanceReportSettings,
  PerformanceReportRequest,
  performanceReportFilename,
  rememberedReportSettings
} from '../model/performance-report';

export interface PerformanceReportDialogData {
  window: PerformanceReportScope;
  scope: string;
  currency: string;
  settings: PerformanceReportSettings | null;
  options: PerformanceReportOptions;
  formDefinition: ClassDescriptorInputAndShow;
}

/** PDF generation and optional settings persistence are separate operations, also for read-only clients. */
@Component({
  template: `
    <p>
      <strong>{{ data.scope }}</strong> · {{ data.currency }}
    </p>
    @if (data.window.reportDate) {
      <p>{{ 'gt.report.reporting.date' | translate }}: {{ reportDate }}</p>
    } @else {
      <p>
        {{ 'gt.report.valuation.base' | translate }}: {{ dateFrom }}<br />
        {{ 'gt.report.booking.range' | translate }}: {{ bookingFrom }} – {{ dateTo }} ·
        {{ data.window.periodSplit | translate }}
      </p>
    }
    @if (droppedSections) {
      <p role="status">{{ 'gt.report.sections.dropped' | translate }}</p>
    }
    <dynamic-form
      #form="dynamicForm"
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      (submitBt)="submit()" />
    @if (transactionsSelected) {
      <p role="status">{{ 'gt.report.transactions.length.warning' | translate }}</p>
    }
    <details open>
      <summary>{{ 'REPORT_SECTIONS' | translate }}</summary>
      @for (section of data.options.sections; track section.key) {
        <p>
          <strong>{{ section.value | translate }}</strong
          >: {{ 'gt.report.section.' + ('' + section.key).toLowerCase() | translate }}
        </p>
      }
    </details>
    @if (downloading) {
      <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
    }
    <p-button [label]="'REPORT_CANCEL' | translate" [disabled]="downloading" (click)="close()" />
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, DynamicFormModule, ButtonModule, ProgressBarModule]
})
export class PerformanceReportDialogComponent extends FormBase implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  data: PerformanceReportDialogData;
  reportDate: string;
  dateFrom: string;
  dateTo: string;
  bookingFrom: string;
  downloading = false;
  droppedSections = false;
  transactionsSelected = false;
  private subscriptions = new Subscription();

  constructor(
    dialog: DynamicDialogConfig,
    private ref: DynamicDialogRef,
    public translateService: TranslateService,
    private gps: GlobalparameterService,
    private holdingService: HoldingService,
    private tenantService: TenantService
  ) {
    super();
    this.data = dialog.data;
  }

  ngOnInit(): void {
    this.data.options = reportOptionsForScope(this.data.options, !!this.data.window.reportDate);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, null, false);
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      this.data.formDefinition,
      '',
      false
    ) as FieldConfig[];
    this.config.splice(1, 0, DynamicFieldHelper.createFieldMultiSelectString('sections', 'REPORT_SECTIONS', true));
    this.config.push(
      DynamicFieldHelper.createFieldCheckboxHeqF('rememberSettings', {
        defaultValue: false,
        invisible: this.gps.isReadOnlyUser()
      }),
      DynamicFieldHelper.createSubmitButton('CREATE_PDF')
    );
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const options = this.data.options;
    this.configObject.preset.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      this.translateService,
      options.presets,
      false
    );
    this.configObject.sections.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      this.translateService,
      options.sections,
      false
    );
    this.configObject.language.valueKeyHtmlOptions = options.languages;
    this.configObject.numberFormat.valueKeyHtmlOptions = options.numberFormats;
    for (const field of ['recipient', 'sender', 'additionalNotes', 'comment']) {
      this.configObject[field].inputType = InputType.Pinputtextarea;
      this.configObject[field].textareaRows = field === 'recipient' || field === 'sender' ? 3 : 4;
    }
    this.configObject.detailColumns.invisible = true;
    const remembered = this.data.settings;
    const preset =
      remembered && options.presets.some((p) => p.key === remembered.preset)
        ? remembered.preset
        : this.data.window.reportDate
          ? 'STATEMENT_OF_ASSETS'
          : 'PERFORMANCE_DETAIL';
    const sections =
      preset === 'CUSTOM'
        ? (remembered?.sections || []).filter((s) => options.sections.some((o) => o.key === s))
        : options.presetSections[preset];
    this.droppedSections = !!remembered?.sections?.some((s) => !options.sections.some((o) => o.key === s));
    const locale =
      options.numberFormats.find((o) => String(o.key).toLowerCase() === this.gps.getLocale().toLowerCase())?.key ||
      options.numberFormats.find((o) => String(o.key).startsWith(this.gps.getUserLang()))?.key ||
      options.numberFormats[0].key;
    const initial = {
      ...remembered,
      preset,
      sections,
      annualYears: remembered?.annualYears || 10,
      detailColumns: remembered?.detailColumns || false,
      language: options.languages.some((o) => o.key === remembered?.language)
        ? remembered.language
        : this.gps.getUserLang(),
      numberFormat: options.numberFormats.some((o) => o.key === remembered?.numberFormat)
        ? remembered.numberFormat
        : locale
    };
    for (const field of this.config) {
      if (field.field in initial) {
        field.defaultValue = initial[field.field];
      }
    }
    this.configObject.annualYears.invisible = !sections.includes('ANNUAL_RETURNS');
    this.configObject.detailColumns.invisible = !sections.includes('HOLDINGS');
    this.transactionsSelected = sections.includes('TRANSACTIONS');
    if (this.data.window.reportDate) {
      this.reportDate = AppHelper.getDateByFormat(this.gps, this.data.window.reportDate);
      return;
    }
    this.dateFrom = AppHelper.getDateByFormat(this.gps, String(this.data.window.dateFrom));
    this.dateTo = AppHelper.getDateByFormat(this.gps, String(this.data.window.dateTo));
    const from = new Date(String(this.data.window.dateFrom).substring(0, 10) + 'T12:00:00');
    from.setDate(from.getDate() + 1);
    this.bookingFrom = AppHelper.getDateByFormat(
      this.gps,
      from.getFullYear() +
        '-' +
        String(from.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(from.getDate()).padStart(2, '0')
    );
  }

  ngAfterViewInit(): void {
    this.subscriptions.add(
      this.configObject.preset.formControl.valueChanges.subscribe((preset) => {
        if (preset !== 'CUSTOM') {
          const sections = this.data.options.presetSections[preset] || [];
          this.configObject.sections.formControl.setValue(sections, { emitEvent: false });
          this.configObject.annualYears.invisible = !sections.includes('ANNUAL_RETURNS');
          this.configObject.detailColumns.invisible = !sections.includes('HOLDINGS');
          this.transactionsSelected = sections.includes('TRANSACTIONS');
        }
      })
    );
    this.subscriptions.add(
      this.configObject.sections.formControl.valueChanges.subscribe((sections: string[]) => {
        this.configObject.preset.formControl.setValue('CUSTOM', { emitEvent: false });
        this.configObject.annualYears.invisible = !sections?.includes('ANNUAL_RETURNS');
        this.configObject.detailColumns.invisible = !sections?.includes('HOLDINGS');
        this.transactionsSelected = !!sections?.includes('TRANSACTIONS');
      })
    );
  }

  submit(): void {
    if (this.downloading) {
      return;
    }
    const request = { ...this.data.window } as PerformanceReportRequest;
    this.form.cleanMaskAndTransferValuesToBusinessObject(request, true);
    request.idPortfolio = this.data.window.idPortfolio || null;
    this.downloading = true;
    this.subscriptions.add(
      this.holdingService.downloadPeriodPerformancePdf(request).subscribe({
        next: (response) => {
          saveAs(response.body, performanceReportFilename(response.headers.get('Content-Disposition')));
          if (!this.gps.isReadOnlyUser() && this.configObject.rememberSettings.formControl.value) {
            this.subscriptions.add(
              this.tenantService.saveReportSettings(rememberedReportSettings(request)).subscribe({
                next: () => this.ref.close(),
                error: () => this.reset()
              })
            );
          } else {
            this.ref.close();
          }
        },
        error: () => this.reset()
      })
    );
  }

  private reset(): void {
    this.downloading = false;
    this.configObject.submit.disabled = false;
  }
  close(): void {
    this.ref.close();
  }
  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
