import { Component, DestroyRef, inject, OnChanges, signal, output, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';

import { TableEditConfigBase } from '../../lib/datashowbase/table.edit.config.base';
import {
  EditableTableComponent,
  RowEditEvent,
  RowEditSaveEvent
} from '../../lib/datashowbase/editable-table.component';
import { EditInputType } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { TaxYearCorrection } from '../../entities/tax.year.correction';
import { TaxYearCorrectionService } from '../service/tax-year-correction.service';

/**
 * Editable table for maintaining the tax year corrections of one security across all tax years. Rows are saved
 * one by one through {@link TaxYearCorrectionService}. For tax years without ICTax data for the security's ISIN
 * the override fields (useTaxableAmount, taxableIncome) are not editable — only a comment may be entered. The
 * two override fields are mutually exclusive; editing one disables the other.
 */
@Component({
  selector: 'tax-year-correction-table',
  template: `
    <div style="display: flex; align-items: center; margin-bottom: 0.5rem;">
      <h5 style="margin: 0;">{{ 'TAX_YEAR_CORRECTIONS' | translate }}</h5>
      <p-button
        [rounded]="true"
        [text]="true"
        (click)="entityTable.addNewRow()"
        [disabled]="getFreeYears(null).length === 0"
        [style]="{ 'margin-left': '0.5rem' }">
        <i class="pi pi-plus" pButtonIcon></i>
      </p-button>
    </div>
    <editable-table
      #entityTable
      [(data)]="corrections"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idTaxYearCorrection"
      [contextMenuEnabled]="false"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [baseLocale]="baseLocale"
      [numberLocale]="numberLocale"
      [createNewEntityFn]="createNewEntity"
      [canDeleteRowFn]="canDeleteRow"
      (rowEditSave)="onRowEditSave($event)"
      (rowDelete)="onRowDelete($event)" />
  `,
  imports: [EditableTableComponent, TranslateModule, ButtonModule],
  providers: [TaxYearCorrectionService]
})
export class TaxYearCorrectionTableComponent extends TableEditConfigBase implements OnChanges {
  /** The security whose corrections are maintained. */
  readonly idSecuritycurrency = input<number>(undefined);
  /** Tax years offered for new rows in addition to the years with ICTax data (e.g. the report years). */
  readonly yearOptions = input<number[]>([]);
  /** Tax year preselected for new rows, typically the year the dialog was opened from. */
  readonly suggestedYear = input<number>(undefined);
  /** Fires after any successful create/update/delete so the opener can reload the dividends report. */
  readonly dataChanged = output<void>();

  private readonly destroyRef = inject(DestroyRef);
  private readSubscription?: Subscription;
  readonly corrections = signal<TaxYearCorrection[]>([]);

  /** User locale (e.g. 'de-CH') so the taxable income edit input formats numbers like the rest of GT. */
  numberLocale: string;

  /** Tax years with ICTax data for the security's ISIN; only these years allow override values. */
  private ictaxYearSet = new Set<number>();
  /** All selectable tax years (input years plus ICTax years), descending. */
  private readonly allYears = signal<number[]>([]);

  private readonly TAX_YEAR_CORRECTION = 'TAX_YEAR_CORRECTION';

  constructor(
    private taxYearCorrectionService: TaxYearCorrectionService,
    private messageToastService: MessageToastService,
    private confirmationService: ConfirmationService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filterService, usersettingsService, translateService, gps);
    this.numberLocale = gps.getLocale();
    const taxYearCol = this.addEditColumnFeqH(DataType.NumericInteger, 'taxYear', true, { width: 90 });
    taxYearCol.cec.inputType = EditInputType.Select;
    taxYearCol.cec.optionsProviderFn = (row: TaxYearCorrection) => this.getYearOptions(row);
    taxYearCol.cec.canEditFn = (row: TaxYearCorrection) => this.isNewRow(row);

    const useTaxableCol = this.addEditColumnFeqH(DataType.Boolean, 'useTaxableAmount', false, {
      templateName: 'check',
      width: 120
    });
    useTaxableCol.cec.canEditFn = (row: TaxYearCorrection) => this.hasIctaxYear(row) && row.taxableIncome == null;

    const taxableIncomeCol = this.addEditColumnFeqH(DataType.Numeric, 'taxableIncome', false, { width: 120 });
    taxableIncomeCol.cec.canEditFn = (row: TaxYearCorrection) => this.hasIctaxYear(row) && !row.useTaxableAmount;

    const noteCol = this.addEditColumnFeqH(DataType.String, 'note', false, {
      width: 350
    });
    noteCol.cec.inputType = EditInputType.Textarea;
    noteCol.cec.maxLength = 1024;
    noteCol.cec.rows = 3;

    this.multiSortMeta.push({ field: 'taxYear', order: -1 });
    this.prepareTableAndTranslate();
  }

  ngOnChanges(): void {
    this.readData();
  }

  private readData(): void {
    this.readSubscription?.unsubscribe();
    this.readSubscription = this.taxYearCorrectionService
      .getBySecurity(this.idSecuritycurrency())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((info) => {
        this.ictaxYearSet = new Set(info.ictaxTaxYears.map((y) => Number(y)));
        this.allYears.set(
          Array.from(new Set([...this.yearOptions(), ...info.ictaxTaxYears].map((y) => Number(y)))).sort(
            (a, b) => b - a
          )
        );
        this.corrections.set(info.corrections);
        this.notifyViewChanged();
      });
  }

  /** New rows carry no persisted id (null or the editable table's negative temporary key). */
  private isNewRow(row: TaxYearCorrection): boolean {
    return row.idTaxYearCorrection == null || row.idTaxYearCorrection < 0;
  }

  /** True when ICTax data exists for the row's tax year, i.e. override values may be entered. */
  private hasIctaxYear(row: TaxYearCorrection): boolean {
    return row.taxYear != null && this.ictaxYearSet.has(Number(row.taxYear));
  }

  /** Tax years not yet used by another correction record; the given row's own year stays selectable. */
  getFreeYears(row: TaxYearCorrection | null): number[] {
    const usedYears = new Set(
      this.corrections()
        .filter((c) => c !== row && c.taxYear != null)
        .map((c) => Number(c.taxYear))
    );
    return this.allYears().filter((y) => !usedYears.has(y));
  }

  private getYearOptions(row: TaxYearCorrection): ValueKeyHtmlSelectOptions[] {
    return this.getFreeYears(row).map((y) => new ValueKeyHtmlSelectOptions(y, String(y)));
  }

  createNewEntity = (): TaxYearCorrection => {
    const entity = new TaxYearCorrection();
    entity.idSecuritycurrency = this.idSecuritycurrency();
    const freeYears = this.getFreeYears(null);
    const suggestedYear = this.suggestedYear();
    entity.taxYear = freeYears.includes(Number(suggestedYear)) ? Number(suggestedYear) : (freeYears[0] ?? null);
    return entity;
  };

  readonly canDeleteRow = (row: TaxYearCorrection): boolean => {
    return true;
  };

  onRowEditSave(event: RowEditSaveEvent<TaxYearCorrection>): void {
    const entity = event.row;
    if (entity.taxYear == null) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'REQUIRED_FIELDS_MISSING');
      return;
    }
    // The native select binds string keys; the editable table assigns negative temporary ids to new rows.
    entity.taxYear = Number(entity.taxYear);
    if (this.isNewRow(entity)) {
      entity.idTaxYearCorrection = null;
    }
    if (!this.hasIctaxYear(entity)) {
      entity.useTaxableAmount = false;
      entity.taxableIncome = null;
    } else if (entity.useTaxableAmount) {
      entity.taxableIncome = null;
    }
    this.taxYearCorrectionService
      .update(entity)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageToastService.showMessageI18n(
            InfoLevelType.SUCCESS,
            event.isNew ? 'MSG_RECORD_CREATED' : 'MSG_RECORD_SAVED',
            { i18nRecord: this.TAX_YEAR_CORRECTION }
          );
          this.readData();
          this.dataChanged.emit();
        },
        error: () => this.readData()
      });
  }

  onRowDelete(event: RowEditEvent<TaxYearCorrection>): void {
    const entity = event.row;
    if (this.isNewRow(entity)) {
      this.corrections.update((rows) => rows.filter((c) => c !== entity));
      return;
    }
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.TAX_YEAR_CORRECTION,
      () => {
        this.taxYearCorrectionService
          .deleteEntity(entity.idTaxYearCorrection)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: this.TAX_YEAR_CORRECTION
            });
            this.readData();
            this.dataChanged.emit();
          });
      }
    );
  }
}
