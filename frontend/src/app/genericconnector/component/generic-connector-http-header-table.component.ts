import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TableEditConfigBase} from '../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditEvent, RowEditSaveEvent} from '../../lib/datashowbase/editable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GenericConnectorHttpHeader} from '../../entities/generic.connector.http.header';

/**
 * Standalone table component for editing HTTP headers of a generic connector definition.
 * Uses row-by-row editing mode with per-row edit/save/cancel and delete buttons.
 */
@Component({
  selector: 'generic-connector-http-header-table',
  template: `
    <editable-table #entityTable
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
          <p-button icon="pi pi-plus" [rounded]="true" [text]="true" (click)="entityTable.addNewRow()" [style]="{'margin-left': '0.5rem'}" />
        }
      </div>
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent, TranslateModule, ButtonModule]
})
export class GenericConnectorHttpHeaderTableComponent extends TableEditConfigBase implements OnChanges {

  @ViewChild('entityTable') entityTable: EditableTableComponent<GenericConnectorHttpHeader>;

  @Input() httpHeaders: GenericConnectorHttpHeader[] = [];
  @Input() editable: boolean = true;
  @Output() httpHeadersChange = new EventEmitter<GenericConnectorHttpHeader[]>();

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
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
   * Converts temporary string IDs assigned by EditableTableComponent (e.g. "new_1")
   * to null so the backend can assign real integer IDs on persist.
   */
  private stripTempIds(headers: GenericConnectorHttpHeader[]): GenericConnectorHttpHeader[] {
    return headers.map(h => {
      if (typeof h.idHttpHeader === 'string') {
        return {...h, idHttpHeader: null};
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
