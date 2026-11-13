import { finalize, Subscription } from 'rxjs';
import { Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY, SignalFormModel } from '../../lib/dynamic-form/signal/signal-form.model';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import type { GermanTaxYearReport } from '../../entities/view/germantax/german.tax.year.report';
import { TenantGermanTaxDetailTableComponent } from './tenant-german-tax-detail-table.component';
import { TenantGermanTaxSumTableComponent } from './tenant-german-tax-sum-table.component';
import { TenantGermanTaxUnclassifiedTableComponent } from './tenant-german-tax-unclassified-table.component';
import { TenantGermanTaxWarningTableComponent } from './tenant-german-tax-warning-table.component';

/** The field of the form that selects the tax year. */
interface GermanTaxFormModel {
  taxYear: number | null;
}

/** The first tax year the German tax help calculates, the year since which the advance lump sum exists. */
const FIRST_TAX_YEAR = 2018;

/**
 * The starting values of a tax year for the German tax return: Anlage KAP and Anlage KAP-INV in euro. It is no tax
 * calculation and shows a sum only when its derivation has no gap. The form selects the tax year, the tables are child
 * components. The component is offered for a client that reports in euro and has Germany as its country.
 */
@Component({
  selector: 'tenant-german-tax-help',
  template: `
    <h4 [attr.title]="'GERMAN_TAX_HELP_TOOLTIP' | translate">{{ 'GERMAN_TAX_HELP' | translate }}</h4>
    <p class="fst-italic">{{ 'GERMAN_TAX_DISCLAIMER' | translate }}</p>
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [rules]="rules"
      [values]="startValues"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
    @if (report(); as taxReport) {
      @if (taxReport.provisional) {
        <div class="alert alert-info">{{ 'GERMAN_TAX_PROVISIONAL' | translate }}</div>
      }
      @if (taxReport.portfolioAssignmentRequired) {
        <div class="alert alert-warning">
          {{ 'GERMAN_TAX_ASSIGN_PORTFOLIOS' | translate }}
          <ul class="mb-0">
            @for (portfolio of taxReport.portfolios; track portfolio.idPortfolio) {
              <li>{{ portfolio.name }}: {{ withholdingLabel(portfolio.taxWithholding) }}</li>
            }
          </ul>
        </div>
      }
      <tenant-german-tax-sum-table [sums]="taxReport.sums" />
      @if (taxReport.warnings.length > 0) {
        <h5>{{ 'GERMAN_TAX_WARNINGS' | translate }}</h5>
        <tenant-german-tax-warning-table [warnings]="taxReport.warnings" />
      }
      @if (taxReport.unclassified.length > 0) {
        <h5>{{ 'GERMAN_TAX_UNCLASSIFIED' | translate }}</h5>
        <p>{{ 'GERMAN_TAX_UNCLASSIFIED_NOTE' | translate }}</p>
        <tenant-german-tax-unclassified-table [unclassified]="taxReport.unclassified" />
      }
      <h5>{{ 'GERMAN_TAX_DETAILS' | translate }}</h5>
      <tenant-german-tax-detail-table [details]="taxReport.details" />
    }
  `,
  imports: [
    TranslateModule,
    SignalDynamicFormComponent,
    TenantGermanTaxSumTableComponent,
    TenantGermanTaxWarningTableComponent,
    TenantGermanTaxUnclassifiedTableComponent,
    TenantGermanTaxDetailTableComponent
  ]
})
export class TenantGermanTaxHelpComponent extends ShowRecordConfigBase implements OnInit {
  private readonly portfolioService = inject(PortfolioService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly form = viewChild<SignalDynamicFormComponent<GermanTaxFormModel>>('form');

  formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 3, null, true);
  config: FieldFormGroup[] = [];
  readonly report = signal<GermanTaxYearReport | null>(null);

  /** The tax years from the current year down to the first one, the select shows them as they arrive. */
  private readonly yearOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  /** The year that is usually declared now, the previous one. */
  readonly startValues: Partial<GermanTaxFormModel> = {
    taxYear: Math.max(new Date().getFullYear() - 1, FIRST_TAX_YEAR)
  };

  readonly rules = (path: SchemaPathTree<GermanTaxFormModel>): void => {
    metadata(path.taxYear, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.yearOptions() }));
  };

  private readRequest?: Subscription;

  ngOnInit(): void {
    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('taxYear', true),
      DynamicFieldHelper.createSubmitButton('REALIZED_GAINS_CALCULATE')
    ];
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    const options: ValueKeyHtmlSelectOptions[] = [];
    for (let year = new Date().getFullYear(); year >= FIRST_TAX_YEAR; year--) {
      options.push(new ValueKeyHtmlSelectOptions(year, String(year)));
    }
    this.yearOptions.set(options);
    this.readReport(this.startValues.taxYear ?? FIRST_TAX_YEAR);
  }

  /** Calculates the report of the selected tax year. */
  submit(value: SignalFormModel): void {
    const taxYear = Number(value['taxYear']);
    if (Number.isFinite(taxYear)) {
      this.readReport(taxYear);
    } else {
      this.form()?.enableSubmit();
    }
  }

  /** The text of the withholding of a portfolio, or the note that it is not assigned. */
  withholdingLabel(taxWithholding: string | undefined): string {
    return this.translateService.instant(taxWithholding ?? 'GERMAN_TAX_PORTFOLIO_NOT_ASSIGNED');
  }

  private readReport(taxYear: number): void {
    this.readRequest?.unsubscribe();
    this.readRequest = this.portfolioService
      .getTaxGermany(taxYear)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.form()?.enableSubmit())
      )
      .subscribe((report) => this.report.set(report));
  }
}
