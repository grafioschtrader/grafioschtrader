import { Component, inject } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { CheckboxModule } from '@openng/optimus-ui/checkbox';
import { SliderModule } from '@openng/optimus-ui/slider';
import { FormsModule } from '@angular/forms';
import { DynamicDialogConfig } from '@openng/optimus-ui/dynamicdialog';
import { ColumnConfig } from './column.config';

/**
 * Holder of the number of leading columns a table keeps in place while it is scrolled horizontally.
 */
export interface FrozenColumnHolder {
  frozenColumnCount: number;
}

/**
 * The table owning the columns. The dialog lives in its own DialogService root, so it tells the table about every
 * change while it is open; the table then re-renders also when it runs OnPush.
 */
export interface ColumnVisibilityOwner {
  notifyViewChanged(): void;
}

/**
 * Dialog component for toggling column visibility in tables.
 * Shows a list of checkboxes for all columns that have changeVisibility enabled.
 * Changes are applied in real-time as checkboxes are toggled because the fields
 * array is passed by reference.
 *
 * When the table passes a {@link FrozenColumnHolder} as data.frozenColumns, a slider above the list sets the number of
 * columns fixed on the left. It counts visible columns in their display order, hidden columns are skipped.
 */
@Component({
  selector: 'column-visibility-dialog',
  imports: [TranslateModule, CheckboxModule, SliderModule, FormsModule],
  template: `
    @if (frozenColumns) {
      <div class="column-visibility-frozen">
        <label>{{ 'FROZEN_COLUMN_COUNT' | translate }}: {{ frozenColumns.frozenColumnCount }}</label>
        <p-slider
          [(ngModel)]="frozenColumns.frozenColumnCount"
          (ngModelChange)="changed()"
          [min]="0"
          [max]="maxFrozenColumns"
          [step]="1"
          [ariaLabel]="'FROZEN_COLUMN_COUNT' | translate" />
      </div>
    }
    <div class="column-visibility-list">
      @for (field of toggleableFields; track field.field) {
        <div class="column-visibility-item">
          <p-checkbox
            [(ngModel)]="field.visible"
            (ngModelChange)="changed()"
            [binary]="true"
            [inputId]="'col-' + field.field" />
          <label [for]="'col-' + field.field" class="ms-2">
            {{ field.headerTranslated || field.headerKey }}
          </label>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .column-visibility-frozen {
        padding-bottom: 1rem;
        margin-bottom: 0.5rem;
        border-bottom: 1px solid var(--surface-border);
      }
      .column-visibility-frozen p-slider {
        display: block;
        margin: 0.75rem 0.5rem 0;
      }
      .column-visibility-list {
        max-height: 400px;
        overflow-y: auto;
      }
      .column-visibility-item {
        display: flex;
        align-items: center;
        padding: 0.5rem 0;
        border-bottom: 1px solid var(--surface-border);
      }
      .column-visibility-item:last-child {
        border-bottom: none;
      }
    `
  ]
})
export class ColumnVisibilityDialogComponent {
  private dynamicDialogConfig = inject(DynamicDialogConfig);

  /** Upper bound of the number of columns which can be fixed on the left */
  static readonly MAX_FROZEN_COLUMNS = 6;

  readonly maxFrozenColumns = ColumnVisibilityDialogComponent.MAX_FROZEN_COLUMNS;

  /** Table whose frozen column count the slider changes, null when the table does not offer it */
  frozenColumns: FrozenColumnHolder | null = null;

  private fields: ColumnConfig[] = [];

  private owner: ColumnVisibilityOwner | null = null;

  constructor() {
    this.fields = this.dynamicDialogConfig.data.fields;
    this.frozenColumns = this.dynamicDialogConfig.data.frozenColumns ?? null;
    this.owner = this.dynamicDialogConfig.data.owner ?? null;
  }

  /** Passes a change of a checkbox or of the slider on to the table while the dialog is open. */
  changed(): void {
    this.owner?.notifyViewChanged();
  }

  /**
   * Returns only the fields that can have their visibility toggled by the user.
   */
  get toggleableFields(): ColumnConfig[] {
    return this.fields.filter((f) => f.changeVisibility);
  }
}
