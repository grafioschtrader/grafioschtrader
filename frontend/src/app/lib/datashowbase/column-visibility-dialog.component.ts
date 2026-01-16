import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {CheckboxModule} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {DynamicDialogConfig} from 'primeng/dynamicdialog';
import {ColumnConfig} from './column.config';

/**
 * Dialog component for toggling column visibility in tables.
 * Shows a list of checkboxes for all columns that have changeVisibility enabled.
 * Changes are applied in real-time as checkboxes are toggled because the fields
 * array is passed by reference.
 */
@Component({
  selector: 'column-visibility-dialog',
  standalone: true,
  imports: [CommonModule, TranslateModule, CheckboxModule, FormsModule],
  template: `
    <div class="column-visibility-list">
      @for (field of toggleableFields; track field.field) {
        <div class="column-visibility-item">
          <p-checkbox [(ngModel)]="field.visible"
                      [binary]="true"
                      [inputId]="'col-' + field.field"/>
          <label [for]="'col-' + field.field" class="ms-2">
            {{field.headerTranslated || field.headerKey}}
          </label>
        </div>
      }
    </div>
  `,
  styles: [`
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
  `]
})
export class ColumnVisibilityDialogComponent {
  private fields: ColumnConfig[] = [];

  constructor(private dynamicDialogConfig: DynamicDialogConfig) {
    this.fields = this.dynamicDialogConfig.data.fields;
  }

  /**
   * Returns only the fields that can have their visibility toggled by the user.
   */
  get toggleableFields(): ColumnConfig[] {
    return this.fields.filter(f => f.changeVisibility);
  }
}
