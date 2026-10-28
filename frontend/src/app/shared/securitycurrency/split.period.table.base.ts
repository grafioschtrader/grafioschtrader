import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { computed, DestroyRef, inject, Directive, Injector, Input, signal, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import moment from 'moment';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { Security } from '../../entities/security';
import { plainToInstance } from 'class-transformer';
import { DeleteCreateMultiple } from '../../securitycurrency/service/delete.create.multiple';
import { ClassConstructor } from 'class-transformer/types/interfaces';
import { FilterService } from '@openng/optimus-ui/api';
import { BaseSettings } from '../../lib/base.settings';

@Directive()
export abstract class SplitPeriodTableBase<T> extends TableConfigBase {
  protected readonly destroyRef = inject(DestroyRef);

  @Input() maxRows: number;
  readonly editData = output<any>();
  readonly savedData = output<any>();

  readonly selectedRow = signal<T>(null);
  dataChanged = false;
  readonly dataList = signal<T[]>([]);
  /** A new columns reference also updates the projected templates of the OnPush table. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });

  protected constructor(
    public dataSortKey: string,
    public maxRowMessageKey: string,
    private classz: ClassConstructor<T>,
    private messageToastService: MessageToastService,
    private deleteCreateMultipleService: DeleteCreateMultiple<T>,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.multiSortMeta.push({ field: dataSortKey, order: 1 });
  }

  setDataList(securitysplitList: T[], dataChanged) {
    this.dataChanged = dataChanged;
    this.selectedRow.set(null);
    this.dataList.set([...securitysplitList]);
    this.createTranslatedValueStoreAndFilterField(this.dataList());
  }

  public addDataRow(rowData: T): void {
    const dateStr = moment(rowData[this.dataSortKey]).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    const rows = this.dataList();
    const replacePos = rows.findIndex(
      (ss) =>
        ss[this.dataSortKey] === dateStr ||
        moment(ss[this.dataSortKey]).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE) === dateStr
    );

    if (replacePos === -1) {
      if (rows.length < this.maxRows) {
        this.dataList.set([...rows, rowData]);
      } else {
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'SECURITY_SPLITS_MAX_REACHED', {
          maxSplits: this.maxRows
        });
        return;
      }
    } else {
      this.dataList.set(rows.map((row, index) => (index === replacePos ? rowData : row)));
      if (this.selectedRow() === rows[replacePos]) {
        this.selectedRow.set(rowData);
      }
    }

    this.dataChanged = true;
    this.createTranslatedValueStoreAndFilterField(this.dataList());
  }

  onClickDelete() {
    this.dataList.update((rows) => rows.filter((row) => row !== this.selectedRow()));
    this.selectedRow.set(null);
    this.dataChanged = true;
  }

  onClickEdit() {
    this.editData.emit(this.selectedRow());
  }

  createEntity(type: new () => T, item?: any): T {
    return new type();
  }

  save(security: Security, noteRequest: string): void {
    if (this.dataChanged) {
      const securitySplitsCleaned = plainToInstance(this.classz, this.dataList(), <any>{
        excludeExtraneousValues: true
      });
      this.deleteCreateMultipleService
        .deleteAndCreateMultiple(security.idSecuritycurrency, securitySplitsCleaned, noteRequest)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((savedEntity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: 'SECURITY_SPLITS'
          });
          this.savedData.emit(new SaveSecuritySuccess(security, true));
        });
    } else {
      this.savedData.emit(new SaveSecuritySuccess(security, true));
    }
    this.savedData.emit(new SaveSecuritySuccess(security, false));
  }
}

export class SaveSecuritySuccess {
  constructor(
    public security: Security,
    public success: boolean
  ) {}
}
