import { Component, computed, effect, inject, input, output, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { SelectItem } from '@openng/optimus-ui/api';
import { SelectModule } from '@openng/optimus-ui/select';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { LogoScopeName } from '../types/logo.scope';
import type { LogoCatalogTableRow } from './instrument-logo.model';
import { InstrumentLogoService } from './instrument-logo.service';

/**
 * Search-result table for InstrumentLogoSelectDialogComponent. Extends TableConfigBase and renders through
 * ConfigurableTableComponent so column translation, sorting and styling match the rest of Grafioschtrader.
 * Existing catalog rows are read-only; when a drag-and-drop draft row is present at the top, its Name, ISIN and
 * Type cells are editable inline and selection stays locked to that draft row.
 */
@Component({
  selector: 'instrument-logo-select-table',
  imports: [ConfigurableTableComponent, FormsModule, SelectModule, TranslateModule],
  template: `
    <configurable-table
      [data]="displayRows()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="rowKey"
      [selectionMode]="'single'"
      [selection]="selectedRow()"
      (selectionChange)="onSelectionChange($event)"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [contextMenuEnabled]="false"
      [scrollable]="true"
      scrollHeight="280px"
      [loading]="loading()">
      <ng-template #customCell let-row let-field="field">
        @if (field.field === 'logo') {
          <div class="text-center">
            @if (getRowLogoUrl(row); as url) {
              <img class="logo-thumb" [src]="url" width="48" height="22" alt="" />
            }
          </div>
        } @else if (row.isEditable && field.field === 'name') {
          <div (click)="$event.stopPropagation()">
            <input
              type="text"
              class="form-control form-control-sm"
              maxlength="120"
              [ngModel]="editName()"
              (ngModelChange)="draftNameChange.emit($event)" />
          </div>
        } @else if (row.isEditable && field.field === 'isin') {
          <div (click)="$event.stopPropagation()">
            <input
              type="text"
              class="form-control form-control-sm text-uppercase"
              maxlength="12"
              [disabled]="editScope() === 'ISSUER'"
              [ngModel]="editIsin()"
              (ngModelChange)="draftIsinChange.emit($event)" />
          </div>
        } @else if (row.isEditable && field.field === 'logoScopeKey') {
          <div (click)="$event.stopPropagation()">
            <p-select
              [options]="scopeOptions()"
              [ngModel]="editScope()"
              (onChange)="draftScopeChange.emit($event.value)"
              appendTo="body"
              size="small"
              styleClass="w-100" />
          </div>
        } @else {
          <span>{{ getValueByPath(row, field) }}</span>
        }
      </ng-template>
    </configurable-table>
  `,
  styles: [
    `
      .logo-thumb {
        display: block;
        margin: auto;
        box-sizing: content-box;
        padding: 2px 4px;
        border-radius: 3px;
        background: #ffffff;
        object-fit: contain;
        border: 1px solid var(--p-content-border-color);
      }
    `
  ]
})
export class InstrumentLogoSelectTableComponent extends TableConfigBase {
  private readonly logoService = inject(InstrumentLogoService);

  readonly rows = input<LogoCatalogTableRow[]>([]);
  readonly selectedRow = input<LogoCatalogTableRow | null>(null);
  readonly loading = input(false);
  readonly draftActive = input(false);
  readonly scopeOptions = input<SelectItem<LogoScopeName>[]>([]);
  readonly editName = input('');
  readonly editIsin = input('');
  readonly editScope = input<LogoScopeName>('COMPANY');
  readonly editPreviewUrl = input<string | null>(null);

  readonly rowSelected = output<LogoCatalogTableRow | null>();
  readonly draftNameChange = output<string>();
  readonly draftIsinChange = output<string>();
  readonly draftScopeChange = output<LogoScopeName>();

  /** Shallow copy of input rows so TableConfigBase's translated-value properties never mutate caller objects. */
  readonly displayRows = computed(() => this.rows().map((row) => ({ ...row })));

  constructor() {
    super();
    this.addColumn(DataType.String, 'logo', 'LOGO', true, false, { width: 72 });
    this.addColumnFeqH(DataType.String, 'name', true, false);
    this.addColumnFeqH(DataType.String, 'isin', true, false, { width: 145 });
    this.addColumn(DataType.String, 'logoScopeKey', 'LOGO_SCOPE', true, false, {
      width: 145,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'sourceKey', 'INSTRUMENT_LOGO_SOURCE', true, false, {
      width: 120,
      translateValues: TranslateValue.NORMAL
    });
    this.prepareTableAndTranslate();

    effect(() => {
      const currentRows = this.displayRows();
      untracked(() => this.createTranslatedValueStore(currentRows));
    });

    // Re-render customCell thumbnails when an authenticated DB-SVG finishes loading its ObjectURL.
    effect(() => {
      this.logoService.revision();
      untracked(() => this.notifyViewChanged());
    });
  }

  getRowLogoUrl(row: LogoCatalogTableRow): string | null {
    if (row.isEditable) {
      const preview = this.editPreviewUrl();
      if (preview) {
        return preview;
      }
    }
    if (row.previewUrl) {
      return row.previewUrl;
    }
    this.logoService.revision();
    return this.logoService.getLogoUrl(row.logo);
  }

  onSelectionChange(selection: LogoCatalogTableRow | LogoCatalogTableRow[] | null): void {
    // Keep selection locked to the drag-and-drop draft until the user applies or discards it.
    if (this.draftActive()) {
      return;
    }
    const row = Array.isArray(selection) ? (selection[0] ?? null) : selection;
    this.rowSelected.emit(row);
  }
}
