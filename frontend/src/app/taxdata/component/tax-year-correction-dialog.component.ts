import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { Security } from '../../entities/security';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TaxYearCorrectionTableComponent } from './tax-year-correction-table.component';

/**
 * Modal dialog hosting the editable tax year correction table for one security. Closing always emits UPDATED so
 * the opener reloads the dividends report — corrections influence cell highlights (e.g. the ISIN background) and
 * year sums, and a stale display after any create/update/delete must be avoided.
 */
@Component({
  selector: 'tax-year-correction-dialog',
  template: `
    <p-dialog
      [visible]="visibleDialog"
      (visibleChange)="onVisibleChange($event)"
      [style]="{ width: '850px' }"
      [contentStyle]="{ 'max-height': '600px' }"
      (onHide)="onHide($event)"
      [modal]="true"
      [closable]="true"
      [closeOnEscape]="true">
      <ng-template pTemplate="header">
        <div style="white-space: normal; word-break: break-word; line-height: 1.3;">
          <div class="p-dialog-title">
            {{ 'TAX_YEAR_CORRECTIONS' | translate }}
          </div>
          <div>{{ security.name }}{{ security.isin ? ' / ' + security.isin : '' }}</div>
        </div>
      </ng-template>
      <tax-year-correction-table
        [idSecuritycurrency]="security.idSecuritycurrency"
        [yearOptions]="yearOptions"
        [suggestedYear]="suggestedYear">
      </tax-year-correction-table>
    </p-dialog>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, TranslateModule, TaxYearCorrectionTableComponent]
})
export class TaxYearCorrectionDialogComponent {
  @Input() security: Security;
  @Input() yearOptions: number[] = [];
  /** Tax year preselected for new rows, typically the year the dialog was opened from. */
  @Input() suggestedYear: number;
  @Input() visibleDialog: boolean;
  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  /** Guards against a double emit when both visibleChange and onHide fire for the same close. */
  private closeEmitted = false;

  /**
   * The dialog's visible input is bound one-way, so the X button and the ESC key only emit visibleChange(false)
   * without hiding the dialog themselves. This handler turns that signal into the closeDialog event; the opener
   * then removes the dialog from the DOM.
   */
  onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.emitClose();
    }
  }

  onHide(event): void {
    this.emitClose();
  }

  private emitClose(): void {
    if (!this.closeEmitted) {
      this.closeEmitted = true;
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
    }
  }
}
