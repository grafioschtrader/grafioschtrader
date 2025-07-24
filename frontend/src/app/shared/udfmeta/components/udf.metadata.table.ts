import {UDFMetadata, UDFMetadataParam, UDFSpecialType} from '../model/udf.metadata';
import {ConfirmationService, FilterService, MenuItem, SortMeta} from 'primeng/api';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {UserSettingsService} from '../../service/user.settings.service';
import {DeleteService} from '../../../lib/datashowbase/delete.service';
import {DataType} from '../../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../../lib/datashowbase/column.config';
import {combineLatest, Observable} from 'rxjs';
import {plainToInstance} from 'class-transformer';
import {ClassConstructor} from 'class-transformer/types/interfaces';
import {TableCrudSupportMenu} from '../../../lib/datashowbase/table.crud.support.menu';
import {UDFSpecialTypeDisableUserService} from '../service/udf.special.type.disable.user.service';


/**
 * Base class for displaying metadata in a table. The user's own entities can be edited via a further dialog.
 */
export abstract class UDFMetaTable<T extends UDFMetadata> extends TableCrudSupportMenu<T> {
  specialTypeDisabledArr: UDFSpecialType[] | number[] = [];

  protected constructor(private classz: ClassConstructor<T>,
    private udfSpecialTypeDisableUserService: UDFSpecialTypeDisableUserService,
    private deleteReadAllService: DeleteReadAllService<UDFMetadata>,
    entityName: string,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {

    super(entityName, deleteReadAllService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService)
  }

  protected abstract addAdditionalFields(beforeOthers: boolean): void;

  protected addMetadataBaseFields(sortMeta: SortMeta[]): void {
    this.addAdditionalFields(true);
    this.addColumnFeqH(DataType.String, 'uiOrder', true, false);
    this.addColumnFeqH(DataType.String, 'udfSpecialType', true, false,
      {translateValues: TranslateValue.NORMAL, width: 80});
    this.addColumnFeqH(DataType.Boolean, 'udfDisabledUser', true, false,
      {templateName: 'check', width: 60, fieldValueFN: this.udfDisabled.bind(this)});
    this.addColumn(DataType.String, 'description', 'FIELD_DESCRIPTION', true, false);
    this.addColumn(DataType.String, 'descriptionHelp', 'FIELD_DESCRIPTION_HELP', true, false,
      {width: 150});
    this.addColumnFeqH(DataType.String, 'udfDataType', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'fieldSize', true, false, {width: 60});
    this.addAdditionalFields(false);
    this.multiSortMeta.push(...sortMeta);
    this.prepareTableAndTranslate();
  }

  protected beforeEdit(entity: UDFMetadata, uDFMetadataParam: UDFMetadataParam): void {
    uDFMetadataParam.excludeUiOrders = this.entityList.filter(m => entity == null
      || entity.uiOrder !== m.uiOrder).map(m => m.uiOrder);
    uDFMetadataParam.excludeFieldNames = this.entityList.filter(m => entity == null
      || entity.description !== m.description).map(m => m.description);
  }

  override readData(): void {
    combineLatest([this.deleteReadAllService.getAllByIdUser(),
      this.udfSpecialTypeDisableUserService.getDisabledSpecialTypes()]).subscribe((data: [T[], UDFSpecialType[]]) => {
      this.entityList = plainToInstance(this.classz, data[0]);
      this.specialTypeDisabledArr = data[1];
      console.log(this.specialTypeDisabledArr);
      this.createTranslatedValueStoreAndFilterField(this.entityList);
    })
  }

  protected override addCustomMenusToSelectedEntity(udfMetaData: T, menuItems: MenuItem[]): void {
    menuItems.push({
      label: 'UDF_TURN_ON_OFF_FIELD_USER0',
      command: (event) => this.turnOnOffFieldUser0(udfMetaData),
      disabled: !udfMetaData.udfSpecialType
    });
  }

  turnOnOffFieldUser0(udfMetaData: T): void {
    if (this.specialTypeDisabledArr.indexOf(udfMetaData.udfSpecialType) >= 0) {
      this.udfSpecialTypeDisableUserService.delete(UDFSpecialType[udfMetaData.udfSpecialType]).subscribe(() => this.readData());
    } else {
      this.udfSpecialTypeDisableUserService.create(UDFSpecialType[udfMetaData.udfSpecialType]).subscribe(() => this.readData());
    }
  }

  udfDisabled(entity: T, field: ColumnConfig, valueField: any): boolean {
    return this.specialTypeDisabledArr.indexOf(entity.udfSpecialType) >= 0;
  }

  protected override hasRightsForUpdateEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }

  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return entity.idUser !== 0;
  }
}

export interface DeleteReadAllService<T extends UDFMetadata> extends DeleteService {
  getAllByIdUser(): Observable<T[]>
}
