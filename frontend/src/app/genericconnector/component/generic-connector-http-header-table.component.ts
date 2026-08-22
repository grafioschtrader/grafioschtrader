import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
  ChangeDetectionStrategy
} from '@angular/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { TableEditConfigBase } from '../../lib/datashowbase/table.edit.config.base';
import {
  EditableTableComponent,
  RowEditEvent,
  RowEditSaveEvent
} from '../../lib/datashowbase/editable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GenericConnectorHttpHeader } from '../../entities/generic.connector.http.header';

/**
 * Standalone table component for editing HTTP headers of a generic connector definition.
 * Uses row-by-row editing mode with per-row edit/save/cancel and delete buttons.
 */
@Component({
  selector: 'generic-connector-http-header-table',
  template: `
    <editable-table
      #entityTable
      [data]="httpHeaders"
      (dataChange)="onDataChange($event)"
      [fields]="fields"
      dataKey="idHttpHeader"
      [showEditColumn]="editable"
      [editColumnWidth]="120"
      [selectionMode]="null"
      [contextMenuEnabled]="false"
      [createNewEntityFn]="createNewEntity.bind(this)"
      (rowEditSave)="onRowEditSave($event)"
      (rowEditCancel)="onRowEditCancel($event)"
      [canDeleteRowFn]="canDeleteRow"
      (rowDelete)="onRowDelete($event)"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [baseLocale]="baseLocale"
      [scrollable]="false"
      [containerClass]="''"
      [stripedRows]="true">
      <div caption style="display: flex; align-items: center;">
        <h6 style="margin: 0;">{{ 'HTTP_HEADERS' | translate }}</h6>
        @if (editable) {
          <p-button
            [rounded]="true"
            [text]="true"
            (click)="entityTable.addNewRow()"
            [style]="{ 'margin-left': '0.5rem' }">
            <i class="pi pi-plus" pButtonIcon></i>
          </p-button>
        }
      </div>
    </editable-table>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [EditableTableComponent, TranslateModule, ButtonModule]
})
export class GenericConnectorHttpHeaderTableComponent extends TableEditConfigBase implements OnChanges {
  @ViewChild('entityTable')
  entityTable: EditableTableComponent<GenericConnectorHttpHeader>;

  @Input() httpHeaders: GenericConnectorHttpHeader[] = [];
  @Input() editable: boolean = true;
  @Output() httpHeadersChange = new EventEmitter<GenericConnectorHttpHeader[]>();

  constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filterService, usersettingsService, translateService, gps);
    this.setupColumns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['httpHeaders'] && this.httpHeaders) {
      this.createTranslatedValueStoreAndFilterField(this.httpHeaders);
    }
  }

  canDeleteRow = (_row: GenericConnectorHttpHeader): boolean => true;

  createNewEntity = (): GenericConnectorHttpHeader => {
    const header = new GenericConnectorHttpHeader();
    header.headerName = '';
    header.headerValue = '';
    return header;
  };

  onRowEditSave(event: RowEditSaveEvent<GenericConnectorHttpHeader>): void {
    this.httpHeadersChange.emit(this.stripTempIds(this.httpHeaders));
  }

  onRowEditCancel(_event: RowEditEvent<GenericConnectorHttpHeader>): void {
    // Cancelled — data restored by EditableTableComponent
  }

  onRowDelete(event: RowEditEvent<GenericConnectorHttpHeader>): void {
    this.httpHeaders = this.httpHeaders.filter((_, i) => i !== event.index);
    this.httpHeadersChange.emit(this.stripTempIds(this.httpHeaders));
  }

  onDataChange(data: GenericConnectorHttpHeader[]): void {
    this.httpHeaders = data;
  }

  /**
   * Converts temporary IDs assigned by EditableTableComponent.addNewRow() — negative numbers
   * (or historical "new_1"-style strings) — to null so the backend inserts the row instead of
   * treating it as a detached entity (StaleObjectStateException).
   */
  private stripTempIds(headers: GenericConnectorHttpHeader[]): GenericConnectorHttpHeader[] {
    return headers.map((h) => {
      if (typeof h.idHttpHeader === 'string' || (typeof h.idHttpHeader === 'number' && h.idHttpHeader < 0)) {
        return { ...h, idHttpHeader: null };
      }
      return h;
    });
  }

  private setupColumns(): void {
    this.addEditColumnFeqH(DataType.String, 'headerName', true);
    this.addEditColumnFeqH(DataType.String, 'headerValue', true);
    this.prepareTableAndTranslate();
  }
}
