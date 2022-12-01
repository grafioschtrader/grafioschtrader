import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../app.settings';
import {FilterService, MenuItem, SelectItem, SortEvent, SortMeta} from 'primeng/api';
import {UserSettingsService} from '../service/user.settings.service';
import {Helper} from '../../helper/helper';
import {GlobalparameterService} from '../service/globalparameter.service';
import {ColumnConfig} from './column.config';
import {Table} from 'primeng/table';
import * as moment from 'moment';
import {FilterType} from './filter.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';
import {DataType} from '../../dynamic-form/models/data.type';
import {TableTreetableTotalBase} from './table.treetable.total.base';


export abstract class TableConfigBase extends TableTreetableTotalBase {
  FilterType: typeof FilterType = FilterType;

  formLocale: string;
  // Used when a component click event is consumed by the child and the parent should ignored it.
  readonly consumedGT = 'consumedGT';

  public hasFilter = false;

  customMatchModeOptions: SelectItem[] = [];
  customSearchNames: string[];

  rowsPerPage: number;
  firstRowIndexOnPage = 0;
  multiSortMeta: SortMeta[] = [];
  private visibleRestore: boolean[] = [];

  protected constructor(protected filterService: FilterService,
                        protected usersettingsService: UserSettingsService,
                        translateService: TranslateService,
                        gps: GlobalparameterService) {
    super(translateService, gps);
    this.formLocale = gps.getLocale();
  }

  get numberOfVisibleColumns(): number {
    return this.fields.filter(field => field.visible).length;
  }

  ////////////////////////////////////////////////////////////////////
  // Read and write table definition in persistence

  get groupFields(): ColumnConfig[] {
    const groupFields: ColumnConfig[] = [];
    for (let i = 0; i < this.fields.length; i++) {
      groupFields.push(this.fields[i]);
      if (this.fields[i].columnGroupConfigs && this.fields[i].columnGroupConfigs[0].colspan) {
        const nextUsedGroupColumn = this.getNextUsedGroupColumnIndex(i + 1);
        const newI = Math.min(i + this.fields[i].columnGroupConfigs[0].colspan, nextUsedGroupColumn);
        this.fields[i].columnGroupConfigs[0].colspan = newI - i;
        i = newI - 1;
      }
    }
    return groupFields;
  }

  prepareTableAndTranslate(): void {
    this.hasFilter = this.fields.filter(field => field.filterType).length > 0;
    if (this.hasFilter) {
      this.registerFilter();
    }
    this.translateHeadersAndColumns();
  }

  ////////////////////////////////////////////////////////////////////
  // Filter Table
  ////////////////////////////////////////////////////////////////////

  ////////////////////////////////////////////////////////////////////
  writeTableDefinition(key: string) {
    const visibleColumns: any[] = [];
    this.fields.forEach(field => {
        visibleColumns.push({[field.headerKey]: field.visible});
      }
    );
    this.usersettingsService.saveArray(key, visibleColumns);
  }

  readTableDefinition(key: string): void {
    const readedFields: any[] = this.usersettingsService.readArray(key);
    if (readedFields != null && readedFields.length > 0) {
      const fieldObject: any = Object.assign({}, ...readedFields);
      this.fields.forEach(field => {
        field.visible = fieldObject[field.headerKey];
      });
    }
  }

