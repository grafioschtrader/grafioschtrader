import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {Directive, EventEmitter, Input, Output} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Security} from '../../entities/security';
import {plainToClass} from 'class-transformer';
import {DeleteCreateMultiple} from '../service/delete.create.multiple';
import {ClassConstructor} from 'class-transformer/types/interfaces';
import {FilterService} from 'primeng/api';


@Directive()
export abstract class SplitPeriodTableBase<T> extends TableConfigBase {
  @Input() maxRows: number;
  @Output() editData: EventEmitter<T> = new EventEmitter<any>();
  @Output() savedData: EventEmitter<SaveSecuritySuccess> = new EventEmitter<any>();

  selectedRow: T;
  dataChanged = false;

  protected constructor(public dataSortKey: string,
                        public maxRowMessageKey: string,
                        private classz: ClassConstructor<T>,
                        private messageToastService: MessageToastService,
                        private deleteCreateMultipleService: DeleteCreateMultiple<T>,
                        filterService: FilterService,
                        usersettingsService: UserSettingsService,
                        translateService: TranslateService,
                        gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
    this.multiSortMeta.push({field: dataSortKey, order: 1});
  }

  _dataList: T[] = [];

  get dataList(): T[] {
    return this._dataList;
  }

  setDataList(securitysplitList: T[], dataChanged) {
    this.dataChanged = dataChanged;
    this._dataList = securitysplitList;
    this.createTranslatedValueStoreAndFilterField(this._dataList);
  }

  public addDataRow(rowData: T): void {
    const dateStr = moment(rowData[this.dataSortKey]).format(AppSettings.FORMAT_DATE_SHORT_NATIVE);
    const replacePos = this._dataList.findIndex(ss => ss[this.dataSortKey] === dateStr ||
      moment(ss[this.dataSortKey]).format(AppSettings.FORMAT_DATE_SHORT_NATIVE) === dateStr);

    if (replacePos === -1) {
      if (this._dataList.length < this.maxRows) {
        this._dataList = [...this._dataList, rowData];
      } else {
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'SECURITY_SPLITS_MAX_REACHED', {maxSplits: this.maxRows});
        return;
      }
    } else {
      this._dataList = Object.assign([], this._dataList, {[replacePos]: rowData});
    }

    this.dataChanged = true;
    this.createTranslatedValueStoreAndFilterField(this._dataList);
  }

  onClickDelete() {
    const i = this._dataList.indexOf(this.selectedRow);
    this._dataList = [...this.dataList.slice(0, i), ...this._dataList.slice(i + 1, this._dataList.length)];
    this.selectedRow = null;
    this.dataChanged = true;
  }

  onClickEdit() {
    this.editData.emit(this.selectedRow);
  }

  createEntity(type: new() => T, item?: any): T {
    return new type();
  }

  save(security: Security, noteRequest: string): void {
    if (this.dataChanged) {
      const securitySplitsCleaned = plainToClass(this.classz, this._dataList, <any>{excludeExtraneousValues: true});
      this.deleteCreateMultipleService.deleteAndCreateMultiple(security.idSecuritycurrency, securitySplitsCleaned, noteRequest).subscribe(
        savedEntity => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'SECURITY_SPLITS'});
          this.savedData.emit(new SaveSecuritySuccess(security, true));
        }
      );
    } else {
      this.savedData.emit(new SaveSecuritySuccess(security, true));
    }
    this.savedData.emit(new SaveSecuritySuccess(security, false));
  }
}


export class SaveSecuritySuccess {
  constructor(public security: Security, public success: boolean) {
  }
}
