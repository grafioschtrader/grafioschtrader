import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild} from '@angular/core';
import {FilterService} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ButtonModule} from 'primeng/button';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TableEditConfigBase} from '../../lib/datashowbase/table.edit.config.base';
import {EditableTableComponent, RowEditEvent, RowEditSaveEvent} from '../../lib/datashowbase/editable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {EditInputType} from '../../lib/datashowbase/column.config';
import {GenericConnectorFieldMapping} from '../../entities/generic.connector.field.mapping';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';

/**
 * Standalone table component for inline editing of field mappings within an endpoint.
 * Uses row-by-row editing mode with per-row edit/save/cancel and delete buttons.
 */
@Component({
  selector: 'generic-connector-field-mapping-table',
  template: `
    <editable-table #entityTable
                    [data]="fieldMappings"
                    (dataChange)="onDataChange($event)"
                    [fields]="fields"
                    dataKey="idFieldMapping"
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
        <h6 style="margin: 0;">{{ 'FIELD_MAPPINGS' | translate }}</h6>
        @if (editable) {
          <p-button icon="pi pi-plus" [rounded]="true" [text]="true" (click)="entityTable.addNewRow()" [style]="{'margin-left': '0.5rem'}" />
        }
      </div>
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent, TranslateModule, ButtonModule]
})
export class GenericConnectorFieldMappingTableComponent extends TableEditConfigBase implements OnChanges {

  private static readonly HISTORY_TARGET_FIELDS = ['date', 'open', 'high', 'low', 'close', 'volume'];
  private static readonly INTRA_TARGET_FIELDS = ['last', 'open', 'high', 'low', 'volume',
    'prevClose', 'changePercentage', 'timestamp'];

  @ViewChild('entityTable') entityTable: EditableTableComponent<GenericConnectorFieldMapping>;

  @Input() fieldMappings: GenericConnectorFieldMapping[] = [];
  @Input() feedSupport: string;
  @Input() editable: boolean = true;
  @Output() fieldMappingsChange = new EventEmitter<GenericConnectorFieldMapping[]>();

  constructor(filterService: FilterService,
              usersettingsService: UserSettingsService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
    this.setupColumns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['fieldMappings'] && this.fieldMappings) {
      this.createTranslatedValueStoreAndFilterField(this.fieldMappings);
    }
    if (changes['feedSupport']) {
      this.updateTargetFieldOptions();
    }
  }

  canDeleteRow = (_row: GenericConnectorFieldMapping): boolean => true;

  createNewEntity = (): GenericConnectorFieldMapping => {
    const mapping = new GenericConnectorFieldMapping();
    mapping.targetField = '';
    mapping.sourceExpression = '';
    mapping.required = false;
    return mapping;
  };

  onRowEditSave(event: RowEditSaveEvent<GenericConnectorFieldMapping>): void {
    this.fieldMappingsChange.emit(this.stripTempIds(this.fieldMappings));
  }

  onRowEditCancel(_event: RowEditEvent<GenericConnectorFieldMapping>): void {
    // Cancelled -- data restored by EditableTableComponent
  }

  onRowDelete(event: RowEditEvent<GenericConnectorFieldMapping>): void {
    this.fieldMappings = this.fieldMappings.filter((_, i) => i !== event.index);
    this.fieldMappingsChange.emit(this.stripTempIds(this.fieldMappings));
  }

  onDataChange(data: GenericConnectorFieldMapping[]): void {
    this.fieldMappings = data;
  }

  /**
   * Converts temporary string IDs assigned by EditableTableComponent (e.g. "new_1")
   * to null so the backend can assign real integer IDs on persist.
   */
  private stripTempIds(mappings: GenericConnectorFieldMapping[]): GenericConnectorFieldMapping[] {
    return mappings.map(m => {
      if (typeof m.idFieldMapping === 'string') {
        return {...m, idFieldMapping: null};
      }
      return m;
    });
  }

  private updateTargetFieldOptions(): void {
    const targetFields = this.feedSupport === 'FS_INTRA'
      ? GenericConnectorFieldMappingTableComponent.INTRA_TARGET_FIELDS
      : GenericConnectorFieldMappingTableComponent.HISTORY_TARGET_FIELDS;
    const targetFieldColumn = this.fields.find(f => f.field === 'targetField');
    if (targetFieldColumn?.cec) {
      targetFieldColumn.cec.valueKeyHtmlOptions = targetFields.map(f => new ValueKeyHtmlSelectOptions(f, f));
    }
  }

  private setupColumns(): void {
    const ccTarget = this.addEditColumnFeqH(DataType.String, 'targetField', true);
    ccTarget.cec.inputType = EditInputType.Select;
    ccTarget.cec.valueKeyHtmlOptions = [];

    const ccSource = this.addEditColumnFeqH(DataType.String, 'sourceExpression', true);
    ccSource.cec.maxLength = 255;

    const ccIndex = this.addEditColumnFeqH(DataType.NumericInteger, 'csvColumnIndex', false);
    ccIndex.cec.min = 0;

    const ccDivider = this.addEditColumnFeqH(DataType.String, 'dividerExpression', false);
    ccDivider.cec.maxLength = 64;

    this.addEditColumnFeqH(DataType.Boolean, 'required', false, {templateName: 'check'});

    this.prepareTableAndTranslate();
  }
}
