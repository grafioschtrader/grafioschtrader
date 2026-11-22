import { Component, OnInit, inject, input, signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { forkJoin } from 'rxjs';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { SpinOffAccountDecision, SpinOffPreview } from '../model/security-action.model';
import { SecurityActionService } from '../service/security-action.service';
import { SpinOffPreviewTableComponent } from './spin-off-preview-table.component';

/** Presents the current tenant's spin-off preview and submits each account's decision. */
@Component({
  selector: 'spin-off-apply-dialog',
  template: `
    <p-dialog
      [header]="'APPLY_SPIN_OFF' | translate"
      [visible]="visibleDialog()"
      [style]="{ width: '76rem', maxWidth: '96vw' }"
      [modal]="true"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)">
      @if (preview(); as data) {
        @for (field of summaryFields; track field.field) {
          <div class="row">
            <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
            <div class="col-md-7 nopadding wrap">{{ formatValue(data, field) }} {{ currencyOf(data, field) }}</div>
          </div>
        }
        @if (data.childPriceDate !== data.actionDate) {
          <p>{{ 'SPIN_OFF_CHILD_CLOSE_LATER' | translate }}</p>
        }
        <p>{{ 'SPIN_OFF_PREVIEW_HELP' | translate }}</p>
        <spin-off-preview-table
          [rows]="data.accounts"
          [options]="options()"
          [busy]="busy()"
          (decisionChanged)="setDecision($event)" />
      }
      @if (busy()) {
        <p role="status">{{ 'SPIN_OFF_LOADING' | translate }}</p>
      }
      <signal-dynamic-form [config]="config" [formConfig]="formConfig" (submitBt)="apply()" />
    </p-dialog>
  `,
  imports: [DialogModule, TranslateModule, SpinOffPreviewTableComponent, SignalDynamicFormComponent]
})
export class SpinOffApplyDialogComponent
  extends SimpleFormEditBase<SignalDynamicFormComponent<Record<string, never>>>
  implements OnInit
{
  readonly actionId = input.required<number>();
  readonly preview = signal<SpinOffPreview | null>(null);
  readonly options = signal<ValueKeyHtmlSelectOptions[]>([]);
  readonly busy = signal(false);
  private readonly actions = inject(SecurityActionService);
  private readonly toast = inject(MessageToastService);
  private readonly translateService = inject(TranslateService);

  /**
   * The values shown above the account table, with their labels translated once into headerTranslated. They are
   * formatted with the data types of GT, so dates follow the user's date format and prices and the exchange rate show
   * the precision of the transaction tables.
   */
  readonly summaryFields: ColumnConfig[] = [
    ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'actionDate', 'ACTION_DATE'),
    this.priceField('parentClose', 'SPIN_OFF_PARENT_CLOSE'),
    this.priceField('childClose', 'SPIN_OFF_CHILD_CLOSE'),
    ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'childPriceDate', 'SPIN_OFF_CHILD_PRICE_DATE'),
    this.priceField('fxChildToParent', 'SPIN_OFF_CHILD_TO_PARENT_FX')
  ];

  /** Formats a value of the preview with the data type of its column configuration. */
  formatValue(preview: SpinOffPreview, field: ColumnConfig): string {
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, preview, field, field.field);
  }

  /** The currency a closing price is quoted in, empty for the other values. */
  currencyOf(preview: SpinOffPreview, field: ColumnConfig): string {
    switch (field.field) {
      case 'parentClose':
        return preview.parentCurrency;
      case 'childClose':
        return preview.childCurrency;
      default:
        return '';
    }
  }

  constructor() {
    super(HelpIds.HELP_BASEDATA_SECURITY_ACTION_SPIN_OFF);
  }

  ngOnInit(): void {
    const headerKeys = this.summaryFields.map((field) => field.headerKey).filter((key): key is string => !!key);
    this.translateService.get(headerKeys).subscribe((translations: Record<string, string>) => {
      this.summaryFields.forEach((field) => (field.headerTranslated = translations[field.headerKey ?? '']));
      this.viewChanged();
    });
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFunctionButtonFieldName('reload', 'SPIN_OFF_RELOAD_PREVIEW', () => this.load()),
      DynamicFieldHelper.createSubmitButton('APPLY_SPIN_OFF')
    ];
  }

  protected override initialize(): void {
    this.load();
  }

  setDecision(decision: SpinOffAccountDecision): void {
    this.preview.update(
      (preview) =>
        preview && {
          ...preview,
          accounts: preview.accounts.map((row) =>
            row.idSecurityaccount === decision.idSecurityaccount ? { ...row, decision: decision.decision } : row
          )
        }
    );
  }

  apply(): void {
    const preview = this.preview();
    if (this.busy() || !preview?.accounts.length) return;
    this.setBusy(true);
    const decisions = preview.accounts.map(({ idSecurityaccount, parentUnits, decision }) => ({
      idSecurityaccount,
      parentUnits,
      decision
    }));
    this.actions
      .applySecurityAction(this.actionId(), decisions)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (application) => {
          this.toast.showMessageI18n(InfoLevelType.SUCCESS, 'APPLY_SPIN_OFF_SUCCESS', {
            booked: application.bookedAccounts ?? 0,
            skipped: application.skippedAccounts ?? 0
          });
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED));
        },
        error: () => this.setBusy(false)
      });
  }

  private load(): void {
    this.preview.set(null);
    this.setBusy(true);
    forkJoin({ preview: this.actions.previewSpinOff(this.actionId()), options: this.actions.getSpinOffDecisions() })
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: ({ preview, options }) => {
          this.preview.set(preview);
          this.options.set(options);
          this.setBusy(false);
        },
        error: () => this.setBusy(false)
      });
  }

  /** A price or rate of the summary, with the precision of the price and exchange rate columns of GT. */
  private priceField(field: string, headerKey: string): ColumnConfig {
    return ShowRecordConfigBase.createColumnConfig(DataType.Numeric, field, headerKey, true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
  }

  private setBusy(busy: boolean): void {
    this.busy.set(busy);
    if (!busy) {
      this.form().enableSubmit();
    }
    this.form().setButtonState('reload', { disabled: busy });
    this.form().setButtonState('submit', { disabled: busy || !this.preview()?.accounts.length });
  }
}