  public prepareFilter(data: any[]) {
    this.fields.forEach(field => {
      if (field.filterType && field.filterType === FilterType.withOptions) {
        const valueLabelHtmlSelectOptions: ValueLabelHtmlSelectOptions[] = [];
        valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions('', ' '));
        if (field.translateValues && field.translatedValueMap) {
          Object.keys(field.translatedValueMap).sort((a, b) => field.translatedValueMap[a] < field.translatedValueMap[b]
            ? -1 : field.translatedValueMap[a] > field.translatedValueMap[b] ? 1 : 0)
            .forEach(key =>
              valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(key, field.translatedValueMap[key]))
            );
        } else {
          const uniqueValuesSet = new Set(data.map(item => this.getValueByPath(item, field)));

          Array.from(uniqueValuesSet).sort((a, b) => a.toLowerCase() < b.toLowerCase() ? -1 :
            a.toLowerCase() > b.toLowerCase() ? 1 : 0).forEach(value => {
            valueLabelHtmlSelectOptions.push(new ValueLabelHtmlSelectOptions(value, value));
          });
        }
        field.filterValues = valueLabelHtmlSelectOptions;
      }
    });
  }

  createFilterField(data: any[]): void {
    const columnConfigs = this.fields.filter(columnConfig => columnConfig.filterType && columnConfig.dataType === DataType.DateNumeric);
    columnConfigs.forEach(cc => {
      const fieldName = cc.field +  AppSettings.FIELD_SUFFIX;
      data.forEach(item => item[fieldName] = this.getValueByPath(item, cc));
    });
  }

  public dateInputFilter(event, columnConfig: ColumnConfig, table: Table, calendar: any): void {
    if (calendar.value || !calendar.filled) {
      this.filterDate(calendar.value, columnConfig, table);
    }
  }

  public filterDate(event, columnConfig: ColumnConfig, table: Table): void {
    if (event) {
      if (columnConfig.dataType === DataType.DateNumeric) {
        const dateString = moment(event).format(this.gps.getDateFormat());
        table.filter(dateString, columnConfig.field +  AppSettings.FIELD_SUFFIX, 'equals');
      } else {
        const dateStringUS = moment(event).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);
        table.filter(dateStringUS, columnConfig.field, 'equals');
      }
    } else {
      // Without value
      table.filter(null, columnConfig.field + (columnConfig.dataType === DataType.DateNumeric ?  AppSettings.FIELD_SUFFIX : ''), null);
    }
  }

  public filterDecimal(event, columnConfig: ColumnConfig, table: Table): void {
    table.filter(event.target.value, columnConfig.field, 'equals');
    // startsWith
  }

  changeToUserSetting() {
    let i = 0;
    this.fields.forEach(columnConfig => columnConfig.visible = this.visibleRestore[i++]);
  }

  setFieldHeaderTranslation(columConfig: ColumnConfig): void {
    this.translateHeaders([columConfig.headerKey], [columConfig]);
  }

  createTranslatedValueStoreAndFilterField(data: any[]): void {
    this.createTranslatedValueStore(data);
    this.createFilterField(data);
  }

  onPage(event) {
    this.rowsPerPage = event.rows;
    this.firstRowIndexOnPage = event.first;
  }

  onColResize(event) {
    const columnConfig = this.getColumnConfigByHeaderTranslated(event.element.innerText.trim());
    columnConfig.width = event.element.style.width;
  }

  getStyle(field: ColumnConfig): any {
    return (field.width) ? {width: field.width + 'px'} : {};
  }

  /**
   *
   * event.data = Data to sort
   * event.mode = 'single' or 'multiple' sort mode
   * event.field = Sort field in single sort
   * event.order = Sort order in single sort
   * event.multiSortMeta = SortMeta array in multiple sort
   */
  customSort(event: SortEvent): void {
    if (event.mode === 'single') {
      this.customSortMultiple(event.data, [{field: event.field, order: event.order}]);
    } else {
      this.customSortMultiple(event.data, event.multiSortMeta);
    }
  }

  customSortMultiple(data: any, sortMeta: SortMeta[]): void {
    data.sort((data1, data2) => {
      let i = 0;
      let result = null;
      do {
        const columnConfig = this.getColumnConfigByField(sortMeta[i].field);
        const isDirectAccess = columnConfig.translateValues || columnConfig.fieldValueFN;
        const value1 = isDirectAccess ? this.getValueByPath(data1, columnConfig)
          : Helper.getValueByPath(data1, columnConfig.field);
        const value2 = isDirectAccess ? this.getValueByPath(data2, columnConfig)
          : Helper.getValueByPath(data2, columnConfig.field);

        if (value1 == null && value2 != null) {
          result = -1;
        } else if (value1 != null && value2 == null) {
          result = 1;
        } else if (value1 == null && value2 == null) {
          result = 0;
        } else if (typeof value1 === 'string' && typeof value2 === 'string') {
          result = value1.localeCompare(value2);
        } else {
          result = (value1 < value2) ? -1 : (value1 > value2) ? 1 : 0;
        }
        i++;
      } while (i < sortMeta.length && result === 0);

      return (sortMeta[i - 1].order * result);
    });
  }

  getColumnsShow(): MenuItem[] {
    const columnsMenuItems: MenuItem[] = [];
    this.fields.forEach(field => {
      if (field.changeVisibility) {
        columnsMenuItems.push({
          label: field.headerKey,
          icon: (field.visible) ? AppSettings.ICONNAME_SQUARE_CHECK : AppSettings.ICONNAME_SQUARE_EMTPY,
          command: (event) => this.handleHideShowColumn(event, field)
        });
      }
    });
    // AppHelper.translateMenuItems(columnsMenuItems, this.translateService);
    return columnsMenuItems;
  }

  hideShowColumnByFileHeader(fileHeader: string, visible: boolean) {
    this.fields.filter(field => field.headerKey === fileHeader).forEach(field => field.visible = visible);
  }

  handleHideShowColumn(event, field: ColumnConfig) {
    field.visible = !field.visible;
    event.item.icon = (field.visible) ? AppSettings.ICONNAME_SQUARE_CHECK : AppSettings.ICONNAME_SQUARE_EMTPY;
    //  this.changeDetectionStrategy.markForCheck();
  }

  getMenuShowOptions(): MenuItem[] {
    const items = this.getColumnsShow();
    return items.length > 0 ? [{label: 'ON_OFF_COLUMNS', items}] : null;
  }

  private registerFilter(): void {

    const filters: FilterFN[] = [{name: 'gtNoFilter', fn: null},
      {name: 'gtIS', fn: this.isEqual.bind(this)},
      {name: 'gtSameOrBefore', fn: this.sameOrBefore.bind(this)},
      {name: 'gtSameAfter', fn: this.sameOrAfter.bind(this)},
    ];

    this.customSearchNames = filters.map(v => v.name);
    this.translateService.get('GT_FILTER').subscribe(tr => filters.forEach(f =>
      this.customMatchModeOptions.push({value: f.name, label: tr[f.name]})));

    filters.forEach(f => {
      this.filterService.register(f.name, (value, filter): boolean => {
        if (filter === undefined || filter === null || f.fn === null) {
          return true;
        }
        return f.fn(value, filter);
      });
    });
  }

  private sameOrBefore(value, filter): boolean {
    return moment(value).isSameOrBefore(filter, 'day');
  }

  private sameOrAfter(value, filter): boolean {
    return moment(value).isSameOrAfter(filter, 'day');
  }

  private isEqual(value, filter): boolean {
    return moment(value).isSame(filter, 'day');
  }

  private getNextUsedGroupColumnIndex(startIndex: number): number {
    for (let i = startIndex; i < this.fields.length; i++) {
      if (this.fields[i].columnGroupConfigs) {
        return i;
      }
    }
    return this.fields.length - 1;
  }

}

interface FilterFN {
  name: string;
  fn: (value, filter) => boolean;
}

class SortFields {
  constructor(public fieldName: string, public order: number) {
  }

}






